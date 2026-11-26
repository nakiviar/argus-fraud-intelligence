package pe.jconf.argus.assessment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("demo-local")
class RiskAssessmentApiTests {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ASSESSMENTS = "/api/v1/assessments";

    @LocalServerPort
    private int port;

    @Test
    void listsOnlySyntheticScenarioAndTransactionIdentifiers() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var response = get(client, "/api/v1/scenarios");
            assertEquals(200, response.statusCode());
            var scenarios = JSON.readTree(response.body());
            assertEquals(5, scenarios.size());
            assertEquals("normal", scenarios.get(0).path("scenarioId").stringValue());
            for (var scenario : scenarios) {
                assertEquals(2, scenario.size());
                assertTrue(scenario.path("transactionId").stringValue().startsWith("SYN-TXN-"));
            }
        }
    }

    @Test
    void createsAnAssessmentAndReturnsTheSameStoredSnapshot() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var response = post(client, """
                    {"scenarioId":"unavailable-evidence"}
                    """);
            assertEquals(201, response.statusCode());
            var body = JSON.readTree(response.body());
            var assessmentId = UUID.fromString(body.path("assessmentId").stringValue());
            assertEquals("SYN-TXN-004", body.path("transactionId").stringValue());
            assertEquals("REVIEW", body.path("decision").stringValue());
            assertEquals("synthetic-risk", body.path("policyId").stringValue());
            assertEquals("synthetic-v1", body.path("policyVersion").stringValue());
            assertEquals("GRAPH_DATA_UNAVAILABLE", body.path("reasonCodes").get(0).stringValue());
            assertEquals("UNAVAILABLE", body.at("/evidenceAvailability/GRAPH_DATA").stringValue());
            assertEquals("AVAILABLE", body.at("/evidenceAvailability/SYNTHETIC_ML").stringValue());
            assertEquals(2, body.path("verifiedEvidenceReferences").size());
            assertEquals("synthetic-fixtures-v1/unavailable-evidence/prediction",
                    body.at("/verifiedEvidenceReferences/1/recordId").stringValue());
            Instant.parse(body.path("assessedAt").stringValue());
            assertTrue(body.path("investigationRequired").asBoolean());
            assertFalse(body.has("sourceAccountId"));
            assertFalse(body.has("amount"));

            var location = response.headers().firstValue("Location").orElseThrow();
            assertEquals(ASSESSMENTS + "/" + assessmentId, location);
            var stored = get(client, location);
            assertEquals(200, stored.statusCode());
            assertEquals(body, JSON.readTree(stored.body()));
        }
    }

    @Test
    void validatesRequestShapeAndReturnsProblemDetails() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            for (var invalid : List.of("{}", "{", "[]", "null", "{\"scenarioId\":\"\"}",
                    "{\"scenarioId\":1}", "{\"scenarioId\":\"Invalid ID\"}",
                    "{\"scenarioId\":\"normal\",\"transaction\":{}}")) {
                assertProblem(post(client, invalid), 400);
            }
            var unsupported = client.send(HttpRequest.newBuilder(uri(ASSESSMENTS))
                    .header("Content-Type", "text/plain")
                    .POST(HttpRequest.BodyPublishers.ofString("normal")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertProblem(unsupported, 415);
            assertProblem(get(client, ASSESSMENTS + "/1-1-1-1-1"), 400);
        }
    }

    @Test
    void returnsNotFoundForUnknownScenarioAndAssessment() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            assertProblem(post(client, """
                    {"scenarioId":"unknown-scenario"}
                    """), 404);
            assertProblem(get(client, ASSESSMENTS + "/" + UUID.randomUUID()), 404);
        }
    }

    private void assertProblem(HttpResponse<String> response, int status) {
        assertEquals(status, response.statusCode(), response.body());
        assertTrue(response.headers().firstValue("Content-Type").orElseThrow().startsWith("application/problem+json"));
        var problem = JSON.readTree(response.body());
        assertEquals(status, problem.path("status").asInt());
        assertTrue(problem.path("title").isString());
    }

    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(path)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(HttpClient client, String body) throws Exception {
        var request = HttpRequest.newBuilder(uri(ASSESSMENTS)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }
}
