package pe.jconf.argus.investigation;

import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import pe.jconf.argus.ArgusApplication;
import pe.jconf.argus.assessment.RiskAssessmentService;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class OtlpExportTests {
    @Test
    void sendsRealOtlpHttpProtobufToLoopbackAndSurvivesExporterFailure() throws Exception {
        var received = new CountDownLatch(1);
        var rejected = new CountDownLatch(1);
        var requestBody = new AtomicReference<byte[]>();
        var contentType = new AtomicReference<String>();
        var responseCode = new AtomicInteger(200);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/traces", exchange -> {
            try (exchange) {
                contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
                requestBody.set(exchange.getRequestBody().readAllBytes());
                int status = responseCode.get();
                exchange.sendResponseHeaders(status, -1);
                if (status == 503) {
                    rejected.countDown();
                }
            } finally {
                received.countDown();
            }
        });
        server.start();
        try (var context = new SpringApplicationBuilder(ArgusApplication.class)
                .web(WebApplicationType.NONE).bannerMode(Banner.Mode.OFF).logStartupInfo(false)
                .properties("logging.level.root=OFF")
                .run("--spring.profiles.active=demo-local,demo-observability",
                        "--management.opentelemetry.tracing.export.otlp.endpoint=http://127.0.0.1:"
                                + server.getAddress().getPort() + "/v1/traces")) {
            var assessments = context.getBean(RiskAssessmentService.class);
            var provider = context.getBean(SdkTracerProvider.class);
            var assessment = assessments.assess("additional-review").orElseThrow();
            var orchestrator = context.getBean(InvestigationOrchestrator.class);
            var id = orchestrator.start(assessment.assessmentId()).snapshot().investigationId();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!orchestrator.find(id).orElseThrow().status().isTerminal() && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertEquals(InvestigationStatus.COMPLETED, orchestrator.find(id).orElseThrow().status());
            var metrics = context.getBean(io.micrometer.core.instrument.MeterRegistry.class);
            while (metrics.find("argus.investigation").timer() == null && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertNotNull(metrics.find("argus.investigation").timer());
            provider.forceFlush().join(5, TimeUnit.SECONDS);
            assertTrue(received.await(5, TimeUnit.SECONDS));
            assertTrue(contentType.get().startsWith("application/x-protobuf"));
            var wire = requestBody.get();
            var encoded = new String(wire, StandardCharsets.ISO_8859_1);
            assertTrue(encoded.contains("argus.investigation"));
            assertTrue(encoded.contains("argus.agent"));
            if (Boolean.getBoolean("argus.observability.write-example")) {
                Files.write(Path.of("target", "phase07-example-investigation.otlp.pb"), wire);
            }
            responseCode.set(503);
            assertTrue(assessments.assess("additional-review").isPresent());
            provider.forceFlush().join(5, TimeUnit.SECONDS);
            assertTrue(rejected.await(5, TimeUnit.SECONDS));
            assertTrue(assessments.assess("normal").isPresent());
        } finally {
            server.stop(0);
        }
    }
}
