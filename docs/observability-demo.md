# Local observability demo

Use Java 21 and Maven 3.9.9. Docker Compose runs Tempo **2.9.0** and Grafana **12.4.2**. The Compose configuration provisions the Tempo data source automatically. Grafana is on `127.0.0.1:3000` and OTLP/HTTP on `127.0.0.1:4318`. Tempo queries stay on the container network. Tempo retains blocks for one hour in temporary storage. Grafana uses local anonymous Explore access and disables analytics and update checks.

```powershell
docker compose -f observability/compose.yaml config
docker compose -f observability/compose.yaml pull
docker compose -f observability/compose.yaml up -d --pull never
mvn clean verify
$jar = 'target/argus-fraud-intelligence-0.1.0-SNAPSHOT.jar'
java -jar $jar --spring.profiles.active=demo-local,demo-observability
```

The initial image and Maven dependency downloads need network access. After caching them, use `mvn -o clean verify` and the same Compose startup with `--pull never`. Stop the containers with `docker compose -f observability/compose.yaml down`. ARGUS's ordinary local profile runs with the services stopped.

## Configuration

| Property | Ordinary local | `demo-observability` |
| --- | --- | --- |
| `management.tracing.sampling.probability` | 0.0 | 1.0 |
| `management.tracing.export.enabled` | false | true, enabling Boot trace propagation |
| `management.tracing.export.otlp.enabled` | inherits disabled export | true |
| `management.opentelemetry.tracing.export.otlp.endpoint` | absent | `http://127.0.0.1:4318/v1/traces` |
| `management.opentelemetry.tracing.export.otlp.transport` | default | http |
| `management.opentelemetry.tracing.export.otlp.connect-timeout` and `timeout` | default | 1s and 2s |
| `management.otlp.metrics.export.enabled`, `management.logging.export.enabled` | false | false |
| management.endpoints.web.exposure.include | health | health,metrics |
| management.tracing.baggage.enabled | false | false |

`ARGUS_OTLP_TRACES_ENDPOINT` can select a different authorized OTLP HTTP receiver. The same activation works with `demo-azure,demo-observability`. Configure Foundry as documented and use only authorized explicit investigation submissions.

## Inspect the five scenarios

In a separate PowerShell window, create a deterministic assessment with a caller-selected trace ID for easy lookup.

```powershell
$base = 'http://127.0.0.1:8080'
$assessmentTrace = [guid]::NewGuid().ToString('N')
$upstream = [guid]::NewGuid().ToString('N').Substring(0,16)
Invoke-RestMethod "$base/api/v1/assessments" -Method Post -ContentType 'application/json' -Headers @{ traceparent = "00-$assessmentTrace-$upstream-01" } -Body '{"scenarioId":"normal"}'
$assessmentTrace

function Invoke-ArgusTrace($scenario) {
    $assessment = Invoke-RestMethod "$base/api/v1/assessments" -Method Post -ContentType 'application/json' -Body (@{scenarioId=$scenario} | ConvertTo-Json -Compress)
    $request = Invoke-WebRequest "$base/api/v1/investigations" -Method Post -ContentType 'application/json' -Body (@{assessmentId=$assessment.assessmentId} | ConvertTo-Json -Compress)
    $location = $base + [string]$request.Headers.Location
    do {
        $report = Invoke-RestMethod $location
        if ($report.status -notin 'COMPLETED','PARTIAL','FAILED') { Start-Sleep -Milliseconds 100 }
    } until ($report.status -in 'COMPLETED','PARTIAL','FAILED')
    $events = Invoke-RestMethod "$location/events"
    [pscustomobject]@{ Scenario=$scenario; InvestigationId=$report.investigationId; Status=$report.status; TraceId=$events[0].traceId }
}

Invoke-ArgusTrace 'additional-review'
Invoke-ArgusTrace 'invalid-agent-response'
```

For the timeout and worker-failure scenarios, restart the local JAR separately using the respective command and call `Invoke-ArgusTrace 'additional-review'` again.

```powershell
java -jar $jar --spring.profiles.active=demo-local,demo-observability --argus.investigation.deadline=PT5S --argus.investigation.agent-timeout=PT2S --argus.investigation.http-request-timeout=PT1S --argus.investigation.simulated-delay-agent=GRAPH --argus.investigation.simulated-delay=PT4S

java -jar $jar --spring.profiles.active=demo-local,demo-observability --argus.investigation.simulated-failure=GRAPH
```

In Grafana Explore, select **Tempo**, choose the **Trace ID** query type, paste the output ID and run the query. Allow a few seconds for the batch exporter. Inspect the two sibling agents, the on-time Behavioral result and the timeout or fallback events in the scoped event endpoint. The deterministic assessment trace contains no model calls. Metrics can be inspected locally, for example `GET /actuator/metrics/argus.resilience.events?tag=event:AGENT_TIMEOUT`.

## Diagnostic without Docker

The automated tests use a real Boot OpenTelemetry pipeline with an in-memory span exporter and a loopback OTLP/HTTP receiver. Export actual span IDs, parent IDs and nanosecond timestamps as a local JSON example and capture the OTLP protobuf payload by running:

```powershell
mvn -o "-Dtest=ObservabilityTests#httpInvestigationHasSiblingAgentSpansAndExportableTraceWithoutModelCalls,OtlpExportTests" "-Dargus.observability.write-example=true" test
Get-Content target/phase07-example-trace.json
Get-Item target/phase07-example-investigation.otlp.pb
```

These files reflect actual simulated execution. They require no container runtime or Azure access. Compose syntax can be validated without a Docker engine. Starting Tempo and inspecting Grafana require a working engine.

## References

- [Tempo Docker example](https://grafana.com/docs/tempo/latest/docker-example/)
- [Grafana Tempo data source](https://grafana.com/docs/grafana/latest/datasources/tempo/)
- [Tempo 2.9.0](https://github.com/grafana/tempo/releases/tag/v2.9.0)
- [Grafana 12.4.2](https://github.com/grafana/grafana/releases/tag/v12.4.2)

