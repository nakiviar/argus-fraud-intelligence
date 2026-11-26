package pe.jconf.argus.assessment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("demo-local")
class DashboardApiTests {
    @LocalServerPort
    private int port;

    @Test
    void servesTheDashboardAndLocalAssets() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var index = get(client, "/");
            assertEquals(200, index.statusCode());
            assertTrue(index.body().contains("Fraud investigation workspace"));
            assertEquals(200, get(client, "/css/styles.css").statusCode());
            assertEquals(200, get(client, "/js/app.js").statusCode());
        }
    }

    @Test
    void reportsSimulationWithoutClaimingLiveConnectivity() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var response = get(client, "/api/v1/dashboard/runtime");
            assertEquals(200, response.statusCode());
            var data = JsonMapper.builder().build().readTree(response.body());
            assertEquals("SIMULATED", data.path("mode").stringValue());
            assertEquals("NOT_RECORDED_IN_THIS_PROCESS", data.path("liveModelVerification").stringValue());
            assertFalse(data.path("traceExportEnabled").asBoolean());
            assertTrue(data.path("grafanaUrl").isNull());
            assertTrue(data.path("simulation").path("delayAgent").isNull());
        }
    }

    @Test
    void readsSyntheticTransactionDetailsWithoutChangingTheScenarioList() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var response = get(client, "/api/v1/scenarios/normal");
            assertEquals(200, response.statusCode());
            var data = JsonMapper.builder().build().readTree(response.body());
            assertEquals("SYN-TXN-001", data.path("transactionId").stringValue());
            assertTrue(data.path("sourceAccountId").stringValue().startsWith("SYN-"));
            assertTrue(data.path("amount").decimalValue().signum() > 0);
            assertEquals(404, get(client, "/api/v1/scenarios/unknown").statusCode());
        }
    }

    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
