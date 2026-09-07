/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.a2a.jakarta.common;

import jakarta.inject.Inject;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

@WebListener
public class SSESubscriberLifecycleListener implements ServletContextListener {

    @Inject
    SSEHeartbeatScheduler heartbeatScheduler;

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        heartbeatScheduler.shutdown();
    }
}
