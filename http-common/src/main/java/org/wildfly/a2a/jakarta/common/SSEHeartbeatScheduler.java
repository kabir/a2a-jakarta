/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.a2a.jakarta.common;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SSEHeartbeatScheduler {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sse-disconnect-detector");
        t.setDaemon(true);
        return t;
    });

    public ScheduledExecutorService getScheduler() {
        return scheduler;
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }
}
