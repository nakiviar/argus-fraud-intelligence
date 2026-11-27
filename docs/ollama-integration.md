# Local Ollama inference

Run `./scripts/start-argus-demo.ps1 -Mode Ollama` after building the JAR. The configured model defaults to the installed llama3.1:latest (8B, Q4_K_M, 4.9 GB). Set OLLAMA_MODEL for another installed local tag and OLLAMA_ENDPOINT for an IPv4 loopback HTTP origin with explicit port. The app adds /v1 and uses the existing Spring AI 2.0.1 OpenAiChatModel and ChatClient. The SDK's placeholder credential is ignored by local Ollama. Native OllamaChatModel would use the native API and require another starter and a different per-round timeout adapter. Existing callback permissions, strict record conversion and proof of provenance apply across providers.

| Property | Ollama default |
|---|---|
| argus.ollama.endpoint | http://127.0.0.1:11434 |
| argus.ollama.model | llama3.1:latest |
| argus.investigation.deadline | PT180S |
| argus.investigation.agent-timeout | PT165S |
| argus.investigation.http-request-timeout | PT100S |

SDK retries are zero. Independent HTTP, agent and case limits remain in force. Run the connectivity probe with `--spring.profiles.active=demo-ollama --check-model-connectivity`. Use `--check-investigation-agent=graph --scenario=additional-review` or `--check-investigation-agent=behavioral --scenario=additional-review` to inspect actual tool invocation and reference validation. Probe results belong to the diagnostic process. Provider selection does not certify connectivity. The connectivity diagnostic exposes provider-reported tokens when present; the dashboard cannot infer per-case usage from a separate process.

Ollama, Foundry and simulation are exclusive model profiles. Start another process with demo-azure and authorized Foundry credentials to switch to Azure, or demo-local to use deterministic agents. Java workers can overlap while a local runner schedules or queues the actual inference. Hardware, model residency and competing load affect timings.

[Ollama compatibility](https://docs.ollama.com/api/openai-compatibility) · [Tool calling](https://docs.ollama.com/capabilities/tool-calling) · [Structured outputs](https://docs.ollama.com/capabilities/structured-outputs)


## Observed on the Windows development machine

Ollama 0.40.18 reported completion and tools for llama3.1:latest. The fixed acknowledgement took 45.573 s after initial load and 4.569 s warm, with 30 reported tokens each. The separate Graph and Behavioral diagnostics completed in 59.341 s and 63.616 s. Each executed its scoped tool and received VERIFIED validation. The local runner reported a 5.6 GB allocation split between CPU and GPU. No additional model was downloaded.
