# ARGUS contributor instructions

ARGUS is an educational proof of concept for JConf Peru 2026. The session is “Arquitectura Multiagente en Banca: Detección de Fraude Financiero con Spring AI 2.0.”

**Agents investigate. Deterministic code decides.**

- Keep source code, identifiers, and technical documentation in English. Explain implementation work in Spanish.
- Use Java 21 LTS, Maven, Spring Boot and Spring AI 2.0. Verify dependency versions and APIs against official documentation and Maven Central.
- Build a modular monolith with explicit dependencies and strongly typed contracts. Use records for immutable contracts where appropriate. Keep abstractions and dependencies proportional to the task.
- Separate deterministic rules, evidence validation, and consensus from generative investigation. Orchestrate independent investigators concurrently with fan-out/fan-in. Only deterministic code may decide the outcome of a simulated transaction.
- Use ChatClient, Tool Calling, and Structured Output for generative agents. Expose least-privilege, read-only tools and validate returned evidence deterministically.
- Use synthetic data exclusively. Never connect to real financial operations or hardcode credentials.
- Keep demo-local credential-free and offline at runtime. Implement deterministic simulated responses when investigation is in scope. Use the Spring AI 2.0 OpenAI starter with Microsoft Foundry and Azure OpenAI for demo-azure.
- Use Resilience4j for resilience, Micrometer and OpenTelemetry for observability, Spring Boot-managed JUnit and Mockito for tests, and plain HTML, CSS, and JavaScript for the dashboard.
- Phase 02 covers the offline deterministic Hot Path, its local REST API, and volatile audit. Obtain explicit approval before implementing Investigation Path workflows, agents, or provider integration.
