package org.wildfly.a2a.jakarta.examples.enterprise;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentSkill;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.eclipse.microprofile.config.ConfigProvider;

@ApplicationScoped
public class EnterpriseExampleAgentCardProducer {

    private static final int BASE_HTTP_PORT = 8080;
    private static final int BASE_GRPC_PORT = 9555;

    @Produces
    @PublicAgentCard
    public AgentCard createAgentCard() {
        // Both nodes share one WAR; the actual listening ports are base port + WildFly's own
        // socket-binding-port-offset, so re-reading that same system property here keeps the
        // advertised URLs correct on whichever node served this request.
        int portOffset = ConfigProvider.getConfig()
                .getOptionalValue("jboss.socket.binding.port-offset", Integer.class)
                .orElse(0);

        String jsonRpcUrl = "http://localhost:" + (BASE_HTTP_PORT + portOffset);
        List<AgentInterface> interfaces = new ArrayList<>();
        // At the moment we always add the JSONRPC transport. It is needed to get the AgentCard.
        // This may change in the future
        interfaces.add(
                new AgentInterface(
                        TransportProtocol.JSONRPC.asString(), jsonRpcUrl));
        if (isRest()) {
            interfaces.add(
                    new AgentInterface(
                            TransportProtocol.HTTP_JSON.asString(), jsonRpcUrl));
        }
        if (isGrpcEnabled()) {
            interfaces.add(
                    new AgentInterface(
                            TransportProtocol.GRPC.asString(), "localhost:" + (BASE_GRPC_PORT + portOffset)));
        }

        return AgentCard.builder()
                .name("Enterprise Hello World Agent")
                .description("Hello world agent demonstrating JPA-backed stores and a replicated queue manager across two nodes")
                .version("1.0.0")
                .documentationUrl("http://example.com/docs")
                .capabilities(AgentCapabilities.builder().streaming(true).build())
                .defaultInputModes(Collections.singletonList("text"))
                .defaultOutputModes(Collections.singletonList("text"))
                .skills(Collections.singletonList(AgentSkill.builder()
                        .id("hello_world")
                        .name("Returns hello world")
                        .description("just returns hello world")
                        .tags(Collections.singletonList("hello world"))
                        .examples(List.of("hi", "hello world"))
                        .build()))
                .supportedInterfaces(interfaces)
                .build();
    }

    private boolean isGrpcEnabled() {
        try {
            Class.forName("org.wildfly.a2a.jakarta.grpc.GrpcBeanInitializer");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean isRest() {
        try {
            Class.forName("org.wildfly.a2a.jakarta.rest.WildFlyRestTransportMetadata");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
