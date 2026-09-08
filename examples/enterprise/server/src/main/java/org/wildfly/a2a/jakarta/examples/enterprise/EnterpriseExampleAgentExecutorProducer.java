package org.wildfly.a2a.jakarta.examples.enterprise;

import java.util.Collections;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.TaskNotCancelableError;
import org.a2aproject.sdk.spec.TextPart;

@ApplicationScoped
public class EnterpriseExampleAgentExecutorProducer {

    @Produces
    public AgentExecutor enterpriseExampleExecutor() {
        return new EnterpriseExampleAgentExecutor();
    }

    private static class EnterpriseExampleAgentExecutor implements AgentExecutor {
        @Override
        public void execute(RequestContext context, AgentEmitter emitter) throws A2AError {
            boolean isNewTask = context.getTask() == null;
            if (isNewTask) {
                emitter.submit();
            }

            // Demo-only convention (not a general A2A pattern): a message with messageId "init"
            // only creates the task and returns, leaving it open for a later continuation
            // message to do the work. This lets a client observe the task via a different node
            // than the one that eventually completes it, proving cross-node event replication.
            boolean isInitHandshake = isNewTask && "init".equals(context.getMessage().messageId());
            if (isInitHandshake) {
                return;
            }

            emitter.startWork();
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }

            List<Part<?>> partsList = context.getMessage().parts();
            List<TextPart> textParts = partsList.stream()
                    .filter(p -> p instanceof TextPart)
                    .map(p -> (TextPart) p)
                    .toList();
            String name = textParts.get(textParts.size() - 1).text();

            String response = "Hello " + name;
            emitter.addArtifact(Collections.singletonList(new TextPart(response)), null, "response", null);

            emitter.complete();
        }

        @Override
        public void cancel(RequestContext context, AgentEmitter emitter) throws A2AError {
            throw new TaskNotCancelableError();
        }
    }
}
