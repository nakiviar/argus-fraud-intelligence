param(
    [ValidateSet('Simulated','Ollama','Azure')][string]$Mode = 'Simulated',
    [switch]$Observability,
    [ValidateRange(1,65535)][int]$Port = 8080
)
$ErrorActionPreference = 'Stop'
$java = (Get-Command java -CommandType Application | Select-Object -First 1).Source
if ((& $java --version | Select-Object -First 1) -notmatch '\b21(?:\.|\s|$)') { throw 'Select a Java 21 JDK on PATH.' }
$jar = Join-Path $PSScriptRoot '../target/argus-fraud-intelligence-0.1.0-SNAPSHOT.jar'
if (!(Test-Path -LiteralPath $jar -PathType Leaf)) { throw 'Build the ARGUS JAR with Maven first.' }
if (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue) { throw "Port $Port is in use." }
$profile = switch ($Mode) {
    'Simulated' { 'demo-local' }
    'Ollama' {
        $endpoint = if ($env:OLLAMA_ENDPOINT) { $env:OLLAMA_ENDPOINT } else { 'http://127.0.0.1:11434' }
        if ($endpoint -notmatch '^http://127\.0\.0\.1:[0-9]{1,5}/?$') { throw 'OLLAMA_ENDPOINT must be a loopback HTTP origin.' }
        $model = if ($env:OLLAMA_MODEL) { $env:OLLAMA_MODEL } else { 'llama3.1:latest' }
        $installed = Invoke-RestMethod ($endpoint.TrimEnd('/') + '/api/tags') -TimeoutSec 5
        if ($model -notin $installed.models.name -and "${model}:latest" -notin $installed.models.name) {
            throw 'The selected Ollama model was not found in the local model inventory.'
        }
        'demo-ollama'
    }
    'Azure' {
        if (!$env:AZURE_OPENAI_ENDPOINT -or !$env:AZURE_OPENAI_DEPLOYMENT -or !$env:AZURE_OPENAI_ACCESS_TOKEN) {
            throw 'Set the authorized Foundry endpoint, deployment and Entra token in this shell.'
        }
        'demo-azure'
    }
}
$options = @("--server.port=$Port")
if ($Observability) {
    $id = '2926b60b87f9'
    $state = docker inspect --format '{{.State.Running}}' $id
    if ($LASTEXITCODE -ne 0) { throw 'The existing LGTM container or Docker engine is unavailable.' }
    if ($Port -in 3000,4317,4318) { throw 'Select an ARGUS port distinct from the LGTM ports.' }
    if ($state -eq 'false') {
        if (Get-NetTCPConnection -State Listen -LocalPort 3000,4317,4318 -ErrorAction SilentlyContinue) {
            throw 'An LGTM port is in use.'
        }
        docker start $id | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Could not start the existing LGTM container.' }
    }
    if (!(docker port $id 4318/tcp)) { throw 'The LGTM container must publish OTLP/HTTP port 4318.' }
    $profile += ',demo-observability'
    $options += '--argus.dashboard.grafana-url=http://127.0.0.1:3000'
    $options += '--management.opentelemetry.tracing.export.otlp.endpoint=http://127.0.0.1:4318/v1/traces'
    Write-Host 'Grafana http://127.0.0.1:3000'
}
Write-Host "ARGUS http://127.0.0.1:$Port ($Mode)"
& $java -jar $jar "--spring.profiles.active=$profile" @options
exit $LASTEXITCODE

