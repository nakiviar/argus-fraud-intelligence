# Distributed tracing

The investigation starts under its submission HTTP request. Assessment and investigation are independent HTTP operations, so their traces are separate by default. If a caller deliberately supplies the same upstream W3C context, the server preserves that upstream relationship. ARGUS links the investigation observation to the sampled assessment span using OpenTelemetry Span.addLink. The link preserves the submission HTTP parent and connects the two traces. Its lookup retains the most recent 512 sampled assessment contexts. A missing or evicted context leaves the investigation trace independently inspectable.

```text
POST /api/v1/investigations
└─ argus.investigation.create
   └─ argus.investigation
      ├─ argus.agent [GRAPH, SIMULATED]
      │  └─ argus.evidence.validation
      ├─ argus.agent [BEHAVIORAL, SIMULATED]
      │  └─ argus.evidence.validation
      ├─ argus.evidence.aggregation
      └─ argus.investigation.completion
```

The root application observation starts after admission. It stays active through final report publication and may outlive the submission HTTP span. Java captures that exact observation on the submission thread and opens its scope explicitly on each worker, TimeLimiter callback, global timeout and fan-in callback. Each scope closes with try-with-resources. There is no assumption that thread-local context crosses an executor. Both agents create direct sibling observations. The same parent also joins evidence aggregation and completion. Agent child spans measure actual worker time. A late agent may end after its investigation span. Its `late` tag and late-result event identify that condition without reopening the report.

In `demo-azure`, the real ChatClient and advisor observations appear beneath the agent. The model call and allowed tools retain Spring AI's contextual span names. In `demo-local`, only application observations represent simulated work. The tool counter records an actual invocation of the synthetic callback.

## Conference Notes

- The investigation UUID identifies a business case in this process. A trace ID identifies runtime operations across threads or services.
- Use the scoped event endpoint to translate the investigation ID to an active trace ID.
- Waterfall overlap shows independent work. Scope restoration keeps concurrent investigations isolated even when worker threads are reused.
- An asynchronous timeout can finish the parent observation before a protected remote worker finishes. Span duration shows actual execution rather than an invented cancellation.

See [Observability](observability.md) and [Demo](observability-demo.md).

