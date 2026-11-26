package pe.jconf.argus.investigation;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import pe.jconf.argus.assessment.RiskAssessmentService;
import pe.jconf.argus.domain.AgentType;
import pe.jconf.argus.domain.InvestigationContext;
import pe.jconf.argus.observability.ArgusTelemetry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"management.tracing.sampling.probability=1.0",
                "management.tracing.export.enabled=true", "management.tracing.export.otlp.enabled=false"})
@ActiveProfiles("demo-local")
class ObservabilityTests {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String INVESTIGATIONS = "/api/v1/investigations";
    private final ArgusTelemetry telemetry;
    private final MeterRegistry meters;
    private final InMemorySpanExporter exporter;
    private final SdkTracerProvider provider;
    private final RiskAssessmentService assessments;
    private final InvestigationFixtures fixtures;
    private final GraphInvestigationAgent graph;
    private final BehavioralInvestigationAgent behavioral;

    @LocalServerPort
    private int port;

    @Autowired
    ObservabilityTests(ArgusTelemetry telemetry, MeterRegistry meters, InMemorySpanExporter exporter,
                       SdkTracerProvider provider, RiskAssessmentService assessments, InvestigationFixtures fixtures,
                       GraphInvestigationAgent graph, BehavioralInvestigationAgent behavioral) {
        this.telemetry = telemetry;
        this.meters = meters;
        this.exporter = exporter;
        this.provider = provider;
        this.assessments = assessments;
        this.fixtures = fixtures;
        this.graph = graph;
        this.behavioral = behavioral;
    }

    @Test
    void httpInvestigationHasSiblingAgentSpansAndExportableTraceWithoutModelCalls() throws Exception {
        try (var http = HttpClient.newHttpClient()) {
            var assessment = request(http, "/api/v1/assessments", Map.of("scenarioId", "additional-review"));
            var created = request(http, INVESTIGATIONS, Map.of("assessmentId", assessment.path("assessmentId").asString()));
            var path = INVESTIGATIONS + "/" + created.path("investigationId").asString();
            assertEquals("COMPLETED", await(() -> {
                var current = request(http, path, null);
                return current.path("status").asString().equals("COMPLETED") ? current : null;
            }).path("status").asString());
            var events = request(http, path + "/events", null);
            var traceId = events.get(0).path("traceId").asString();
            assertTrue(traceId.matches("[0-9a-f]{32}"));
            assertTrue(events.get(0).path("spanId").asString().matches("[0-9a-f]{16}"));

            var trace = await(() -> {
                var spans = flush().stream().filter(span -> span.getTraceId().equals(traceId)).toList();
                return spans.size() >= 9 ? spans : null;
            });
            var run = only(trace, "argus.investigation");
            var creation = only(trace, "argus.investigation.create");
            assertEquals(creation.getSpanId(), run.getParentSpanId());
            assertEquals("SERVER", trace.stream().filter(span -> span.getSpanId().equals(creation.getParentSpanId()))
                    .findFirst().orElseThrow().getKind().name());
            var agents = trace.stream().filter(span -> span.getName().equals("argus.agent")).toList();
            assertEquals(2, agents.size());
            for (var agent : agents) {
                assertEquals(run.getSpanId(), agent.getParentSpanId());
                assertEquals("SIMULATED", agent.getAttributes().asMap().entrySet().stream()
                        .filter(entry -> entry.getKey().getKey().equals("mode")).findFirst().orElseThrow().getValue());
            }
            assertEquals(run.getSpanId(), only(trace, "argus.evidence.aggregation").getParentSpanId());
            var all = flush();
            var decision = all.stream().filter(span -> span.getName().equals("argus.assessment.decision"))
                    .reduce((first, second) -> second).orElseThrow();
            assertNotEquals(traceId, decision.getTraceId());
            var assessed = all.stream().filter(span -> span.getTraceId().equals(decision.getTraceId())
                    && span.getName().equals("argus.assessment")).findFirst().orElseThrow();
            var link = run.getLinks().getFirst();
            assertEquals(assessed.getTraceId(), link.getSpanContext().getTraceId());
            assertEquals(assessed.getSpanId(), link.getSpanContext().getSpanId());
            assertTrue(link.getAttributes().isEmpty());
            assertTrue(all.stream().filter(span -> span.getTraceId().equals(decision.getTraceId())
                    || span.getTraceId().equals(traceId)).noneMatch(span -> span.getAttributes().asMap().keySet().stream()
                            .anyMatch(key -> key.getKey().startsWith("gen_ai."))));
            assertTrue(meters.find("argus.tool.executions").tag("mode", "SIMULATED").counters().stream()
                    .mapToDouble(counter -> counter.count()).sum() >= 2);

            if (Boolean.getBoolean("argus.observability.write-example")) {
                var rows = trace.stream().map(span -> Map.of("name", span.getName(), "traceId", span.getTraceId(),
                        "spanId", span.getSpanId(), "parentSpanId", span.getParentSpanId(),
                        "startEpochNanos", span.getStartEpochNanos(), "endEpochNanos", span.getEndEpochNanos(),
                        "kind", span.getKind().name(), "links", span.getLinks().stream().map(item -> Map.of(
                                        "traceId", item.getSpanContext().getTraceId(),
                                        "spanId", item.getSpanContext().getSpanId())).toList())).toList();
                Files.writeString(Path.of("target", "phase07-example-trace.json"),
                        JSON.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                                "mode", "SIMULATED", "investigationId", created.path("investigationId").asString(),
                                "traceId", traceId, "spans", rows)));
            }
        }
    }

    @Test
    void distinctConcurrentInvestigationsKeepIndependentTraceContexts() throws Exception {
        var allStarted = new CountDownLatch(4);
        var release = new CountDownLatch(1);
        try (var orchestrator = orchestrator(gated(graph, allStarted, release),
                gated(behavioral, allStarted, release), Duration.ofSeconds(5))) {
            var first = start(orchestrator);
            var second = start(orchestrator);
            assertTrue(allStarted.await(4, TimeUnit.SECONDS));
            assertNotEquals(traceId(first), traceId(second));
            release.countDown();
            await(() -> terminal(orchestrator, first));
            await(() -> terminal(orchestrator, second));
            for (var id : List.of(first, second)) {
                var trace = traceId(id);
                assertTrue(telemetry.events(id).stream().allMatch(event -> trace.equals(event.traceId())));
                var spans = await(() -> {
                    var found = flush().stream().filter(span -> span.getTraceId().equals(trace)).toList();
                    return found.size() >= 8 ? found : null;
                });
                var root = only(spans, "argus.investigation");
                assertEquals(2, spans.stream().filter(span -> span.getParentSpanId().equals(root.getSpanId())
                        && span.getName().equals("argus.agent")).count());
            }
        } finally {
            release.countDown();
        }
    }

    @Test
    void timeoutAndLateWorkerAreCorrelatedAndCountedWithoutReopeningTheReport() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var orchestrator = orchestrator(gated(graph, entered, release), behavioral, Duration.ofSeconds(1))) {
            var id = start(orchestrator);
            assertTrue(entered.await(4, TimeUnit.SECONDS));
            var completed = await(() -> terminal(orchestrator, id));
            assertEquals(InvestigationStatus.PARTIAL, completed.status());
            var traceId = traceId(id);
            assertTrue(telemetry.events(id).stream().anyMatch(event ->
                    event.eventType().equals("AGENT_TIMEOUT") && traceId.equals(event.traceId())));
            release.countDown();
            await(() -> telemetry.events(id).stream().filter(event ->
                    event.eventType().equals("LATE_RESULT_DISCARDED")).findFirst().orElse(null));
            assertEquals(completed, orchestrator.find(id).orElseThrow());
            assertTrue(meters.get("argus.resilience.events").tag("event", "AGENT_TIMEOUT").counter().count() >= 1);
            assertTrue(meters.get("argus.resilience.events").tag("event", "FALLBACK").counter().count() >= 1);
            assertTrue(meters.get("argus.resilience.events").tag("event", "LATE_RESULT_DISCARDED").counter().count() >= 1);
        } finally {
            release.countDown();
        }
    }

    @Test
    void exportedSpanStripsUntrustedAttributesAndExceptionContents() {
        var secret = "SYNTHETIC_SECRET_PROBE_917";
        assertThrows(IllegalStateException.class, () -> telemetry.operation("argus.privacy.probe")
                .highCardinalityKeyValue("prompt", secret).observe(() -> {
                    throw new IllegalStateException(secret);
                }));
        var span = flush().stream().filter(value -> value.getName().equals("argus.privacy.probe"))
                .findFirst().orElseThrow();
        assertFalse(span.getAttributes().toString().contains(secret));
        assertFalse(span.getEvents().toString().contains(secret));
        assertFalse(span.toString().contains(secret));
        meters.getMeters().stream().filter(meter -> meter.getId().getName().startsWith("argus."))
                .forEach(meter -> meter.getId().getTags().forEach(tag -> assertTrue(
                        List.of("agent", "mode", "status", "event", "outcome", "error", "exception", "late", "token.usage")
                                .contains(tag.getKey()), tag::toString)));
    }

    @Test
    void eventHistoryEvictsOldestEntriesAndScopesReadsToOneCase() {
        var oldCase = UUID.randomUUID();
        var otherCase = UUID.randomUUID();
        var time = java.time.Instant.now();
        telemetry.lifecycle(new InvestigationEvent(oldCase, InvestigationEvent.Type.INVESTIGATION_CREATED,
                null, null, null, InvestigationStatus.PENDING, time), AgentDiagnosticResult.Mode.SIMULATED);
        for (int index = 0; index < 512; index++) {
            telemetry.lifecycle(new InvestigationEvent(otherCase, InvestigationEvent.Type.AGENT_STARTED,
                    AgentType.GRAPH, null, null, InvestigationStatus.RUNNING, time), AgentDiagnosticResult.Mode.SIMULATED);
        }
        assertTrue(telemetry.events(oldCase).isEmpty());
        assertEquals(512, telemetry.events(otherCase).size());
        assertTrue(telemetry.events(UUID.randomUUID()).isEmpty());
    }

    @Test
    void respectsIncomingW3cTraceparentWithoutExportingBaggageOrTraceState() throws Exception {
        var traceId = "60f94c147c9146cda4a0a884155a8020";
        var parent = "1234567890abcdef";
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/assessments"))
                    .header("Content-Type", "application/json")
                    .header("traceparent", "00-" + traceId + "-" + parent + "-01")
                    .header("tracestate", "demo=private-probe")
                    .header("baggage", "demo=private-probe")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"scenarioId\":\"normal\"}")).build();
            assertEquals(201, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
            var spans = await(() -> {
                var found = flush().stream().filter(span -> span.getTraceId().equals(traceId)).toList();
                return found.size() >= 3 ? found : null;
            });
            var server = spans.stream().filter(span -> span.getKind().name().equals("SERVER"))
                    .findFirst().orElseThrow();
            assertEquals(parent, server.getParentSpanId());
            assertTrue(server.getSpanContext().getTraceState().isEmpty());
            assertTrue(server.getParentSpanContext().getTraceState().isEmpty());
            assertFalse(server.getAttributes().toString().contains("private-probe"));
        }
    }

    private InvestigationOrchestrator orchestrator(InvestigationAgent first, InvestigationAgent second, Duration budget) {
        return new InvestigationOrchestrator(new InvestigationRepository(telemetry::lifecycle), assessments, fixtures,
                List.of(first, second), AgentDiagnosticResult.Mode.SIMULATED, null,
                new InvestigationBudgets(Duration.ofSeconds(9), budget, Duration.ofMillis(100)),
                telemetry::resilience, null, Duration.ZERO, telemetry);
    }

    private UUID start(InvestigationOrchestrator orchestrator) {
        return orchestrator.start(assessments.assess("additional-review").orElseThrow().assessmentId())
                .snapshot().investigationId();
    }

    private static InvestigationSnapshot terminal(InvestigationOrchestrator orchestrator, UUID id) {
        var state = orchestrator.find(id).orElseThrow();
        return state.status().isTerminal() ? state : null;
    }

    private String traceId(UUID id) {
        return telemetry.events(id).getFirst().traceId();
    }

    private static InvestigationAgent gated(InvestigationAgent delegate, CountDownLatch started, CountDownLatch release) {
        return new InvestigationAgent() {
            @Override
            public AgentType type() {
                return delegate.type();
            }

            @Override
            public AgentDiagnosticResult investigate(InvestigationContext context) {
                started.countDown();
                boolean interrupted = false;
                for (;;) {
                    try {
                        release.await();
                        break;
                    } catch (InterruptedException exception) {
                        interrupted = true;
                    }
                }
                try {
                    return delegate.investigate(context);
                } finally {
                    if (interrupted) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        };
    }

    private List<SpanData> flush() {
        assertTrue(provider.forceFlush().join(5, TimeUnit.SECONDS).isSuccess());
        return exporter.getFinishedSpanItems();
    }

    private static SpanData only(List<SpanData> spans, String name) {
        var matches = spans.stream().filter(span -> span.getName().equals(name)).toList();
        assertEquals(1, matches.size(), name);
        return matches.getFirst();
    }

    private static <T> T await(Supplier<T> query) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        do {
            var result = query.get();
            if (result != null) {
                return result;
            }
            Thread.sleep(10);
        } while (System.nanoTime() < end);
        throw new AssertionError("Expected telemetry was not available.");
    }

    private JsonNode request(HttpClient http, String path, Object payload) {
        try {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
            if (payload != null) {
                request.header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(payload)));
            }
            var response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            assertTrue(response.statusCode() >= 200 && response.statusCode() < 300, response::body);
            return JSON.readTree(response.body());
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ExporterConfiguration {
        @Bean
        InMemorySpanExporter testSpanExporter() {
            return InMemorySpanExporter.create();
        }
    }
}
