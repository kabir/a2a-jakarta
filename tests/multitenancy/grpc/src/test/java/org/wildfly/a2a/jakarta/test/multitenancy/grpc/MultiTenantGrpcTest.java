package org.wildfly.a2a.jakarta.test.multitenancy.grpc;

import static org.wildfly.a2a.jakarta.test.common.ArchiveUtils.getJarForClass;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import com.google.api.AnnotationsProto;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.protobuf.util.JsonFormat;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.a2aproject.sdk.A2A;
import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.http.A2AHttpClient;
import org.a2aproject.sdk.client.transport.grpc.GrpcTransport;
import org.a2aproject.sdk.client.transport.grpc.GrpcTransportConfigBuilder;
import org.a2aproject.sdk.client.transport.grpc.GrpcTransportProvider;
import org.a2aproject.sdk.client.transport.spi.ClientTransport;
import org.a2aproject.sdk.extras.multitenancy.CdiAgentExecutorRouter;
import org.a2aproject.sdk.extras.multitenancy.tests.AbstractMultiTenantServerTest;
import org.a2aproject.sdk.extras.multitenancy.tests.MultiTenantAgentCardProducer;
import org.a2aproject.sdk.grpc.A2AServiceGrpc;
import org.a2aproject.sdk.grpc.utils.JSONRPCUtils;
import org.a2aproject.sdk.integrations.microprofile.MicroProfileConfigProvider;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.spec.Event;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.a2aproject.sdk.transport.grpc.handler.GrpcHandler;
import org.a2aproject.sdk.util.Assert;
import mutiny.zero.ZeroPublisher;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit5.container.annotation.ArquillianTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.AfterAll;
import org.wildfly.a2a.jakarta.common.AsyncManagedExecutorServiceProducer;
import org.wildfly.a2a.jakarta.grpc.WildFlyGrpcHandler;

@ArquillianTest
@RunAsClient
public class MultiTenantGrpcTest extends AbstractMultiTenantServerTest {

    private static final List<ManagedChannel> channels = new CopyOnWriteArrayList<>();

    public MultiTenantGrpcTest() {
        super(8080); // HTTP utility port; unused (public-card checks are no-ops below)
    }

    @Override
    protected String getTransportProtocol() {
        return TransportProtocol.GRPC.asString();
    }

    @Override
    protected String getTransportUrl() {
        // gRPC port (from WildFly's gRPC subsystem configuration)
        return "localhost:9555";
    }

    @Override
    protected void configureTransport(ClientBuilder builder) {
        builder.withTransport(GrpcTransport.class, new GrpcTransportConfigBuilder().channelFactory(target -> {
            ManagedChannel channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
            channels.add(channel);
            return channel;
        }));
    }

    @Deployment
    public static WebArchive createDeployment() throws Exception {
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

        final JavaArchive[] libraries = List.of(
                // a2a-jakarta-grpc.jar - contains WildFlyGrpcHandler
                getJarForClass(WildFlyGrpcHandler.class),
                // a2a-java-sdk-client.jar
                getJarForClass(A2A.class),
                getJarForClass(Assert.class),
                getJarForClass(A2AHttpClient.class),
                getJarForClass(PublicAgentCard.class),
                getJarForClass(Event.class),
                getJarForClass(JSONRPCUtils.class),
                getJarForClass(GrpcHandler.class),
                getJarForClass(JsonUtil.class),
                getJarForClass(Gson.class),
                // protobuf-java.jar - include correct version to match gencode
                getJarForClass(com.google.protobuf.Message.class),
                getJarForClass(JsonFormat.class),
                getJarForClass(AnnotationsProto.class),
                getJarForClass(ImmutableSet.class),
                getJarForClass(MicroProfileConfigProvider.class),
                // a2a-java-spec-grpc.jar (contains generated gRPC classes; removed from auto-registration below)
                getJarForClass(A2AServiceGrpc.class),
                getJarForClass(ZeroPublisher.class),
                getJarForClass(ClientTransport.class),
                getJarForClass(GrpcTransportProvider.class),
                getJarForClass(AsyncManagedExecutorServiceProducer.class),
                // extras-multitenancy: CdiAgentExecutorRouter + CdiAgentCardRouter + @Tenant
                getJarForClass(CdiAgentExecutorRouter.class),
                // shared multitenancy test infra: AbstractMultiTenantServerTest,
                // MultiTenantAgentCardProducer, MultiTenantAgentExecutorProducer, Tenants
                multiTenantTestCommonJar
        ).toArray(new JavaArchive[0]);

        // These are provided by WildFly's gRPC feature-pack and should not be packaged in the WAR;
        // the manifest export makes the module classes visible to all classloaders in the deployment.
        String manifest = "Manifest-Version: 1.0\n" +
                "Dependencies: io.grpc-all\n";

        WebArchive archive = ShrinkWrap.create(WebArchive.class, "ROOT.war")
                .addAsLibraries(libraries)
                .addAsManifestResource("META-INF/beans.xml", "beans.xml")
                .addAsResource("a2a-requesthandler-test.properties")
                .setManifest(new StringAsset(manifest));

        archive.addAsResource("META-INF/disable-authorization-microprofile-config.properties",
                "META-INF/microprofile-config.properties");

        return archive;
    }

    // gRPC-only deployments do not serve the well-known public-card endpoints.
    @Override
    public void publicCardWithoutTenantReturnsDefault() {
        // no-op: not served by gRPC
    }

    @Override
    public void publicCardWithAcmeTenant() {
        // no-op: not served by gRPC
    }

    @Override
    public void publicCardWithBetaTenant() {
        // no-op: not served by gRPC
    }

    @Override
    public void publicCardUnknownTenantReturns404() {
        // no-op: not served by gRPC
    }

    @AfterAll
    public static void closeChannels() {
        for (ManagedChannel channel : channels) {
            channel.shutdownNow();
            try {
                channel.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        channels.clear();
    }
}
