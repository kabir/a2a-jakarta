package org.wildfly.a2a.jakarta.test.multitenancy.jsonrpc;

import static org.wildfly.a2a.jakarta.test.common.ArchiveUtils.getJarForClass;
import static org.wildfly.a2a.jakarta.test.common.ArchiveUtils.prepareMultiTenantTestCommonJar;

import java.util.List;

import com.google.api.AnnotationsProto;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import mutiny.zero.ZeroPublisher;
import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.config.ClientConfig;
import org.a2aproject.sdk.client.http.A2AHttpClient;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfigBuilder;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportProvider;
import org.a2aproject.sdk.client.transport.spi.ClientTransport;
import org.a2aproject.sdk.extras.multitenancy.CdiAgentExecutorRouter;
import org.a2aproject.sdk.extras.multitenancy.tests.AbstractMultiTenantServerTest;
import org.a2aproject.sdk.grpc.utils.JSONRPCUtils;
import org.a2aproject.sdk.integrations.microprofile.MicroProfileConfigProvider;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.spec.Event;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.a2aproject.sdk.transport.jsonrpc.handler.JSONRPCHandler;
import org.a2aproject.sdk.util.Assert;
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
        JavaArchive multiTenantTestCommonJar = prepareMultiTenantTestCommonJar();

        JavaArchive[] libraries = List.of(
                getJarForClass(Assert.class),
                // a2a-java-sdk-http-client: needed by BasePushNotificationSender in server-common
                getJarForClass(A2AHttpClient.class),
                getJarForClass(PublicAgentCard.class),
                getJarForClass(Event.class),
                getJarForClass(JSONRPCUtils.class),
                // a2a-java-sdk-transport-jsonrpc: JSONRPCHandler
                getJarForClass(JSONRPCHandler.class),
                getJarForClass(JsonUtil.class),
                getJarForClass(Gson.class),
                getJarForClass(InvalidProtocolBufferException.class),
                getJarForClass(JsonFormat.class),
                getJarForClass(AnnotationsProto.class),
                getJarForClass(ImmutableSet.class),
                // a2a-jakarta-http-common: filters + A2ARequestAttributes
                getJarForClass(org.wildfly.a2a.jakarta.common.SSESubscriber.class),
                // a2a-jakarta-jsonrpc: resource + delegate
                getJarForClass(A2AServerResource.class),
                // microprofile-config: enables reading microprofile-config.properties
                getJarForClass(MicroProfileConfigProvider.class),
                getJarForClass(ZeroPublisher.class),
                // a2a-java-sdk-client.jar (client library, used by AbstractMultiTenantServerTest)
                getJarForClass(ClientConfig.class),
                // a2a-java-sdk-client-transport-spi.jar (client transport SPI)
                getJarForClass(ClientTransport.class),
                // a2a-java-sdk-client-transport-jsonrpc.jar (JSONRPC client transport)
                getJarForClass(JSONRPCTransportProvider.class),
                // a2a-jakarta-common.jar (ManagedExecutor for RequestScoped bean injection into AgentExecutors)
                getJarForClass(AsyncManagedExecutorServiceProducer.class),
                // extras-multitenancy: CdiAgentExecutorRouter + CdiAgentCardRouter + @Tenant
                getJarForClass(CdiAgentExecutorRouter.class),
                // shared multitenancy test infra: AbstractMultiTenantServerTest,
                // MultiTenantAgentCardProducer, MultiTenantAgentExecutorProducer, Tenants
                multiTenantTestCommonJar
        ).toArray(JavaArchive[]::new);

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
