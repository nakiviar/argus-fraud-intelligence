# Local benchmark report

Measured on November 26, 2026 against the packaged Spring Boot JAR on Windows 11 amd64, Microsoft OpenJDK 21.0.8 and Intel Core i7-1165G7 with eight logical processors. One PowerShell client used loopback HTTP and submitted cases serially. The app ran demo-local with sampled traces and external export disabled. Initial warm-up comprised ten normal and ten review requests; the timeout process had one timeout warm-up.

| Workload | n | Client p50 / p95 ms | Server case p50 / p95 ms | Outcome and retained references |
|---|---:|---:|---:|---|
| Normal assessment | 60 | 4.714 / 6.828 | — | 60 APPROVE |
| Additional review | 30 | 8.666 / 11.613 | 2.000 / 2.999 | 30 COMPLETED, two references per case |
| Invalid agent response | 10 | 8.101 / 29.614 | 2.000 / 11.996 | 10 PARTIAL, one reference per case |
| Controlled Graph timeout | 5 | 2027.825 / 2049.396 | 2006.043 / 2016.543 | 5 PARTIAL, one reference per case |

All 45 investigations reached a terminal state and preserved REVIEW. The intentional workload produced 30/45 COMPLETED and 15/45 PARTIAL. Those rates reflect the sample mix.

| Workload | Graph p50 / p95 ms | Behavioral p50 / p95 ms |
|---|---:|---:|
| Additional review | 0.000 / 1.001 | 0.000 / 1.004 |
| Invalid response | 0.989 / 9.998 | 0.000 / 1.003 |
| Controlled timeout | 0.000 / 0.000 | 1.000 / 1.007 |

The Graph timeout row uses a synthetic terminal result whose start and finish match. Its zero-length interval describes the published result, and the approximately two-second duration is measured at the case level. Agent timings are calculated from the API timestamps as parsed by PowerShell/.NET. Zero also occurs in very short simulated work with the available timestamp resolution.

## Reproduction

Use Java 21 and the packaged JAR described in the [runbook](DEMO-RUNBOOK.md). Run [measure-local.ps1](measure-local.ps1) from the project root with port 19090 free. It starts and stops its own two local processes. It writes raw timings under .local. Stopwatch measures POST latency for assessments and POST through the first terminal GET for investigations. Polling includes a 10 ms delay when the previous result was nonterminal. Subtract the server creation timestamp from completion for case timing. Percentiles use nearest rank, `ceil(p*n)`. Diagnostic delays were 4 s Graph, with 5 s global, 2 s agent and 1 s HTTP budgets.

The application exposes one concurrent coordinator. The concurrency test proves overlap. No sequential baseline was measured, so these results do not quantify speedup. Sampling, client overhead, process load, coarse agent timestamps and small samples limit inference. Live Foundry latency, fraud detection accuracy, throughput under load and any production SLA remain outside these measurements.

