# Offline failure scenarios

Use Java 21 and Maven 3.9.9. After dependencies are cached, run `mvn -o clean verify` and start the packaged application in `demo-local`. The local simulations and scripted tests require no Azure credentials or external services.

```powershell
$jar = 'target/argus-fraud-intelligence-0.1.0-SNAPSHOT.jar'
java -jar $jar --spring.profiles.active=demo-local
```

In another PowerShell window use this function to create a synthetic assessment and wait for its investigation.

```powershell
function Invoke-ArgusCase($scenario) {
    $base = 'http://127.0.0.1:8080'
    $assessmentBody = @{ scenarioId = $scenario } | ConvertTo-Json -Compress
    $assessment = Invoke-RestMethod "$base/api/v1/assessments" -Method Post -ContentType 'application/json' -Body $assessmentBody
    $body = @{ assessmentId = $assessment.assessmentId } | ConvertTo-Json -Compress
    $request = Invoke-WebRequest "$base/api/v1/investigations" -Method Post -ContentType 'application/json' -Body $body
    do {
        $result = Invoke-RestMethod ($base + [string]$request.Headers.Location)
        if ($result.status -notin 'COMPLETED','PARTIAL','FAILED') { Start-Sleep -Milliseconds 100 }
    } until ($result.status -in 'COMPLETED','PARTIAL','FAILED')
    $result | Select-Object mode,status,agentResults,verifiedEvidenceReferences | ConvertTo-Json -Depth 10
}

Invoke-ArgusCase 'additional-review'
Invoke-ArgusCase 'invalid-agent-response'
Invoke-ArgusCase 'unavailable-evidence'
```

| Scenario | Expected status | Graph | Behavioral |
| --- | --- | --- | --- |
| additional-review | COMPLETED | SUCCESS | SUCCESS |
| invalid-agent-response | PARTIAL | INVALID_RESPONSE | SUCCESS |
| unavailable-evidence | PARTIAL | UNAVAILABLE | SUCCESS |
| additional-review with graph failure control | PARTIAL | ERROR | SUCCESS |
| additional-review with graph delay control | PARTIAL | TIMEOUT | SUCCESS |

For a controlled error, restart the local JAR and run `Invoke-ArgusCase 'additional-review'` with this launch command.

```powershell
java -jar $jar --spring.profiles.active=demo-local --argus.investigation.simulated-failure=GRAPH
```

For a controlled timeout, restart the local JAR with a short agent budget, a larger global budget, and a synthetic delay. Run the same case.

```powershell
java -jar $jar --spring.profiles.active=demo-local --argus.investigation.deadline=PT5S --argus.investigation.agent-timeout=PT2S --argus.investigation.http-request-timeout=PT1S --argus.investigation.simulated-delay-agent=GRAPH --argus.investigation.simulated-delay=PT4S
```

The synthetic delay and failure controls apply only to the offline profile. The following scripted demonstrations exercise circuit opening and recovery, saturation and retained permits, late response containment, and the global deadline through local tests.

```powershell
mvn -o "-Dtest=ResilienceTests#sharedCircuitOpensAndRecoversUsingAControlledClock" test
mvn -o "-Dtest=ResilienceTests#timeLimiterPublishesPartialReportsWhileRealWorkStillHoldsBothBulkheads" test
mvn -o "-Dtest=ResilienceTests#globalDeadlinePublishesPartialReportWithSanitizedEvent" test
mvn -o "-Dtest=ResilienceTests#actualChatClientLoopDoesNotRunToolAfterModelConsumesDeadline" test
```

These checks use synthetic failures or a scripted ChatModel. Live Azure invocation requires a separate, explicit authorized request.

