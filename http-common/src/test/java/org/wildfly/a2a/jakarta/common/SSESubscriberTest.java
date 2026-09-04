package org.wildfly.a2a.jakarta.common;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SSESubscriberTest {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
    }

    @Test
    void synchronousComplete_cancelsHeartbeat() throws Exception {
        CompletableFuture<Void> streamingComplete = new CompletableFuture<>();
        PrintWriter writer = new PrintWriter(new StringWriter());

        SSESubscriber subscriber = new SSESubscriber(streamingComplete, writer, scheduler);

        Flow.Publisher<String> publisher = sub -> {
            sub.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {
                    sub.onComplete();
                }

                @Override
                public void cancel() {
                }
            });
        };

        publisher.subscribe(subscriber);

        assertTrue(streamingComplete.get(2, TimeUnit.SECONDS) == null,
                "streamingComplete should complete normally");
    }
}
