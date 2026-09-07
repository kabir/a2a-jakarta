package org.wildfly.a2a.jakarta.test.multitenancy;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public abstract class AbstractMultiTenantTest {

    @BeforeAll
    static void restAssuredSetup() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = 8080;
    }

    protected static TestResponse getPublicCard(String tenant) {
        String path = tenant != null ? "/.well-known/" + tenant + "/agent-card.json" : "/.well-known/agent-card.json";
        var r = given().when().get(path);
        return new TestResponse(r.getStatusCode(), r.getBody().asString());
    }

    protected abstract TestResponse sendMessage(String tenant);

    protected abstract TestResponse streamMessage(String tenant);

    protected abstract TestResponse getExtendedCard(String tenant);

    // ---------- executor routing ----------

    @Test
    public void knownTenantRoutesToAcmeExecutor() {
        assertThat(sendMessage("acme").getBody(), containsString("acme"));
    }

    @Test
    public void secondTenantRoutesToBetaExecutor() {
        assertThat(sendMessage("beta").getBody(), containsString("beta"));
    }

    @Test
    public void unknownTenantFallsBackToDefault() {
        assertThat(sendMessage("unknown-corp").getBody(), containsString("default"));
    }

    @Test
    public void noTenantUsesDefault() {
        assertThat(sendMessage(null).getBody(), containsString("default"));
    }

    // ---------- extended card ----------

    @Test
    public void getExtendedAgentCardWithAcmeTenant() {
        assertThat(getExtendedCard("acme").getBody(), containsString("acme-extended"));
    }

    @Test
    public void getExtendedAgentCardWithBetaTenant() {
        assertThat(getExtendedCard("beta").getBody(), containsString("beta-extended"));
    }

    @Test
    public void getExtendedAgentCardWithUnknownTenant() {
        assertThat(getExtendedCard("unknown").getBody(), containsString("default-extended"));
    }

    @Test
    public void getExtendedAgentCardWithoutTenant() {
        assertThat(getExtendedCard(null).getBody(), containsString("default-extended"));
    }

    // ---------- streaming ----------

    @Test
    public void streamingWithKnownTenant() {
        TestResponse r = streamMessage("acme");
        assertEquals(200, r.getStatusCode());
        assertThat(r.getBody(), containsString("\"text\":\"acme\""));
    }

    @Test
    public void streamingWithUnknownTenantUsesDefault() {
        assertThat(streamMessage("unknown").getBody(), containsString("\"text\":\"default\""));
    }

    // ---------- public card ----------

    @Test
    public void getPublicAgentCardWithAcmeTenant() {
        assertThat(getPublicCard("acme").getBody(), containsString("Acme Agent"));
    }

    @Test
    public void getPublicAgentCardWithBetaTenant() {
        assertThat(getPublicCard("beta").getBody(), containsString("Beta Agent"));
    }

    @Test
    public void getPublicAgentCardWithUnknownTenantReturns404() {
        assertEquals(404, getPublicCard("unknown").getStatusCode());
    }

    @Test
    public void getPublicAgentCardWithoutTenantReturnsDefault() {
        assertThat(getPublicCard(null).getBody(), containsString("Multi-Tenant Test Agent"));
    }

    // ---------- concurrency ----------

    @Test
    public void concurrentRequestsForDifferentTenants() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            Future<String> acme = pool.submit(() -> sendMessage("acme").getBody());
            Future<String> beta = pool.submit(() -> sendMessage("beta").getBody());
            Future<String> def = pool.submit(() -> sendMessage(null).getBody());
            assertThat(acme.get(30, TimeUnit.SECONDS), containsString("acme"));
            assertThat(beta.get(30, TimeUnit.SECONDS), containsString("beta"));
            assertThat(def.get(30, TimeUnit.SECONDS), containsString("default"));
        } finally {
            pool.shutdownNow();
        }
    }
}
