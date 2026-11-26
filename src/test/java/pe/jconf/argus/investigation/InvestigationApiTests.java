package pe.jconf.argus.investigation;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("demo-local")
class InvestigationApiTests {
    private static final String PATH = "/api/v1/investigations";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    private int port;

    @Test
    void createsAsynchronouslyAndRetrievesAnImmutableReportForAnExistingReview() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var assessment = assessment(client, "additional-review");
            var payload = payload(assessment);
            var created = send(client, PATH, payload);
            assertEquals(202, created.statusCode());
            var pending = JSON.readTree(created.body());
            assertEquals("PENDING", pending.path("status").asString());
            assertEquals("SIMULATED", pending.path("mode").asString());
            var location = created.headers().firstValue("Location").orElseThrow();
            assertEquals(PATH + "/" + pending.path("investigationId").asString(), location);

            var completed = await(client, location);
            assertEquals("COMPLETED", completed.path("status").asString());
            assertEquals(2, completed.path("agentResults").size());
            assertEquals(2, completed.path("verifiedEvidenceReferences").size());
            assertEquals(assessment.path("assessmentId"), completed.path("assessmentId"));
            assertEquals(assessment.path("assessedAt"), completed.at("/originalRiskDecision/decidedAt"));
            assertEquals("REVIEW", completed.at("/originalRiskDecision/decision").asString());
            assertEquals(completed.path("originalRiskDecision"), completed.at("/report/context/riskDecision"));
            assertEquals("INVESTIGATION_COMPLETED", completed.path("events").get(completed.path("events").size() - 1).path("type").asString());
            var repeat = send(client, PATH, payload);
            assertEquals(200, repeat.statusCode());
            assertEquals(completed, JSON.readTree(repeat.body()));
        }
    }

    @Test
    void preservesBehavioralEvidenceWhenGraphEvidenceIsUnavailableOrInvalid() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            for (var scenario : java.util.List.of("unavailable-evidence", "invalid-agent-response")) {
                var response = send(client, PATH, payload(assessment(client, scenario)));
                assertEquals(202, response.statusCode());
                var result = await(client, response.headers().firstValue("Location").orElseThrow());
                assertEquals("PARTIAL", result.path("status").asString());
                assertEquals("SUCCESS", result.at("/agentResults/1/status").asString());
                assertEquals(scenario.equals("unavailable-evidence") ? "UNAVAILABLE" : "INVALID_RESPONSE",
                        result.at("/agentResults/0/status").asString());
                assertEquals(1, result.path("verifiedEvidenceReferences").size());
                assertEquals("BEHAVIORAL", result.at("/verifiedEvidenceReferences/0/type").asString());
            }
        }
    }

    @Test
    void rejectsUnknownNonReviewAndInvalidRequestsWithProblemDetails() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            assertProblem(send(client, PATH, "{\"assessmentId\":\"" + UUID.randomUUID() + "\"}"), 404);
            assertProblem(send(client, PATH, payload(assessment(client, "normal"))), 409);
            assertProblem(send(client, PATH, payload(assessment(client, "mandatory-policy"))), 409);
            assertProblem(send(client, PATH, "{\"assessmentId\":\"bad\"}"), 400);
            assertProblem(send(client, PATH, "{\"prompt\":\"hello\"}"), 400);
            assertProblem(send(client, PATH + "/" + UUID.randomUUID(), null), 404);
            assertProblem(send(client, PATH + "/bad", null), 400);
        }
    }

    private JsonNode assessment(HttpClient client, String scenario) throws Exception {
        var response = send(client, "/api/v1/assessments", "{\"scenarioId\":\"" + scenario + "\"}");
        assertEquals(201, response.statusCode());
        return JSON.readTree(response.body());
    }

    private static String payload(JsonNode assessment) {
        return "{\"assessmentId\":\"" + assessment.path("assessmentId").asString() + "\"}";
    }

    private JsonNode await(HttpClient client, String location) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        do {
            var response = send(client, location, null);
            assertEquals(200, response.statusCode());
            var body = JSON.readTree(response.body());
            var state = InvestigationStatus.valueOf(body.path("status").asString());
            if (state.isTerminal()) {
                return body;
            }
            Thread.sleep(15);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Investigation did not reach a terminal status.");
    }

    private HttpResponse<String> send(HttpClient client, String path, String payload) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (payload != null) {
            builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(payload));
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void assertProblem(HttpResponse<String> response, int expected) {
        assertEquals(expected, response.statusCode(), response.body());
        assertTrue(response.headers().firstValue("Content-Type").orElseThrow().startsWith("application/problem+json"));
        assertEquals(expected, JSON.readTree(response.body()).path("status").asInt());
    }
}
