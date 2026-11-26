# Roadmap

| Phase | Scope | Exit criteria |
| --- | --- | --- |
| 00 | Build baseline, profiles, Actuator, and documentation | Startup and local health work without cloud credentials. |
| 01 | Foundation corrections, immutable contracts, two-path architecture, fixtures, and tests | Clean Java 21 build passes and fixtures cover both paths. |
| 02 | Deterministic Hot Path with rules, graph fixtures, simulated predictions, decision policy, local REST API, and volatile audit | Identical synthetic inputs yield identical decision outcomes and reasons. Missing inputs are handled explicitly. |
| 03 | Isolated Microsoft Foundry model connectivity with offline simulation and an opt-in Entra-authenticated diagnostic | Clean verification exercises the Azure v1 request through a local double. Live verification requires an authorized deployment and explicit invocation. |
| 04 | Independent Graph and Behavioral agents with scoped read-only tools, structured output, and verified evidence | Each agent runs from the CLI with deterministic simulation or an explicitly invoked model. Scripted model tests verify the Spring AI tool loop. |
| 05 | Bounded concurrent fan-out/fan-in, deterministic aggregation, async REST API, in-memory lifecycle and events | Reports include partial results and preserve the original decision for human review. |
| 06 | Containment and programmatic Resilience4j policies | Offline demonstrations verify deadline gates, circuit recovery, bulkhead isolation, and immutable partial reports. |
| 07 | Micrometer observations, OpenTelemetry OTLP, Tempo, Grafana Explore and bounded event lookup | Offline tests verify context propagation, privacy and exporter failure. Azure Monitor is a separate proposal. |
| 08 | Dashboard | Reviewers can inspect decision rationale and evidence while running offline in demo-local. |

Phase 07 delivers local observability and tracing. Dashboard work and cloud provisioning require explicit approval.
