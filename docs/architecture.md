# Architecture

ARGUS is a modular monolith shipped as one Spring Boot application. Java package boundaries keep the domain independent of Spring, Spring AI, Azure, and JSON libraries. Phase 01 defines the contracts and fixture cases for two execution paths.

## Hot Path

Assess a synthetic transaction synchronously. The deterministic Rules Engine, graph data provider, and Synthetic ML Predictor supply typed inputs to the Deterministic Risk Decision Engine. The engine emits APPROVE, REVIEW, or BLOCK with the policy version, signals, and explicit unavailable sources. Every input comes from deterministic code or source data. The hot path has zero dependency on an LLM or the Investigation Path.

## Investigation Path

A REVIEW opens an investigation with a snapshot of the transaction and its Hot Path decision. The orchestrator will run the Graph Investigation Agent and Behavioral Investigation Agent concurrently with bounded fan-out/fan-in. Tools will read narrowly scoped synthetic data and the application will record each tool execution. The evidence validator will resolve candidate citations against the recorded results for the current investigation and agent. The orchestrator will report each execution outcome, including timeouts and unavailable evidence.

The report supplies context to a human reviewer. The original decision remains available in the report. Agents have no financial operations or decision override capability. The dashboard will present the decision alongside the report for human review.

```mermaid
flowchart LR
    T[Synthetic transaction] --> H[Hot Path]
    H --> R[Rules, graph data, synthetic predictor]
    R --> D[Deterministic Risk Decision Engine]
    D --> O[APPROVE or BLOCK]
    D --> C[REVIEW investigation context]
    C --> F[Concurrent investigation orchestrator]
    F --> G[Graph agent]
    F --> B[Behavioral agent]
    G --> V[Evidence validation against tool records]
    B --> V
    V --> P[Investigation report]
    P --> U[Human review]
```

## Design decisions

| Decision | Reason |
| --- | --- |
| One deployable with explicit package dependencies | The demo can illustrate separation of concerns without a service network. |
| Synchronous decision followed by a separate investigation | Model latency and failure stay outside transaction assessment. |
| Application-owned outcome metadata | Investigations remain attributable when model output is invalid or unavailable. |
| Explicit availability in both paths | Policy and human reviewers can account for missing inputs without treating missing data as zero risk. |

The future resilience layer will bound latency and concurrency. Audit and observability will correlate decisions, investigation reports, and execution outcomes using Micrometer and OpenTelemetry. The existing `demo-local` profile starts offline and exposes health on loopback. The existing `demo-azure` profile supports external Foundry configuration. Implementations of the two paths and provider integration are future work.

See [Domain model](domain-model.md) for contract ownership and examples.
