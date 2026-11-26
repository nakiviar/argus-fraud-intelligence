# Spring AI foundations

Phase 03 follows one explicit invocation from `ChatClient` through `OpenAiChatModel` and the OpenAI Java SDK to the Azure OpenAI v1 inference endpoint. Credentials and deployment selection belong to the provider configuration. The assessed transaction never enters this flow.

## Conference Notes

- Spring AI supplies Java and Spring abstractions for calling AI models and composing application-side capabilities. Model weights, deployment capacity, identities, and inference hosting belong to the model provider.
- `ChatClient` is the application-facing fluent interface for composing a prompt and obtaining a response. `ChatModel` is the provider-neutral model contract. `OpenAiChatModel` implements it and translates between Spring AI and the OpenAI SDK.
- The OpenAI Java SDK owns the wire protocol, HTTP request construction, authentication headers, response types, and retry configuration. The same SDK supports the Azure OpenAI v1 route through explicit unified Azure URL handling.
- The 2.0 upgrade notes explain that the OpenAI module adopted the official OpenAI Java SDK and removed the separate Azure OpenAI module and starter. Its migration path uses the regular OpenAI starter for Azure OpenAI and Microsoft Foundry.
- Model hosting serves inference for a deployed model. Agent hosting additionally runs agent instructions, orchestration, tools, and state. Phase 03 exercises model inference from an application-owned diagnostic.
- Future application agents can share a model deployment while maintaining distinct prompts, tool permissions, and output schemas. Shared deployment capacity and quotas still apply to their aggregate traffic.

## References

- [Spring AI reference](https://docs.spring.io/spring-ai/reference/2.0/)
- [Spring AI upgrade notes](https://docs.spring.io/spring-ai/reference/2.0/upgrade-notes.html)
- [Spring AI v2.0.1 source](https://github.com/spring-projects/spring-ai/tree/v2.0.1)
- [Microsoft Spring AI for Beginners](https://github.com/microsoft/Spring-AI-for-Beginners)
- [ARGUS Azure connectivity guide](azure-integration.md)

