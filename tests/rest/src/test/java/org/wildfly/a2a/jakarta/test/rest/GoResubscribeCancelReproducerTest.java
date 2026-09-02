package org.wildfly.a2a.jakarta.test.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit5.container.annotation.ArquillianTest;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.Test;

/**
 * Reproducer for the a2a-java 1.3.0 regression where canceling a COMPLETED task via REST
 * returns an error instead of 200.
 *
 * <p>This test exactly mirrors the ITK Go v10 agent's {@code handleCallAgentWithResubscribe()}
 * method (agents/go/v10/main.go) when calling a server that completes its task immediately
 * (holdTask=false, no "task-finished" marker in the response):
 *
 * <ol>
 *   <li>Go: {@code events = client.SendStreamingMessage(initCtx, req)}
 *       → reads until taskId found → {@code cancelInit()} (breaks connection)
 *   <li>Go: {@code resubEvents = client.SubscribeToTask(ctx, req)}
 *       → iterates until stream closes; no "task-finished" found → {@code taskFinished=false}
 *   <li>Go: {@code client.CancelTask(ctx, req)}
 *       → always called; in 1.3.0 fails because task is already COMPLETED
 *       → before the upstream ITK fix, error propagates → executor calls {@code emitter.fail()}
 *       → ITK "Resubscribe Test - Non-JSONRPC Protocols" loses http_json traversal result
 * </ol>
 *
 * <p>The upstream ITK fix (upstream/fix/itk-go-http-json-resubscribe) adds {@code taskFinished}
 * tracking and tolerates the CancelTask error when the task already reached a terminal state.
 * The correct server-side fix is to return 200 for CancelTask on a COMPLETED task (as in 1.2.0).
 */
@ArquillianTest
@RunAsClient
public class GoResubscribeCancelReproducerTest {

    private static final String REST_BASE = "http://localhost:8080/a2a_rest_v1.0";
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Deployment
    public static WebArchive createTestArchive() throws Exception {
        return JakartaA2AServerTest.createTestArchive();
    }

    @Test
    public void testGoResubscribeCancelFlow() throws Exception {
        // Step 1 — SendStreamingMessage: POST /message:stream, read first SSE event for taskId,
        // then abandon the connection (mirrors Go's cancelInit() after taskID is obtained).
        String taskId = sendStreamingMessageAndGetTaskId();
        assertNotNull(taskId, "Expected a taskId from the /message:stream SSE response");

        // Step 2 — SubscribeToTask: POST /tasks/{taskId}:subscribe, drain SSE until stream closes.
        // The task completes immediately (no hold, no "task-finished"), so taskFinished stays false.
        subscribeAndDrain(taskId);

        // Step 3 — CancelTask: POST /tasks/{taskId}:cancel — the Go client always calls this.
        // In a2a-java 1.2.0 this returned 200 even for a COMPLETED task.
        // In a2a-java 1.3.0 this returns an error, causing the Go ITK agent to fail.
        int cancelStatus = cancelTask(taskId);
        assertEquals(200, cancelStatus,
                "POST /tasks/{taskId}:cancel on a COMPLETED task must return 200. " +
                "Regressed in a2a-java 1.3.0: the ITK Go v10 handleCallAgentWithResubscribe() " +
                "always calls CancelTask after draining the subscribe stream (taskFinished=false), " +
                "and a failure here causes the http_json traversal result to be lost.");
    }

    /**
     * POST /message:stream — returns the taskId from the first SSE event that carries one,
     * then abandons the connection (like Go's cancelInit()).
     *
     * <p>Uses the {@code #a2a-delegated#} prefix so the test agent immediately calls
     * {@code complete()}, mirroring a leaf node with holdTask=false.
     */
    private String sendStreamingMessageAndGetTaskId() throws Exception {
        String body = buildSendMessageBody("#a2a-delegated#hello");

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(REST_BASE + "/message:stream"))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<InputStream> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resp.body()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                String json = line.substring(5).trim();
                String taskId = extractTaskId(json);
                if (taskId != null) {
                    // Break immediately once we have the taskId — mirrors Go's cancelInit().
                    return taskId;
                }
            }
        }
        return null;
    }

    /**
     * POST /tasks/{taskId}:subscribe — drain all SSE lines until the stream closes.
     * No "task-finished" marker is expected (the agent used holdTask=false).
     * This leaves taskFinished=false in the Go client, which then calls CancelTask.
     */
    private void subscribeAndDrain(String taskId) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(REST_BASE + "/tasks/" + taskId + ":subscribe"))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<InputStream> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resp.body()))) {
            // Drain until stream closes — taskFinished stays false (no "task-finished" text).
            while (reader.readLine() != null) {
                // consume
            }
        }
    }

    /**
     * POST /tasks/{taskId}:cancel — returns the HTTP status code.
     * Mirrors: {@code _, err := client.CancelTask(ctx, &a2a.CancelTaskRequest{ID: taskID})}
     */
    private int cancelTask(String taskId) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(REST_BASE + "/tasks/" + taskId + ":cancel"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        return HTTP.send(req, HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    /**
     * Builds the A2A REST SendMessageRequest JSON body for POST /message:stream.
     */
    private static String buildSendMessageBody(String text) {
        // Escape the text for JSON (covers the #a2a-delegated# prefix used here)
        String escaped = text.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"message\":{"
                + "\"messageId\":\"" + UUID.randomUUID() + "\","
                + "\"role\":\"user\","
                + "\"parts\":[{\"kind\":\"text\",\"text\":\"" + escaped + "\"}]"
                + "}}";
    }

    /**
     * Extract the task id from an A2A REST SSE event JSON payload.
     *
     * <p>The Java server emits events in the same shape as the Go client parses them:
     * <ul>
     *   <li>{@code {"task":{"id":"<uuid>", ...}}}
     *   <li>{@code {"statusUpdate":{"id":"<uuid>", ...}}}
     *   <li>{@code {"artifactUpdate":{"id":"<uuid>", ...}}}
     * </ul>
     * In all cases the task id appears as the first {@code "id":"<uuid>"} value.
     */
    private static String extractTaskId(String json) {
        int idx = json.indexOf("\"id\":\"");
        if (idx < 0) {
            return null;
        }
        int start = idx + 6;
        int end = json.indexOf('"', start);
        if (end <= start) {
            return null;
        }
        String candidate = json.substring(start, end);
        // UUID shape: 8-4-4-4-12 hex chars with hyphens = 36 chars total
        return candidate.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                ? candidate : null;
    }
}
