package org.wildfly.a2a.jakarta.test.multitenancy;

public final class TestResponse {

    private final int statusCode;
    private final String body;

    public TestResponse(int statusCode, String body) {
        this.statusCode = statusCode;
        this.body = body;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getBody() {
        return body;
    }
}
