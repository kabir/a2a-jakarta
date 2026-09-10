package org.wildfly.a2a.jakarta.test.multitenancy.jsonrpc;

import static org.wildfly.a2a.jakarta.test.common.ArchiveUtils.getCommonMultitenancyLibraries;
import static org.wildfly.a2a.jakarta.test.common.ArchiveUtils.getJarForClass;

import com.google.protobuf.InvalidProtocolBufferException;
import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.config.ClientConfig;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfigBuilder;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportProvider;
import org.a2aproject.sdk.extras.multitenancy.tests.AbstractMultiTenantServerTest;
import org.a2aproject.sdk.integrations.microprofile.MicroProfileConfigProvider;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.a2aproject.sdk.transport.jsonrpc.handler.JSONRPCHandler;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit5.container.annotation.ArquillianTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.wildfly.a2a.jakarta.common.AsyncManagedExecutorServiceProducer;
import org.wildfly.a2a.jakarta.jsonrpc.A2AServerResource;

@ArquillianTest
@RunAsClient
public class MultiTenantJsonRpcTest extends AbstractMultiTenantServerTest {

    public MultiTenantJsonRpcTest() {
        super(8080);
    }

    @Override
    protected String getTransportProtocol() {
        return TransportProtocol.JSONRPC.asString();
    }

    @Override
    protected String getTransportUrl() {
        return "http://localhost:8080";
    }

    @Override
    protected void configureTransport(ClientBuilder builder) {
        builder.withTransport(JSONRPCTransport.class, new JSONRPCTransportConfigBuilder());
    }

    @Deployment
    public static WebArchive createTestArchive() throws Exception {
        JavaArchive[] libraries = getCommonMultitenancyLibraries(
                // a2a-java-sdk-transport-jsonrpc: JSONRPCHandler
                getJarForClass(JSONRPCHandler.class),
                getJarForClass(InvalidProtocolBufferException.class),
                // a2a-jakarta-http-common: filters + A2ARequestAttributes
                getJarForClass(org.wildfly.a2a.jakarta.common.SSESubscriber.class),
                // a2a-jakarta-jsonrpc: resource + delegate
                getJarForClass(A2AServerResource.class),
                getJarForClass(MicroProfileConfigProvider.class),
                getJarForClass(AsyncManagedExecutorServiceProducer.class),
                // a2a-java-sdk-client.jar (client library, used by AbstractMultiTenantServerTest)
                getJarForClass(ClientConfig.class),
                // a2a-java-sdk-client-transport-jsonrpc.jar (JSONRPC client transport)
                getJarForClass(JSONRPCTransportProvider.class));

        return ShrinkWrap.create(WebArchive.class, "ROOT.war")
                .addAsLibraries(libraries)
                .addClass(JsonRpcApplication.class)
                .addAsManifestResource("META-INF/beans.xml", "beans.xml")
                .addAsWebInfResource("WEB-INF/web.xml", "web.xml")
                .addAsResource("a2a-requesthandler-test.properties")
                .addAsResource("META-INF/disable-authorization-microprofile-config.properties",
                        "META-INF/microprofile-config.properties");
    }
}
