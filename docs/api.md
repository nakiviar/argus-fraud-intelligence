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

