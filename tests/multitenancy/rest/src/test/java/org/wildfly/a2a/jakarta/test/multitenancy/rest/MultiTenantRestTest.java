package org.wildfly.a2a.jakarta.test.multitenancy.rest;

import static org.wildfly.a2a.jakarta.test.common.ArchiveUtils.getJarForClass;

import java.util.List;

import com.google.api.AnnotationsProto;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.config.ClientConfig;
import org.a2aproject.sdk.client.http.A2AHttpClient;
import org.a2aproject.sdk.client.transport.rest.RestTransport;
import org.a2aproject.sdk.client.transport.rest.RestTransportConfigBuilder;
import org.a2aproject.sdk.client.transport.spi.ClientTransport;
import org.a2aproject.sdk.extras.multitenancy.CdiAgentExecutorRouter;
import org.a2aproject.sdk.extras.multitenancy.tests.AbstractMultiTenantServerTest;
import org.a2aproject.sdk.extras.multitenancy.tests.MultiTenantAgentCardProducer;
import org.a2aproject.sdk.grpc.utils.JSONRPCUtils;
import org.a2aproject.sdk.integrations.microprofile.MicroProfileConfigProvider;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.spec.Event;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.a2aproject.sdk.transport.rest.handler.RestHandler;
import org.a2aproject.sdk.util.Assert;
import mutiny.zero.ZeroPublisher;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit5.container.annotation.ArquillianTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
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
        // See MultiTenantJsonRpcTest for why TestAuthorizationController must be stripped.
        JavaArchive multiTenantTestCommonJar = getJarForClass(MultiTenantAgentCardProducer.class);
        multiTenantTestCommonJar.delete(
                "/org/a2aproject/sdk/extras/multitenancy/tests/TestAuthorizationController.class");
        // MultiTenantAgentCardProducer is annotated with jakarta.inject.Singleton, which is a
        // pseudo-scope (meta-annotated @Scope, not @NormalScope) and therefore is NOT one of the
        // CDI "bean defining annotations" that trigger implicit-bean-archive discovery under this
        // jar's own bean-discovery-mode="annotated" beans.xml. Weld silently skips the class,
        // leaving @PublicAgentCard/@ExtendedAgentCard unsatisfied at runtime. Widen discovery to
        // "all" for this archive so the producer (and its sibling classes) are picked up.
        multiTenantTestCommonJar.delete("/META-INF/beans.xml");
        multiTenantTestCommonJar.addAsManifestResource(
                new StringAsset("<beans xmlns=\"https://jakarta.ee/xml/ns/jakartaee\" "
                        + "bean-discovery-mode=\"all\"/>"),
                "beans.xml");

        JavaArchive[] libraries = List.of(
                getJarForClass(Assert.class),
                getJarForClass(A2AHttpClient.class),
                getJarForClass(PublicAgentCard.class),
                getJarForClass(Event.class),
                getJarForClass(JSONRPCUtils.class),
                getJarForClass(RestHandler.class),
                getJarForClass(JsonUtil.class),
                getJarForClass(Gson.class),
                getJarForClass(InvalidProtocolBufferException.class),
                getJarForClass(JsonFormat.class),
                getJarForClass(AnnotationsProto.class),
                getJarForClass(ImmutableSet.class),
                getJarForClass(org.wildfly.a2a.jakarta.common.SSESubscriber.class),
                getJarForClass(A2ARestServerResource.class),
                getJarForClass(MicroProfileConfigProvider.class),
                getJarForClass(ZeroPublisher.class),
                getJarForClass(ClientConfig.class),
                getJarForClass(ClientTransport.class),
                getJarForClass(RestTransport.class),
                getJarForClass(AsyncManagedExecutorServiceProducer.class),
                getJarForClass(CdiAgentExecutorRouter.class),
                multiTenantTestCommonJar
        ).toArray(JavaArchive[]::new);

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
