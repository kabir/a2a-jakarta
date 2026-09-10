package org.wildfly.a2a.jakarta.rest;

import static org.a2aproject.sdk.spec.A2AMethods.GET_TASK_PUSH_NOTIFICATION_CONFIG_METHOD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.SecurityContext;

import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.auth.UnauthenticatedUser;
import org.a2aproject.sdk.transport.rest.context.RestContextKeys;
import org.a2aproject.sdk.transport.rest.handler.RestHandler;
import org.junit.jupiter.api.Test;
import org.wildfly.a2a.jakarta.common.TenantHolder;

class A2ARestServerResourceDelegateTest {

    @Test
    void getTaskPushNotificationConfigurationUsesGetMethodContext() {
        AtomicReference<ServerCallContext> capturedContext = new AtomicReference<>();
        RestHandler restHandler = new RestHandler() {
            @Override
            public HTTPRestResponse getTaskPushNotificationConfiguration(ServerCallContext context, String tenant,
                    String taskId, String configId) {
                capturedContext.set(context);
                return new HTTPRestResponse(200, "application/json", "{}");
            }
        };

        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(), new Class<?>[] { HttpServletRequest.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("getRequestURI")) {
                        return "/a2a_rest_v1.0/tasks/task/pushNotificationConfigs/";
                    }
                    if (method.getReturnType().equals(boolean.class)) {
                        return false;
                    }
                    if (method.getReturnType().isPrimitive()) {
                        return 0;
                    }
                    return null;
                });
        SecurityContext securityContext = null;
        var scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            A2ARestServerResourceDelegate delegate = new A2ARestServerResourceDelegate(
                    restHandler, new TenantHolder(), scheduler) {
                @Override
                protected ServerCallContext createCallContext(HttpServletRequest request, SecurityContext securityContext,
                        String methodName) {
                    return new ServerCallContext(UnauthenticatedUser.INSTANCE,
                            Map.of(RestContextKeys.METHOD_NAME_KEY, methodName), Set.of(), null);
                }
            };

            assertThrows(RuntimeException.class,
                    () -> delegate.getOrListTaskPushNotificationConfigurations("task", request, securityContext));
            assertEquals(GET_TASK_PUSH_NOTIFICATION_CONFIG_METHOD,
                    capturedContext.get().getState().get(RestContextKeys.METHOD_NAME_KEY));
        } finally {
            scheduler.shutdownNow();
        }
    }
}
