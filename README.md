# ARGUS Fraud Intelligence

**Agents investigate. Deterministic code decides.**

ARGUS is a fictional banking fraud investigation proof of concept for JConf Peru 2026. ARGUS exposes an offline Hot Path for synthetic assessments. Phase 03 adds a separate command-line connectivity diagnostic for Microsoft Foundry. Two independently executable agents can also run concurrently in a separate asynchronous investigation for stored REVIEW assessments.

## Prerequisites

Use a Java 21 JDK installed outside the project build directory and Maven 3.9.9. Select the JDK for the current shell. For example, PowerShell can use a persistent JDK installed under the user profile:

```powershell
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\ms-21.0.8"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn -version
```

Adjust the path to match your installation. Maven needs access to Maven Central for the first build unless dependencies and plugins are cached.

```sh
mvn clean verify
java -jar target/argus-fraud-intelligence-0.1.0-SNAPSHOT.jar
```

Open http://127.0.0.1:8080/actuator/health. The default profile is `demo-local`. The packaged application runs offline and uses no cloud credentials. Use `mvn -o test` after caching the test dependencies and plugins.

## Run a synthetic assessment

```powershell
$base = 'http://127.0.0.1:8080'
Invoke-RestMethod "$base/api/v1/scenarios"
Invoke-RestMethod "$base/api/v1/assessments" -Method Post -ContentType 'application/json' -Body '{"scenarioId":"normal"}'
```

The normal scenario returns APPROVE. The assessment is stored in memory until the process stops. See the [API guide](docs/api.md) for all endpoints and examples.

## Model connectivity diagnostic

```powershell
$jar = 'target/argus-fraud-intelligence-0.1.0-SNAPSHOT.jar'
java -jar $jar --spring.profiles.active=demo-local --check-model-connectivity
```

The offline diagnostic prints a SIMULATED result and exits. The `demo-azure` profile requires `AZURE_OPENAI_ENDPOINT`, `AZURE_OPENAI_DEPLOYMENT`, and a short-lived `AZURE_OPENAI_ACCESS_TOKEN` from Microsoft Entra ID. See the [Azure integration guide](docs/azure-integration.md) for safe PowerShell instructions and the explicitly invoked Azure check. Ordinary startup and Actuator health perform no inference.

## Run an investigation agent

```powershell
$jar = 'target/argus-fraud-intelligence-0.1.0-SNAPSHOT.jar'
java -jar $jar --spring.profiles.active=demo-local --check-investigation-agent=graph --scenario=additional-review
java -jar $jar --spring.profiles.active=demo-local --check-investigation-agent=behavioral --scenario=additional-review
```

Each command runs one simulated agent, returns its evidence and validation outcome, and exits. See [Agent Engineering](docs/agent-engineering.md) for the missing-evidence example, the invalid-response example, and the opt-in Azure commands.

## Run both agents on a REVIEW assessment

```powershell
$base = 'http://127.0.0.1:8080'
$assessment = Invoke-RestMethod "$base/api/v1/assessments" -Method Post -ContentType 'application/json' -Body '{"scenarioId":"additional-review"}'
$body = @{ assessmentId = $assessment.assessmentId } | ConvertTo-Json -Compress
$submission = Invoke-WebRequest "$base/api/v1/investigations" -Method Post -ContentType 'application/json' -Body $body
Invoke-RestMethod ($base + [string]$submission.Headers.Location)
```

The default profile runs both simulated agents. Repeat the GET to retrieve the final report. See [API](docs/api.md) for examples and errors, and [Multi-agent orchestration](docs/multi-agent-orchestration.md) for bounds, failure handling, and the opt-in behavior in `demo-azure`.

## Demonstrate resilience offline

The Investigation Path includes shared model protection, per-agent isolation, and configurable time budgets. Use the local failure controls and scripted checks in [Offline failure scenarios](docs/failure-scenarios.md) to demonstrate partial reports, circuit recovery, and retained permits.

## Trace the offline demo

Use the opt-in `demo-observability` profile with `demo-local` or an authorized `demo-azure` deployment. The demo can send sanitized OTLP traces to local Tempo and inspect them in Grafana Explore. Basic local execution remains independent of Docker and telemetry services.

See [Observability demo](docs/observability-demo.md) for startup, the five trace scenarios, and a diagnostic that runs without Docker.

## Project documentation

- [Contributor instructions](AGENTS.md)
- [Architecture](docs/architecture.md)
- [Azure integration](docs/azure-integration.md)
- [Spring AI foundations](docs/spring-ai-foundations.md)
- [Agent engineering](docs/agent-engineering.md)
- [Multi-agent orchestration](docs/multi-agent-orchestration.md)
- [Investigation lifecycle](docs/investigation-lifecycle.md)
- [Observability](docs/observability.md)
- [Distributed tracing](docs/distributed-tracing.md)
- [Observability demo](docs/observability-demo.md)
- [Resilience](docs/resilience.md)
- [Containment](docs/containment.md)
- [Offline failure scenarios](docs/failure-scenarios.md)
- [Tool Calling](docs/tool-calling.md)
- [Structured Output](docs/structured-output.md)
- [Deterministic baseline](docs/deterministic-baseline.md)
- [API guide](docs/api.md)
- [Domain model](docs/domain-model.md)
- [Synthetic scenarios](src/main/resources/synthetic/scenarios.json)
- [Roadmap](docs/roadmap.md)

## Phase 08 dashboard

Run the packaged JAR with demo-local and visit http://127.0.0.1:8080/. See [dashboard guide](docs/dashboard.md) and [conference runbook](docs/dashboard-demo.md).

## Conference preparation

ARGUS uses Java 21, Spring Boot 4.1.1, Spring AI 2.0.1, Resilience4j 2.4.0, Micrometer and OpenTelemetry. Review the [as-built architecture](docs/conference/ARCHITECTURE-AS-BUILT.md), [runbook](docs/conference/DEMO-RUNBOOK.md), [study guide](docs/conference/CONFERENCE-STUDY-GUIDE.md), [benchmarks](docs/conference/BENCHMARK-REPORT.md), and [conference assets](docs/conference/assets/README.md).

The demo has normal, mandatory policy violation, additional review, invalid response and unavailable evidence fixtures. Local startup flags support a controlled failure or timeout. Domain data and local generation are synthetic. Storage is volatile, demo HTTP binds to loopback, and the prototype does not provide production identity, durable audit or validated fraud detection.
