# Roadmap

| Phase | Scope | Exit criteria |
| --- | --- | --- |
| 00 | Build baseline, profiles, Actuator, and documentation | Startup and local health work without cloud credentials. |
| 01 | Foundation corrections, immutable contracts, two-path architecture, fixtures, and tests | Clean Java 21 build passes and fixtures cover both paths. |
| 02 | Deterministic Hot Path with rules, graph fixtures, simulated predictions, decision policy, local REST API, and volatile audit | Identical synthetic inputs yield identical decision outcomes and reasons. Missing inputs are handled explicitly. |
| 03 | Investigation Path, including local responses, concurrent orchestration, tools, evidence validation, Foundry integration, and resilience | Investigation reports include partial results and preserve the original decision for human review. |
| 04 | Audit, Micrometer metrics, OpenTelemetry traces, and dashboard | Reviewers can trace decision rationale and inspect evidence while running offline in demo-local. |

Phase 02 completes the offline deterministic baseline. Implementation beyond Phase 02 requires explicit approval.
