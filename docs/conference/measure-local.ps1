New-Item -ItemType Directory -Force .local | Out-Null
$ErrorActionPreference='Stop'
$java=(Get-Command java -CommandType Application | Select-Object -First 1).Source
$jar=(Resolve-Path target/argus-fraud-intelligence-0.1.0-SNAPSHOT.jar).Path
$base='http://127.0.0.1:19090'
$opts=@('-jar', ('"' + $jar + '"'),'--server.port=19090','--spring.profiles.active=demo-local','--management.tracing.sampling.probability=1.0')
function Start-App($extra) {
  $p=Start-Process $java -WindowStyle Hidden -ArgumentList ($opts + $extra) -RedirectStandardOutput .local/phase09-app.log -RedirectStandardError .local/phase09-app.err -PassThru
  for($i=0;$i -lt 40;$i++) {
    try { if ((Invoke-RestMethod "$base/actuator/health" -TimeoutSec 1).status -eq 'UP') { return $p } } catch {}
    Start-Sleep -Milliseconds 500
  }
  Stop-Process -Id $p.Id -ErrorAction SilentlyContinue
  throw 'App failed to start'
}
function Post($path,$data) {
  Invoke-RestMethod "$base/api/v1/$path" -Method Post -ContentType application/json -Body ($data | ConvertTo-Json -Compress)
}
function Assess($id) { Post 'assessments' @{scenarioId=$id} }
function Case($scenario) {
  $a=Assess $scenario
  $started=[Diagnostics.Stopwatch]::StartNew()
  $s=Post 'investigations' @{assessmentId=$a.assessmentId}
  $url="$base/api/v1/investigations/$($s.investigationId)"
  do {
    $s=Invoke-RestMethod $url
    if ($s.status -in 'COMPLETED','PARTIAL','FAILED') { break }
    Start-Sleep -Milliseconds 10
    if($started.Elapsed.TotalSeconds -gt 12){throw 'Case did not finish'}
  } while($true)
  if ($s.originalRiskDecision.decision -ne 'REVIEW') { throw 'Original decision changed' }
  [pscustomobject]@{
    scenario=$scenario;status=$s.status;refs=$s.verifiedEvidenceReferences.Count
    serverMs=([DateTimeOffset]$s.completedAt-[DateTimeOffset]$s.createdAt).TotalMilliseconds
    clientMs=$started.Elapsed.TotalMilliseconds
    agents=@($s.agentResults | ForEach-Object { @{type=$_.agent;status=$_.status;ms=([DateTimeOffset]$_.finishedAt-[DateTimeOffset]$_.startedAt).TotalMilliseconds} })
    eventsUrl="$url/events"
  }
}
$app=Start-App @()
try {
  1..10 | ForEach-Object { $null=Assess normal; $null=Case additional-review }
  $hot=@(1..60 | ForEach-Object { $watch=[Diagnostics.Stopwatch]::StartNew(); $a=Assess normal; if($a.decision -ne 'APPROVE'){throw 'Decision regression'}; $watch.Elapsed.TotalMilliseconds })
  $cases=@(1..30 | ForEach-Object { Case additional-review })
  $partial=@(1..10 | ForEach-Object { Case invalid-agent-response })
  $trace=Invoke-RestMethod $cases[-1].eventsUrl
  if(!($trace | Where-Object traceId)){throw 'Trace correlation unavailable'}

} finally {Stop-Process -Id $app.Id}
$app=Start-App @('--argus.investigation.deadline=PT5S','--argus.investigation.agent-timeout=PT2S','--argus.investigation.http-request-timeout=PT1S','--argus.investigation.simulated-delay-agent=GRAPH','--argus.investigation.simulated-delay=PT4S')
try { $null=Case additional-review; $timeout=@(1..5 | ForEach-Object { Case additional-review }) }
finally {Stop-Process -Id $app.Id}
@{hot=$hot;cases=$cases;partial=$partial;timeout=$timeout} | ConvertTo-Json -Depth 10 | Set-Content -Encoding utf8 .local/phase09-bench.json
