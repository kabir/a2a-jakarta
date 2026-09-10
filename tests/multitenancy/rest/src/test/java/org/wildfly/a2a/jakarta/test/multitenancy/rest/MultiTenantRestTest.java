package org.wildfly.a2a.jakarta.test.multitenancy.rest;

import static org.wildfly.a2a.jakarta.test.common.ArchiveUtils.getCommonMultitenancyLibraries;
import static org.wildfly.a2a.jakarta.test.common.ArchiveUtils.getJarForClass;

import com.google.protobuf.InvalidProtocolBufferException;
import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.config.ClientConfig;
import org.a2aproject.sdk.client.transport.rest.RestTransport;
import org.a2aproject.sdk.client.transport.rest.RestTransportConfigBuilder;
import org.a2aproject.sdk.extras.multitenancy.tests.AbstractMultiTenantServerTest;
import org.a2aproject.sdk.integrations.microprofile.MicroProfileConfigProvider;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.a2aproject.sdk.transport.rest.handler.RestHandler;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit5.container.annotation.ArquillianTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.wildfly.a2a.jakarta.common.AsyncManagedExecutorServiceProducer;
import org.wildfly.a2a.jakarta.rest.A2ARestServerResource;

@ArquillianTest
@RunAsClient
public class MultiTenantRestTest extends AbstractMultiTenantServerTest {

    public MultiTenantRestTest() {
        super(8080);
    }

    @Override
    protected String getTransportProtocol() {
        return TransportProtocol.HTTP_JSON.asString();
    }

    @Override
    protected String getTransportUrl() {
        return "http://localhost:8080";
    }

    @Override
    protected void configureTransport(ClientBuilder builder) {
        builder.withTransport(RestTransport.class, new RestTransportConfigBuilder());
    }

    @Deployment
    public static WebArchive createTestArchive() throws Exception {
        JavaArchive[] libraries = getCommonMultitenancyLibraries(
                getJarForClass(RestHandler.class),
                getJarForClass(InvalidProtocolBufferException.class),
                getJarForClass(org.wildfly.a2a.jakarta.common.SSESubscriber.class),
                getJarForClass(A2ARestServerResource.class),
                getJarForClass(MicroProfileConfigProvider.class),
                getJarForClass(AsyncManagedExecutorServiceProducer.class),
                getJarForClass(ClientConfig.class),
                getJarForClass(RestTransport.class));

        return ShrinkWrap.create(WebArchive.class, "ROOT.war")
                .addAsLibraries(libraries)
                .addClass(RestApplication.class)
                .addAsManifestResource("META-INF/beans.xml", "beans.xml")
                .addAsWebInfResource("WEB-INF/web.xml", "web.xml")
                .addAsResource("a2a-requesthandler-test.properties")
                .addAsResource("META-INF/disable-authorization-microprofile-config.properties",
                        "META-INF/microprofile-config.properties");
    }
}
