# ARGUS architecture as built

```mermaid
flowchart LR
  Speaker --> Browser
  Browser -->|loopback HTTP| ARGUS
  ARGUS -->|optional inference| Foundry
  ARGUS -->|optional OTLP HTTP| Tempo
  Grafana --> Tempo
```

```mermaid
flowchart TD
  UI[Static dashboard] --> A[Assessment REST]
  UI --> I[Investigation REST]
  A --> P[Rules and synthetic providers]
  P --> D[RiskDecisionEngine]
  D --> R[In-memory assessment record]
  R -->|REVIEW| O[InvestigationOrchestrator]
  I --> O
  O --> G[Graph agent]
  O --> B[Behavioral agent]
  G --> S[Scoped tool and evidence validation]
  B --> S
  S --> F[EvidenceAggregator and report]
  F --> E[In-memory investigation snapshot]
```

| Package | Responsibility |
|---|---|
| domain | Immutable contracts and invariants |
| assessment, assessment.web | Fixtures, providers, deterministic policy, audit recorder and REST |
| investigation | Agent selection, execution, tools, validation, concurrency, resilience and API |
| ai | Foundry settings, SDK wiring and explicit connectivity checking |
| observability | Observations, correlation, bounded events and export privacy |
| resources/static | HTML, CSS, Fetch, SVG and safe text rendering |

## Hot Path

```mermaid
sequenceDiagram
 Browser->>AssessmentController: POST scenarioId
 AssessmentController->>RiskAssessmentService: assess
 RiskAssessmentService->>RiskDecisionEngine: rule, graph and predictor inputs
 RiskDecisionEngine-->>RiskAssessmentService: RiskDecision
 RiskAssessmentService->>AuditRecorder: store assessment
 AssessmentController-->>Browser: 201 and recommendation
```

## Investigation Path and concurrency

```mermaid
sequenceDiagram
 Browser->>InvestigationController: POST assessmentId
 InvestigationController->>Orchestrator: verify REVIEW and register
 InvestigationController-->>Browser: 202 and Location
 par bounded worker
 Orchestrator->>Graph: scoped investigation
 Graph-->>Orchestrator: validated result
 and bounded worker
 Orchestrator->>Behavioral: scoped investigation
 Behavioral-->>Orchestrator: validated result
 end
 Orchestrator->>Aggregator: terminal results and original decision
 Browser->>InvestigationController: GET snapshot and events
```

```mermaid
flowchart LR
 Admission --> Root[Investigation observation]
 Root --> G[Graph worker]
 Root --> B[Behavioral worker]
 Root --> T[Deadline scheduler]
 G --> F[Fan-in]
 B --> F
 T --> F
 F --> R[Final snapshot]
```

## Tool calling on the Azure path

```mermaid
sequenceDiagram
 Agent->>ChatClient: system, user, callback and context
 ChatClient->>ToolCallingAdvisor: execute loop
 ToolCallingAdvisor->>ModelRoundAdvisor: each model round
 ModelRoundAdvisor->>OpenAiChatModel: protected SDK request
 OpenAiChatModel-->>ToolCallingAdvisor: tool request
 ToolCallingAdvisor->>ScopedTool: arguments and ToolContext
 ScopedTool-->>ToolCallingAdvisor: bounded synthetic fact and reference
 ToolCallingAdvisor->>ModelRoundAdvisor: follow-up model round
 ToolCallingAdvisor-->>ChatClient: final model response
 ChatClient-->>Agent: converted ModelFinding
 Agent->>EvidenceValidator: validate against tool ledger
```

Execution order per model round is deadline and round checks, agent semaphore, shared semaphore, shared circuit breaker, SDK/transport call. The coordinator TimeLimiter observes a separate stage outside the protected call. Permit lifetime follows actual execution. Four workers, a queue of four and at most two admitted cases bound local execution. Default budgets are 35 s global, 25 s agent and 10 s HTTP. See [resilience](../resilience.md).

```mermaid
flowchart LR
 HTTP --> Observation[Micrometer Observation]
 Worker --> Observation
 Scheduler --> Observation
 Observation --> OTel[OpenTelemetry spans]
 OTel --> Privacy[Privacy exporter]
 Privacy -->|opt-in OTLP| Tempo
 Tempo --> Grafana
 Observation --> Events[Scoped operational event history]
```

The investigation observation is parented by submission and can link to a sampled assessment span. Explicit scopes propagate to both sibling workers and scheduled callbacks. Metrics keep bounded labels. The model can propose a callback invocation and a finding. Java owns scope, deadlines, identifiers, provenance, policy and report finalization. REST accepts references to synthetic fixtures or stored assessments; the dashboard renders strings as text. Model observations remain untrusted. The in-memory audit and operational telemetry have different purposes.

| Profile | Agent execution | Hot Path |
|---|---|---|
| demo-local | SimulatedAgentModel and scoped synthetic tools | Same deterministic policy |
| demo-azure | Spring AI and OpenAI SDK to configured Foundry deployment | Same deterministic policy |
| demo-observability | Optional overlay for OTLP | Same deterministic policy |

## Architecture comparison

| Concern | Implemented here | Simulated today | Possible Azure production component | Pending |
|---|---|---|---|---|
| Evidence | Read-only provider interfaces | Bundled graph and predictor fixtures | Approved graph storage such as Cosmos DB; Azure ML inference if justified | Adapters, data governance and validation |
| Async work | In-process bounded workers | Synthetic failure cases | Azure Service Bus for durable work if needed | Queue and idempotent consumers |
| Persistence | In-memory assessment and investigation stores | Synthetic records | Governed durable storage | Retention and access model |
| Model | Foundry adapter, Entra bearer handling and local HTTP contract tests | Local finding generation | Authorized Foundry deployment | Live inference and latency |
| Hosting | Spring Boot process | Local loopback demo | Azure Container Apps if suitable | Deployment and identity automation |
| Telemetry | Micrometer, OTel and local OTLP pipeline | Simulated workloads | Azure Monitor as a separate validated design | Grafana UI and Azure Monitor verification |

