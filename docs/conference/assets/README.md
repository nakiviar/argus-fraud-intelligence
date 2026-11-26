# Conference assets

These screenshots were captured in Chrome against the packaged demo-local application. Synthetic IDs and timestamps are visible. The application was running without Grafana or remote model inference.

| Asset | What to show |
|---|---|
| [Architecture, 1920×1080](argus-overview-1920x1080.png) | Deterministic Hot Path and separate investigation, after a successful review case. |
| [Successful investigation](successful-investigation.png) | COMPLETED with the original REVIEW and both agents successful. |
| [Partial investigation, 1366×768](argus-investigation-1366x768.png) | Invalid Graph response and successful Behavioral result. |
| [Execution waterfall](execution-waterfall.png) | SVG built from reported execution and lifecycle timestamps. |
| [Evidence Explorer](evidence-explorer.png) | Observations alongside verified source references. |
| [Observability](observability.png) | Scoped events and local trace correlation. |
| [Investigation events](investigation-events.json) | Sanitized API event history from an actual local run with tracing sampled and export disabled. |
| [Span example](span-example.json) | Actual spans from the in-memory exporter test. Compare trace and parent span IDs for the sibling agents. |

The events and spans were captured in separate runs and need not share identifiers. The spans are evidence of local tracing; Grafana visualization and Foundry inference remain pending.

