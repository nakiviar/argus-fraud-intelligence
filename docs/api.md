# Local assessment API

Start the application as described in the [README](../README.md). The default `demo-local` profile binds to `127.0.0.1:8080` and works with the packaged application offline.

| Method and path | Success | Purpose |
| --- | --- | --- |
| GET `/api/v1/scenarios` | 200 | List the allowed scenario IDs and synthetic transaction IDs. |
| POST `/api/v1/assessments` | 201 | Evaluate a scenario and return its assessment with a `Location` header. |
| GET `/api/v1/assessments/{assessmentId}` | 200 | Retrieve the exact stored snapshot using the UUID from POST. |
| GET `/actuator/health` | 200 | Check local application health. |

## Requests

```powershell
$base = 'http://127.0.0.1:8080'
Invoke-RestMethod "$base/api/v1/scenarios"
$assessment = Invoke-RestMethod "$base/api/v1/assessments" -Method Post -ContentType 'application/json' -Body '{"scenarioId":"unavailable-evidence"}'
Invoke-RestMethod "$base/api/v1/assessments/$($assessment.assessmentId)"
Invoke-RestMethod "$base/actuator/health"
```

The POST takes a JSON object containing only `scenarioId`. The list endpoint gives the accepted values. Here is an example response with an illustrative UUID and timestamp.

```json
{
  "assessmentId": "00000000-0000-4000-8000-000000000001",
  "transactionId": "SYN-TXN-004",
  "decision": "REVIEW",
  "policyId": "synthetic-risk",
  "policyVersion": "synthetic-v1",
  "verifiedEvidenceReferences": [
    {"source": "RULES", "recordId": "synthetic-fixtures-v1/unavailable-evidence/rules"},
    {"source": "SYNTHETIC_ML", "recordId": "synthetic-fixtures-v1/unavailable-evidence/prediction"}
  ],
  "reasonCodes": ["GRAPH_DATA_UNAVAILABLE"],
  "evidenceAvailability": {
    "RULES": "AVAILABLE",
    "GRAPH_DATA": "UNAVAILABLE",
    "SYNTHETIC_ML": "AVAILABLE"
  },
  "assessedAt": "2026-11-26T14:00:01Z",
  "investigationRequired": true
}
```

`investigationRequired` indicates a REVIEW recommendation. Assessment IDs are valid for the lifetime of the current process.

## Errors

The API returns `application/problem+json` for request errors. A malformed body, additional input fields, an invalid scenario ID format, or a malformed assessment UUID results in 400. A well-formed unknown scenario ID or unknown assessment UUID results in 404. An unsupported request media type results in 415.

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "Synthetic scenario not found.",
  "instance": "/api/v1/assessments"
}
```


## Investigation telemetry

GET `/api/v1/investigations/{investigationId}/events` returns 200 with the retained sanitized lifecycle and resilience events for the requested case. Invalid UUIDs return 400 and unknown investigations return 404. A known case can return an empty array after eviction. Trace and span IDs can be null. Trace availability in an exporter depends on sampling, exporter configuration and retention.

```powershell
$events = Invoke-RestMethod "$base/api/v1/investigations/$($investigation.investigationId)/events"
$events | Select-Object eventType,agent,executionStatus,traceId,spanId
```

The endpoint stores at most 512 recent events across the process. It serves local diagnostics and has no streaming behavior. See [Observability](observability.md).

## Asynchronous investigations

| Method and path | Success | Purpose |
| --- | --- | --- |
| POST `/api/v1/investigations` | 202 or 200 | Start one investigation per stored REVIEW assessment, or return its existing snapshot. |
| GET `/api/v1/investigations/{investigationId}` | 200 | Retrieve state, events, agent results, verified references, and the final report. |

The POST accepts a JSON object containing only `assessmentId`, a UUID returned by the assessment API. First submission returns 202 with a PENDING snapshot and a relative Location header. A repeated request returns the same location with 202 while active or 200 once terminal.

```powershell
$base = 'http://127.0.0.1:8080'
$assessment = Invoke-RestMethod "$base/api/v1/assessments" -Method Post -ContentType 'application/json' -Body '{"scenarioId":"additional-review"}'
$body = @{ assessmentId = $assessment.assessmentId } | ConvertTo-Json -Compress
$submission = Invoke-WebRequest "$base/api/v1/investigations" -Method Post -ContentType 'application/json' -Body $body
$investigation = Invoke-RestMethod ($base + [string]$submission.Headers.Location)
$investigation | ConvertTo-Json -Depth 12
```

If the status is PENDING, RUNNING, or AGGREGATING, repeat the GET until the case reaches a terminal state. Example initial response, with selected fields:

```json
{
  "investigationId": "00000000-0000-4000-8000-000000000010",
  "assessmentId": "00000000-0000-4000-8000-000000000011",
  "mode": "SIMULATED",
  "status": "PENDING",
  "completedAt": null,
  "agentResults": [],
  "verifiedEvidenceReferences": [],
  "report": null
}
```

Example final result for `unavailable-evidence`, with selected fields:

```json
{
  "mode": "SIMULATED",
  "status": "PARTIAL",
  "originalRiskDecision": {
    "transactionId": "SYN-TXN-004",
    "decision": "REVIEW",
    "policyVersion": "synthetic-v1"
  },
  "agentResults": [
    {"agent": "GRAPH", "status": "UNAVAILABLE"},
    {"agent": "BEHAVIORAL", "status": "SUCCESS"}
  ],
  "verifiedEvidenceReferences": [
    {
      "toolExecutionId": "00000000-0000-4000-8000-000000000012",
      "recordId": "synthetic-fixtures-v1/unavailable-evidence/prediction",
      "type": "BEHAVIORAL"
    }
  ]
}
```

Request errors use `application/problem+json`. The API returns 400 for invalid input, 404 for unknown IDs, 409 for APPROVE or BLOCK assessments, 429 when both investigation slots are occupied, and 503 when processing is shutting down. The explicit investigation POST can trigger model calls in `demo-azure`. The assessment API and health endpoint remain offline-capable and do not initiate investigations.

Agent deadlines, provider timeouts and containment failures appear in the existing per-agent execution results. A timed-out slot uses TIMEOUT; an open circuit or model bulkhead rejection yields UNAVAILABLE. Report status remains COMPLETED, PARTIAL, or FAILED. The 429 response refers to investigation admission. See [Investigation lifecycle](investigation-lifecycle.md) for status and retention and [Resilience](resilience.md) for the timeout hierarchy.
