# Dashboard Demo

Build and run from the project directory in PowerShell using Java 21.

```powershell
mvn clean verify
java -jar target/argus-fraud-intelligence-0.1.0-SNAPSHOT.jar --spring.profiles.active=demo-local
```

Open http://127.0.0.1:8080/. After Maven dependencies are cached, use `mvn -o clean verify` with the same Maven repository previously populated. Docker and credentials are optional.

| Scenario | Assessment | Default investigation |
|---|---|---|
| Normal transaction | APPROVE | Ineligible |
| Mandatory policy violation | BLOCK | Ineligible |
| Additional review | REVIEW | COMPLETED |
| Invalid agent response | REVIEW | PARTIAL with a rejected agent result |
| Unavailable evidence | REVIEW | PARTIAL with explicitly unavailable graph evidence |

Start a second local process on port 8081 for a controlled timeout and choose Additional review there.

```powershell
java -jar target/argus-fraud-intelligence-0.1.0-SNAPSHOT.jar --server.port=8081 --spring.profiles.active=demo-local --argus.investigation.deadline=PT5S --argus.investigation.agent-timeout=PT2S --argus.investigation.http-request-timeout=PT1S --argus.investigation.simulated-delay-agent=GRAPH --argus.investigation.simulated-delay=PT4S
```

For a controlled failure, launch this command on port 8082 and select Additional review.

```powershell
java -jar target/argus-fraud-intelligence-0.1.0-SNAPSHOT.jar --server.port=8082 --spring.profiles.active=demo-local --argus.investigation.simulated-failure=GRAPH
```

For local trace correlation, follow [the observability runbook](observability-demo.md) and start the app with both profiles plus an optional Grafana base URL.

```powershell
java -jar target/argus-fraud-intelligence-0.1.0-SNAPSHOT.jar '--spring.profiles.active=demo-local,demo-observability' --argus.dashboard.grafana-url=http://127.0.0.1:3000
```

Copy the trace ID from Observability and paste it into Grafana Explore with the Tempo data source. Grafana visualization and live Foundry verification remain pending.

## 13-minute conference sequence

| Minute | Demonstration |
|---|---|
| 0–2 | Open Overview. Explain the two paths and the SIMULATED runtime badge. |
| 2–4 | Run Normal transaction and Mandatory policy violation. Compare APPROVE and BLOCK, reason codes and disabled investigation controls. |
| 4–7 | Run Additional review. Open the investigation and show measured fan-out in the waterfall. Check the original REVIEW. |
| 7–10 | Run Invalid agent response and Unavailable evidence. Show accepted evidence alongside the rejected or unavailable agent outcome. |
| 10–12 | Open the second process and run the timeout. Show PARTIAL, retained evidence, and the timeout event. |
| 12–13 | Open Observability, show event and trace correlation availability, and close with the original REVIEW recommendation. |
