# Demo runbook

## Verified local path

From PowerShell in the repository root, select the installed Java 21 JDK and cached Maven repository. Adjust the JDK and cache paths for another machine.

```powershell
$env:JAVA_HOME = "$env:USERPROFILE/.jdks/ms-21.0.8"
$env:Path = "$env:JAVA_HOME/bin;$env:Path"
java -version
mvn -version
mvn -o -B -ntp "-Dmaven.repo.local=$PWD/.local/maven-cache" clean verify
$jar = (Resolve-Path target/argus-fraud-intelligence-0.1.0-SNAPSHOT.jar).Path
java -jar $jar --spring.profiles.active=demo-local
```

Open http://127.0.0.1:8080/. Diagnostics run in a separate shell with the same JDK and jar variable.

```powershell
java -jar $jar --spring.profiles.active=demo-local --check-model-connectivity
java -jar $jar --spring.profiles.active=demo-local --check-investigation-agent=graph --scenario=additional-review
java -jar $jar --spring.profiles.active=demo-local --check-investigation-agent=behavioral --scenario=additional-review
```

Create a case in the dashboard or use these requests.

```powershell
$base = 'http://127.0.0.1:8080'
$assessment = Invoke-RestMethod "$base/api/v1/assessments" -Method Post -ContentType application/json -Body '{"scenarioId":"additional-review"}'
$body = @{ assessmentId = $assessment.assessmentId } | ConvertTo-Json -Compress
$case = Invoke-RestMethod "$base/api/v1/investigations" -Method Post -ContentType application/json -Body $body
$caseUrl = "$base/api/v1/investigations/$($case.investigationId)"
Invoke-RestMethod $caseUrl
Invoke-RestMethod "$caseUrl/events"
```

Repeat GET until terminal. For a timeout, use a second process and choose Additional review at http://127.0.0.1:8081/.

```powershell
java -jar $jar --server.port=8081 --spring.profiles.active=demo-local --argus.investigation.deadline=PT5S --argus.investigation.agent-timeout=PT2S --argus.investigation.http-request-timeout=PT1S --argus.investigation.simulated-delay-agent=GRAPH --argus.investigation.simulated-delay=PT4S
```

For port conflicts, identify the listener and choose an unused port. When switching to offline mode, launch a clean process explicitly using demo-local.

```powershell
Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue
Remove-Item Env:AZURE_OPENAI_ACCESS_TOKEN -ErrorAction SilentlyContinue
java -jar $jar --server.port=8082 --spring.profiles.active=demo-local
```

For correlation without external export, add `--management.tracing.sampling.probability=1.0` in demo-local. Inspect the Trace ID in Observability or event JSON. This behavior was verified locally. The [captured span example](assets/span-example.json) also shows parent IDs.

## Conditional Foundry path

Use a separate PowerShell window. Configure the authorized resource endpoint and deployment. The following requires Azure CLI authentication, inference permissions and explicit authorization for live requests. No live command was executed during Phase 09. Avoid console transcripts and never print the token.

```powershell
$env:AZURE_OPENAI_ENDPOINT = 'https://RESOURCE.openai.azure.com'
$env:AZURE_OPENAI_DEPLOYMENT = 'CHAT-DEPLOYMENT'
$env:AZURE_OPENAI_ACCESS_TOKEN = az account get-access-token --resource https://cognitiveservices.azure.com --query accessToken -o tsv --only-show-errors
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($env:AZURE_OPENAI_ACCESS_TOKEN)) {
    Remove-Item Env:AZURE_OPENAI_ACCESS_TOKEN -ErrorAction SilentlyContinue
    throw 'Unable to obtain an authorized access token'
}
try {
    java -jar $jar --spring.profiles.active=demo-azure --check-model-connectivity
    if ($LASTEXITCODE -ne 0) { throw 'Live diagnostic failed; use Plan B' }
    java -jar $jar --spring.profiles.active=demo-azure
} finally {
    Remove-Item Env:AZURE_OPENAI_ACCESS_TOKEN -ErrorAction SilentlyContinue
}
```

Startup alone performs no inference. An investigation submits real requests. If configuration fails, check the resource hostname and deployment. For authentication errors, check expiry, audience and authorized permissions and relaunch with a fresh token. See [Azure troubleshooting](../azure-integration.md).

## Conditional Grafana and Tempo

Compose configuration is distinct from engine and UI validation. A running Docker engine and pre-cached images are required for offline startup.

```powershell
docker compose -f observability/compose.yaml config
docker compose -f observability/compose.yaml up -d --pull never
java -jar $jar --spring.profiles.active=demo-local,demo-observability --argus.dashboard.grafana-url=http://127.0.0.1:3000
```

Run a REVIEW investigation and copy its trace ID. Open Grafana Explore at http://127.0.0.1:3000/, select Tempo and query by Trace ID. Use the bundled [trace assets](assets/README.md) if the engine or UI is unavailable.

