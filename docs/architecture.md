# Architecture

ARGUS is a modular monolith shipped as one Spring Boot application. The planned module boundaries are Java packages. Provider adapters depend on investigation contracts, and deterministic policies depend only on domain data and validated evidence.

| Component | Responsibility |
| --- | --- |
| Fraud Orchestrator | Run rules and fan out independent investigations concurrently. Gather candidate evidence and track unavailable results. |
| Deterministic Rules Engine | Evaluate versioned policies against synthetic transactions. |
| Graph Investigation Agent | Investigate relationships in a synthetic transaction graph. |
| Behavioral Investigation Agent | Investigate deviations from synthetic behavioral baselines. |
| Synthetic ML Predictor | Return a reproducible score and model version as evidence inputs. |
| Evidence Validation Layer | Verify types, fixture references, provenance, and consistency before accepting evidence. |
| Deterministic Consensus Engine | Apply versioned policy to validated evidence and produce the simulated decision and reasons. |
| Resilience Layer | Bound investigation latency and concurrency and account for partial failures. |
| Audit and Observability Layer | Correlate synthetic cases, timings, validation outcomes, policy versions, and decisions. |
| Fraud Investigation Dashboard | Present investigations, evidence, and decisions using HTML, CSS, and JavaScript. |

The planned flow starts with a synthetic transaction and deterministic rule evaluation. The orchestrator fans out investigations and gathers their responses within a deadline. Deterministic validation rejects unsupported evidence. The consensus engine consumes validated evidence, rule findings, and availability information. Agent tools read synthetic data with narrowly scoped access.

## Runtime profiles

- `demo-local` is the default. Phase 0 disables provider models and serves Actuator health on loopback. Later phases will use deterministic fixture responses.
- `demo-azure` enables OpenAI chat against the Microsoft Foundry deployment supplied through environment variables. Later phases will use ChatClient, Tool Calling, and Structured Output through this adapter.

## References

- [Spring AI compatibility and dependency management](https://docs.spring.io/spring-ai/reference/2.0/getting-started.html)
- [Spring AI migration notes](https://docs.spring.io/spring-ai/reference/2.0/upgrade-notes.html)
- [Microsoft Foundry](https://learn.microsoft.com/en-us/azure/foundry/)
- [Azure Architecture Center](https://learn.microsoft.com/en-us/azure/architecture/)
- [Resilience4j](https://resilience4j.readme.io/)
