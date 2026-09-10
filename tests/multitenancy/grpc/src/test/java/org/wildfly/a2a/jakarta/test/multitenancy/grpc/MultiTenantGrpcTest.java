package org.wildfly.a2a.jakarta.test.multitenancy.grpc;

import static org.wildfly.a2a.jakarta.test.common.ArchiveUtils.getJarForClass;
import static org.wildfly.a2a.jakarta.test.common.ArchiveUtils.getCommonMultitenancyLibraries;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.a2aproject.sdk.A2A;
import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.transport.grpc.GrpcTransport;
import org.a2aproject.sdk.client.transport.grpc.GrpcTransportConfigBuilder;
import org.a2aproject.sdk.client.transport.grpc.GrpcTransportProvider;
import org.a2aproject.sdk.extras.multitenancy.tests.AbstractMultiTenantServerTest;
import org.a2aproject.sdk.grpc.A2AServiceGrpc;
import org.a2aproject.sdk.integrations.microprofile.MicroProfileConfigProvider;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.a2aproject.sdk.transport.grpc.handler.GrpcHandler;
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

/**
 * gRPC multitenancy coverage. The upstream base class also defines HTTP public-card tests;
 * those inherited tests are intentionally no-ops here because gRPC does not serve those endpoints.
 */
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
        final JavaArchive[] libraries = getCommonMultitenancyLibraries(
                // a2a-jakarta-grpc.jar - contains WildFlyGrpcHandler
                getJarForClass(WildFlyGrpcHandler.class),
                // a2a-java-sdk-client.jar
                getJarForClass(A2A.class),
                getJarForClass(GrpcHandler.class),
                // protobuf-java.jar - include correct version to match gencode
                getJarForClass(com.google.protobuf.Message.class),
                // a2a-java-spec-grpc.jar (contains generated gRPC classes; removed from auto-registration below)
                getJarForClass(A2AServiceGrpc.class),
                getJarForClass(GrpcTransportProvider.class),
                getJarForClass(MicroProfileConfigProvider.class),
                getJarForClass(AsyncManagedExecutorServiceProducer.class));

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

    @Override
    public void publicCardWithoutTenantReturnsDefault() {
    }

    @Override
    public void publicCardWithAcmeTenant() {
    }

    @Override
    public void publicCardWithBetaTenant() {
    }

    @Override
    public void publicCardUnknownTenantReturns404() {
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
