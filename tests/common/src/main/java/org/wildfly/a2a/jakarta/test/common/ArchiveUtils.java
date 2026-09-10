package org.wildfly.a2a.jakarta.test.common;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.google.api.AnnotationsProto;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.protobuf.util.JsonFormat;
import mutiny.zero.ZeroPublisher;
import org.a2aproject.sdk.client.http.A2AHttpClient;
import org.a2aproject.sdk.client.transport.spi.ClientTransport;
import org.a2aproject.sdk.extras.multitenancy.CdiAgentExecutorRouter;
import org.a2aproject.sdk.extras.multitenancy.tests.MultiTenantAgentCardProducer;
import org.a2aproject.sdk.grpc.utils.JSONRPCUtils;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.spec.Event;
import org.a2aproject.sdk.util.Assert;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;

public class ArchiveUtils {

    private ArchiveUtils() {
    }

    public static JavaArchive getJarForClass(Class<?> clazz) throws Exception {
        File f = new File(clazz.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (!f.exists()) {
            throw new IllegalArgumentException(
                    "Archive not found for class " + clazz.getName() + " at " + f.getAbsolutePath());
        }
        return ShrinkWrap.createFromZipFile(JavaArchive.class, f);
    }

    /**
     * Resolves the a2a-java-extras-multitenancy-tests-test-common jar and strips it of the two
     * things that break a WildFly deployment: an unused, Quarkus-only bean, and a bean-discovery
     * gap that leaves the shared agent-card producer unregistered.
     */
    public static JavaArchive prepareMultiTenantTestCommonJarForWildFly() throws Exception {
        // TestAuthorizationController extends Quarkus's own AuthorizationController SPI class
        // (io.quarkus:quarkus-security-runtime-spi), which isn't on WildFly's classpath. It's an
        // unused CDI bean today — nothing in a2a-java activates it via
        // quarkus.arc.selected-alternatives — but Weld would still try to load it as a managed bean
        // and fail the deployment with NoClassDefFoundError. Strip it before packaging.
        JavaArchive jar = getJarForClass(MultiTenantAgentCardProducer.class);
        jar.delete("/org/a2aproject/sdk/extras/multitenancy/tests/TestAuthorizationController.class");

        // MultiTenantAgentCardProducer is annotated with jakarta.inject.Singleton, which is a
        // pseudo-scope (meta-annotated @Scope, not @NormalScope) and therefore is NOT one of the
        // CDI "bean defining annotations" that trigger implicit-bean-archive discovery under this
        // jar's own bean-discovery-mode="annotated" beans.xml. Weld silently skips the class,
        // leaving @PublicAgentCard/@ExtendedAgentCard unsatisfied at runtime. Widen discovery to
        // "all" for this archive so the producer (and its sibling classes) are picked up.
        jar.delete("/META-INF/beans.xml");
        jar.addAsManifestResource(
                new StringAsset("<beans xmlns=\"https://jakarta.ee/xml/ns/jakartaee\" "
                        + "bean-discovery-mode=\"all\"/>"),
                "beans.xml");

        return jar;
    }

    /**
     * Returns the archives shared by the REST, JSON-RPC, and gRPC multitenancy deployments,
     * followed by the transport-specific archives supplied by the caller.
     */
    public static JavaArchive[] getCommonMultitenancyLibraries(JavaArchive... transportLibraries)
            throws Exception {
        List<JavaArchive> libraries = new ArrayList<>(List.of(
                getJarForClass(Assert.class),
                getJarForClass(A2AHttpClient.class),
                getJarForClass(PublicAgentCard.class),
                getJarForClass(Event.class),
                getJarForClass(JSONRPCUtils.class),
                getJarForClass(JsonUtil.class),
                getJarForClass(Gson.class),
                getJarForClass(JsonFormat.class),
                getJarForClass(AnnotationsProto.class),
                getJarForClass(ImmutableSet.class),
                getJarForClass(ZeroPublisher.class),
                getJarForClass(ClientTransport.class),
                getJarForClass(CdiAgentExecutorRouter.class),
                prepareMultiTenantTestCommonJarForWildFly()));
        libraries.addAll(List.of(transportLibraries));
        return libraries.toArray(JavaArchive[]::new);
    }
}
