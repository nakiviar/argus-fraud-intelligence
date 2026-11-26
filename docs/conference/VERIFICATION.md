# Verification evidence

The clean build used Microsoft OpenJDK 21.0.8, Maven 3.9.9 and the local dependency cache. Maven reported 64 tests, zero failures, zero errors and zero skipped.

| Component | Version |
|---|---|
| Spring Boot parent, starters and Maven plugin | 4.1.1 |
| Spring AI BOM, OpenAI starter and integration | 2.0.1 |
| OpenAI Java SDK core | 4.49.0 |
| Resilience4j core, CircuitBreaker, Bulkhead and TimeLimiter | 2.4.0 |
| Micrometer Core and Observation | 1.17.1 |
| Micrometer Tracing and OpenTelemetry bridge | 1.7.1 |
| OpenTelemetry SDK and OTLP exporter | 1.62.0 |
| Embedded Tomcat | 11.0.24 |
| JUnit Jupiter | 6.0.3 |
| Mockito Core | 5.23.0 |
| OpenTelemetry SDK testing | 1.62.0 |
| Tempo and Grafana Compose image pins | 2.9.0 and 12.4.2 |

The POM and packaged JAR establish the dependency versions. Runtime validation used the bundled JAR, Chrome and loopback only. Maven tests exercise concurrent agent execution, structured conversion, callback scope and provenance, deadline and isolation behavior, trace context propagation and OTLP/HTTP protobuf delivery to a local receiver. Dashboard flow was also checked against the actual JAR. The additional span and OTLP test run passed two tests.

Live Foundry inference, Grafana rendering and Azure Monitor remain pending. Docker CLI reported that the Docker Desktop Linux engine pipe was unavailable.
