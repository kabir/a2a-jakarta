package org.wildfly.a2a.jakarta.test.multitenancy.jsonrpc;

import static io.restassured.RestAssured.given;
import static org.wildfly.a2a.jakarta.test.common.ArchiveUtils.getJarForClass;

import java.util.List;
import java.util.UUID;

import org.a2aproject.sdk.extras.multitenancy.CdiAgentExecutorRouter;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit5.container.annotation.ArquillianTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.wildfly.a2a.jakarta.test.multitenancy.AbstractMultiTenantTest;
import org.wildfly.a2a.jakarta.test.multitenancy.MultiTenantAgentCardProducer;
import org.wildfly.a2a.jakarta.test.multitenancy.TestResponse;

@ArquillianTest
@RunAsClient
public class MultiTenantJsonRpcTest extends AbstractMultiTenantTest {

    @Deployment
    public static WebArchive createTestArchive() throws Exception {
        JavaArchive[] libraries = List.of(
                getJarForClass(org.a2aproject.sdk.util.Assert.class),
                // a2a-java-sdk-http-client: needed by BasePushNotificationSender in server-common
                getJarForClass(org.a2aproject.sdk.client.http.A2AHttpClient.class),
                getJarForClass(org.a2aproject.sdk.server.PublicAgentCard.class),
                getJarForClass(org.a2aproject.sdk.spec.Event.class),
                getJarForClass(org.a2aproject.sdk.grpc.utils.JSONRPCUtils.class),
                // a2a-java-sdk-transport-jsonrpc: JSONRPCHandler
                getJarForClass(org.a2aproject.sdk.transport.jsonrpc.handler.JSONRPCHandler.class),
                getJarForClass(org.a2aproject.sdk.jsonrpc.common.json.JsonUtil.class),
                getJarForClass(com.google.gson.Gson.class),
                getJarForClass(com.google.protobuf.InvalidProtocolBufferException.class),
                getJarForClass(com.google.protobuf.util.JsonFormat.class),
                getJarForClass(com.google.api.AnnotationsProto.class),
                getJarForClass(com.google.common.collect.ImmutableSet.class),
                // a2a-jakarta-http-common: filters + A2ARequestAttributes
                getJarForClass(org.wildfly.a2a.jakarta.common.SSESubscriber.class),
                // a2a-jakarta-jsonrpc: resource + delegate
                getJarForClass(org.wildfly.a2a.jakarta.jsonrpc.A2AServerResource.class),
                // microprofile-config: enables reading microprofile-config.properties
                getJarForClass(org.a2aproject.sdk.integrations.microprofile.MicroProfileConfigProvider.class),
                getJarForClass(mutiny.zero.ZeroPublisher.class),
                // extras-multitenancy: CdiAgentExecutorRouter + CdiAgentCardRouter + @Tenant
                getJarForClass(CdiAgentExecutorRouter.class),
                // shared tenant producers
                getJarForClass(MultiTenantAgentCardProducer.class)
        ).toArray(JavaArchive[]::new);

        return ShrinkWrap.create(WebArchive.class, "ROOT.war")
                .addAsLibraries(libraries)
                .addClass(JsonRpcApplication.class)
                .addAsManifestResource("META-INF/beans.xml", "beans.xml")
                .addAsWebInfResource("WEB-INF/web.xml", "web.xml")
                .addAsResource("META-INF/disable-authorization-microprofile-config.properties",
                        "META-INF/microprofile-config.properties");
    }

    private static String tenantField(String tenant) {
        return tenant != null && !tenant.isBlank() ? ", \"tenant\": \"" + tenant + "\"" : "";
    }

    private static String sendBody(String method, String tenant) {
        return """
                {"jsonrpc":"2.0","id":"1","method":"%s","params":{"message":{"messageId":"%s","role":"ROLE_USER","parts":[{"text":"hello"}]}%s}}"""
                .formatted(method, UUID.randomUUID(), tenantField(tenant));
    }

    private static String extendedCardBody(String tenant) {
        return tenant == null
                ? "{\"jsonrpc\":\"2.0\",\"method\":\"GetExtendedAgentCard\",\"id\":\"1\"}"
                : "{\"jsonrpc\":\"2.0\",\"method\":\"GetExtendedAgentCard\",\"id\":\"1\",\"params\":{\"tenant\":\"" + tenant + "\"}}";
    }

    private static TestResponse rpc(String body) {
        var r = given().header("A2A-Version", "1.0").contentType("application/json").body(body).when().post("/");
        return new TestResponse(r.getStatusCode(), r.getBody().asString());
    }

    @Override
    protected TestResponse sendMessage(String tenant) {
        return rpc(sendBody("SendMessage", tenant));
    }

    @Override
    protected TestResponse streamMessage(String tenant) {
        return rpc(sendBody("SendStreamingMessage", tenant));
    }

    @Override
    protected TestResponse getExtendedCard(String tenant) {
        return rpc(extendedCardBody(tenant));
    }
}
