package pe.jconf.argus.ai;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pe.jconf.argus.ai.ModelConnectivityResult.ErrorCategory;

class FoundryConnectivityTests {
    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private static final String FIXTURE_TOKEN = "test-only-bearer";
    private static final String FIXTURE_DEPLOYMENT = "conference-deployment";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<CapturedRequest> request = new AtomicReference<>();
    private final AtomicReference<Reply> reply = new AtomicReference<>(
            new Reply(200, chatResponse("ARGUS_CONNECTIVITY_OK")));

    private HttpServer server;
    private SpringAiOpenAiHttpClient transport;
    private ModelConnectivityService service;

    @BeforeEach
    void startHttpDouble() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                calls.incrementAndGet();
                request.set(new CapturedRequest(exchange.getRequestMethod(), exchange.getRequestURI(),
                        exchange.getRequestHeaders().getFirst("Authorization"),
                        exchange.getRequestHeaders().getFirst("api-key"),
                        new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
                var response = reply.get();
                var bytes = response.json().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(response.status(), bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
        server.start();

        var baseUrl = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/openai/v1");
        var settings = new FoundrySettings(baseUrl, FIXTURE_DEPLOYMENT, TIMEOUT, 0, 64);
        transport = SpringAiOpenAiHttpClient.builder().timeout(TIMEOUT).build();
        service = new AzureModelConnectivityService(
                FoundryConnectivityConfiguration.chatClient(settings, transport, FIXTURE_TOKEN), FIXTURE_DEPLOYMENT);
    }

    @AfterEach
    void stopHttpDouble() {
        server.stop(0);
        transport.close();
    }

    @Test
    void usesAzureV1ChatCompletionsAndTheDeploymentAsModel() {
        assertEquals(0, calls.get());
        var result = service.check();

        assertEquals(ModelConnectivityResult.Mode.AZURE, result.mode());
        assertTrue(result.connected());
        assertEquals(FIXTURE_DEPLOYMENT, result.deployment());
        assertEquals("ARGUS_CONNECTIVITY_OK", result.response());
        assertEquals(new ModelConnectivityResult.TokenUsage(20, 5, 25), result.tokenUsage());

        var captured = request.get();
        assertEquals("POST", captured.method());
        assertEquals("/openai/v1/chat/completions", captured.uri().toString());
        assertEquals("Bearer " + FIXTURE_TOKEN, captured.authorization());
        assertNull(captured.apiKey());
        var json = JSON.readTree(captured.json());
        assertEquals(FIXTURE_DEPLOYMENT, json.path("model").stringValue());
        assertEquals(64, json.path("max_completion_tokens").asInt());
        assertEquals(1, json.path("messages").size());
        assertEquals("Reply with exactly these characters and nothing else: ARGUS_CONNECTIVITY_OK",
                json.at("/messages/0/content").stringValue());
        assertFalse(json.has("tools"));
        assertEquals(1, calls.get());
    }

    @Test
    void categorizesProviderErrorsWithoutRetriesOrSensitiveOutput() {
        expectFailure(401, ErrorCategory.AUTHENTICATION);
        expectFailure(403, ErrorCategory.AUTHENTICATION);
        expectFailure(404, ErrorCategory.DEPLOYMENT_UNAVAILABLE);
        expectFailure(429, ErrorCategory.RATE_LIMIT);
        expectFailure(500, ErrorCategory.PROVIDER_ERROR);
        assertEquals(5, calls.get());
    }

    @Test
    void emitsOnlyTheExpectedAcknowledgement() {
        reply.set(new Reply(200, chatResponse("fixture response with sensitive-looking content")));
        var result = service.check();
        assertFalse(result.connected());
        assertNull(result.response());
        assertEquals(ErrorCategory.UNEXPECTED_RESPONSE, result.errorCategory());
    }

    @Test
    void distinguishesConnectionFailure() {
        server.stop(0);
        var result = service.check();
        assertFalse(result.connected());
        assertEquals(ErrorCategory.CONNECTION, result.errorCategory());
        assertNull(result.response());
    }

    private void expectFailure(int status, ErrorCategory category) {
        reply.set(new Reply(status, """
                {"error":{"message":"private provider error and credential data","code":"fixture_error"}}
                """));
        var result = service.check();
        assertEquals(category, result.errorCategory());
        assertFalse(result.connected());
        assertNull(result.response());
        assertNull(result.tokenUsage());
        assertFalse(JSON.writeValueAsString(result).contains("private provider error"));
    }

    private static String chatResponse(String content) {
        return """
                {
                  "id":"chatcmpl-fixture","object":"chat.completion","created":1770000000,"model":"base-model-fixture",
                  "choices":[{"index":0,"message":{"role":"assistant","content":"%s"},"finish_reason":"stop"}],
                  "usage":{"prompt_tokens":20,"completion_tokens":5,"total_tokens":25}
                }
                """.formatted(content);
    }

    private record CapturedRequest(String method, URI uri, String authorization, String apiKey, String json) {
    }

    private record Reply(int status, String json) {
    }
}
