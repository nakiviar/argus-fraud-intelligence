# Investigation Dashboard

Start the packaged Spring Boot app in demo-local and open http://127.0.0.1:8080/. One JAR serves the HTML, CSS, JavaScript modules and APIs. No build step or third-party browser assets are required.

| View | Data and behavior |
|---|---|
| Overview | SVG separates the deterministic assessment from the REVIEW investigation. Node states use the assessment, lifecycle events and completed agent results. |
| Transaction Assessment | Scenario catalog and synthetic fixture details, assessment reasons, policy, availability and verified references. |
| Multi-Agent Investigation | REVIEW-only submission, existing investigation lookup, original decision, execution outcomes and measured SVG waterfall. |
| Evidence Explorer | Simulated or model-generated observations alongside validated source record and tool-execution identifiers. |
| Resilience Lab | Predefined cases, active local startup controls, retained evidence and sanitized fallback events. |
| Observability | Scoped event history, trace ID and copy, optional Grafana link. |

The browser sends only synthetic scenario IDs and existing assessment or investigation IDs. Requests go to the same origin and no credentials are persisted by the UI. Observations and API values are rendered as text. The server validates eligibility and deduplicates by assessment. The browser serializes polling, aborts obsolete requests when the selection changes and retries transient retrieval errors. A terminal snapshot ends regular polling. Refresh events retrieves current history, including events published later.

Agent time bars use backend start and finish timestamps. Lifecycle operations without an interval appear as markers. The relative axis uses the original timestamp precision and can extend when a late event is retrieved. Inspect evidence and event tables to see exact timestamps and provenance.

The runtime endpoint obtains SIMULATED or AZURE from the active agent model. Live Foundry verification is pending, and this process does not store results from the separate connectivity diagnostic. A configured Grafana link or an OTLP setting indicates configuration. The event contract can contain a trace ID even without a reachable trace viewer. When absent, the UI reports it as unavailable.

Per-call tool history, rejected candidate payloads and provider token counts are outside the public investigation contract and appear as unavailable. Verified references still include authoritative tool execution IDs. The local startup delay and failure settings apply to the running demo-local process. Investigation history is volatile and is lost on restart.

See [dashboard demo](dashboard-demo.md) for startup commands and [API](api.md) for routes.
