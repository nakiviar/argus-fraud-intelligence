# Local observability verification

The existing LGTM container 2926b60b87f9 combines an OpenTelemetry collector, Tempo, Grafana, Loki and Prometheus. Use `./scripts/start-argus-demo.ps1 -Mode Simulated -Observability` or select Ollama. The collector accepts OTLP/HTTP at http://127.0.0.1:4318/v1/traces. Grafana listens on http://127.0.0.1:3000 and its Tempo datasource UID is tempo. Existing Docker port bindings listen on host interfaces, so use this infrastructure only in a trusted local network. The alternative Compose configuration is unchanged.

Create a REVIEW investigation, copy a sampled trace ID from its event endpoint or the dashboard, then query by Trace ID in Grafana Explore with the Tempo datasource. The JSON equivalent is GET /api/datasources/proxy/uid/tempo/api/traces/{traceId} through Grafana. The app exports through its existing privacy filter and keeps assessment records separate from telemetry. Look for sibling agent spans, validation, aggregation and completion. Real model and callback spans appear only when those steps actually execute.

[Grafana LGTM](https://github.com/grafana/docker-otel-lgtm)


## Observed runs

The simulated trace was b3ea14cf9fcc8b3d94ed72e6fb876feb. The real Ollama trace was b20607393a86648efaab4a186dcfa08c. Both were retrieved via Grafana's Tempo proxy and rendered in Explore. The real trace contains sibling argus.agent spans, spring_ai chat_client, four chat llama3.1:latest spans, execute_tool read_graph_relationship, execute_tool read_behavioral_summary, validation, aggregation and completion. The case completed in 53.764 s, retained two references and preserved REVIEW. The first two HTTP model spans overlapped; that overlap includes waiting and does not measure simultaneous token generation inside the runner.

- [Simulation trace](conference/assets/grafana/simulated-trace.json)
- [Ollama trace](conference/assets/grafana/ollama-first-trace.json)
- [Grafana Explore capture](conference/assets/grafana/ollama-explore.png)

The final packaged dashboard was verified in Chrome with a completed Ollama investigation. See the [dashboard capture](conference/assets/ollama/dashboard-real.png). The first Grafana capture corresponds to the first trace listed above.
