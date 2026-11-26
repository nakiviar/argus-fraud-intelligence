# Microsoft Foundry connectivity

Phase 03 adds an opt-in command-line diagnostic. The assessment API remains independent of the model and uses bundled fixtures in both profiles. The diagnostic runs without an HTTP listener and sends a single fixed acknowledgement prompt with no transaction information.

## Resource, project, and deployment

A Foundry resource provides the Azure service boundary for model inference. A Foundry project organizes AI application assets and access within the resource. Copy the HTTPS resource endpoint from an existing, authorized deployment. Project endpoints include a project path and serve project-level APIs.

A deployment name is the identifier you choose when deploying a model version. Use that name as the JSON `model` value. The base model family name works only when it matches the deployment name. This diagnostic expects a deployment supporting Chat Completions and `max_completion_tokens`. A small chat deployment suited to short replies is appropriate for the fixed acknowledgement.

## Configuration on Spring AI 2.0.1

| Setting | Value and use |
| --- | --- |
| `spring.ai.openai.base-url` | `${AZURE_OPENAI_ENDPOINT:}`. Accepts the resource origin or its exact `/openai/v1` path, with optional trailing slash. ARGUS normalizes it to one v1 path. |
| `spring.ai.openai.chat.model` | `${AZURE_OPENAI_DEPLOYMENT:}`. Passed to the Chat Completions body as the deployment name. |
| `spring.ai.openai.timeout` | `PT10S`. Applied to the SDK and its HTTP transport. |
| `spring.ai.openai.max-retries` | `0`. Applies to SDK retries. The underlying HTTP transport retains its normal connection handling. |
| `spring.ai.openai.chat.max-completion-tokens` | `64`. Counts completion tokens, including reasoning tokens on applicable models. |
| `spring.ai.model.chat` | `none`. Both profiles disable the automatic OpenAI model. The Azure profile explicitly constructs its dedicated ChatClient. |
| `spring.ai.chat.client.enabled` | `false`. Disables the automatic ChatClient builder. |
| `spring.ai.model.embedding`, `spring.ai.model.image`, `spring.ai.model.audio.speech`, `spring.ai.model.audio.transcription`, `spring.ai.model.moderation` | `none`. Disables unused models from the starter. |

Spring AI 2.0.1's OpenAiSetup distinguishes unified and legacy Azure paths. It can also select a credential from the environment. ARGUS reads the documented configuration keys, builds the resolved OpenAI Java SDK 4.49.0 client with `AzureUrlPathMode.UNIFIED` and an explicit bearer credential, then supplies that client to Spring AI's `OpenAiChatModel` and `ChatClient`. The loopback test asserts the exact `POST /openai/v1/chat/completions` request, the deployment in the JSON body, and the absence of `api-version` and `api-key`. The common deployment property `spring.ai.openai.microsoft-deployment-name` is available in the release. This integration uses the v1 body field through `spring.ai.openai.chat.model`.

## PowerShell

Build with Java 21 and Maven 3.9.9 using `mvn clean verify`. Use the [README](../README.md) to select Java 21 for the current shell.

```powershell
$jar = 'target/argus-fraud-intelligence-0.1.0-SNAPSHOT.jar'

# Optional offline simulation. It prints one result and exits.
java -jar $jar --spring.profiles.active=demo-local --check-model-connectivity

# Start the offline assessment API.
java -jar $jar --spring.profiles.active=demo-local
```

Use a separate shell for the Azure diagnostic. Use an Azure CLI session already signed in to an authorized identity with permission to run inference on the existing deployment. Run the last command only when the request and any associated charge are authorized.

```powershell
$jar = 'target/argus-fraud-intelligence-0.1.0-SNAPSHOT.jar'
$env:AZURE_OPENAI_ENDPOINT = 'https://RESOURCE.openai.azure.com'
$env:AZURE_OPENAI_DEPLOYMENT = 'CHAT-DEPLOYMENT'

$env:AZURE_OPENAI_ACCESS_TOKEN = az account get-access-token --resource https://cognitiveservices.azure.com --query accessToken -o tsv --only-show-errors
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($env:AZURE_OPENAI_ACCESS_TOKEN)) {
    Remove-Item Env:AZURE_OPENAI_ACCESS_TOKEN -ErrorAction SilentlyContinue
    throw 'Could not obtain an authorized Microsoft Entra access token.'
}
try {
    java -jar $jar --spring.profiles.active=demo-azure --check-model-connectivity
    $diagnosticExit = $LASTEXITCODE
} finally {
    Remove-Item Env:AZURE_OPENAI_ACCESS_TOKEN -ErrorAction SilentlyContinue
}
$diagnosticExit
```

The access token is short-lived and read from the process environment. Reacquire it before another diagnostic when it expires. ARGUS uses bearer authentication and accepts no API-key mode. Keep the token out of command-line arguments, PowerShell transcripts, source files, build artifacts and shared logs. Running the Azure profile without the diagnostic flag only validates the configuration and starts the application. Application startup and Actuator health perform no inference.

## Results and troubleshooting

The diagnostic emits JSON and exits with 0 for a successful check, 1 for a failed probe and 2 for configuration or startup failure. `SIMULATED` identifies the offline stub. `AZURE` identifies the configured client path. Only an actual invocation against an authorized deployment verifies live connectivity. Elapsed milliseconds and optional token counts are reported, and the fixed acknowledgement is the only provider text emitted.

| Category | Check |
| --- | --- |
| CONFIGURATION | Select one profile and check the environment variable names, deployment name and resource endpoint. The endpoint accepts public Azure resource hostnames ending in `.openai.azure.com` or `.services.ai.azure.com`. |
| AUTHENTICATION | Check the token expiry, Azure tenant, token audience, and existing inference permissions. Azure can return 401 or 403. |
| DEPLOYMENT_UNAVAILABLE | Verify the deployment exists on the selected resource and supports the request. A 404 can also indicate a service route mismatch. |
| RATE_LIMIT | Check deployment quota and capacity. The diagnostic performs zero SDK retries. |
| CONNECTION | Check DNS, TLS, network access and the request timeout. |
| PROVIDER_ERROR | Check deployment compatibility and provider health. Raw error bodies and headers stay out of the result. |
| UNEXPECTED_RESPONSE | The server responded but the result did not contain the exact acknowledgement. Check model compatibility and the completion budget. |

Live inference, network policies, RBAC, hosting region, and quota must be verified in the target Azure environment. Maven verification uses local test doubles and the offline profile.

## References

- [Spring AI source at v2.0.1](https://github.com/spring-projects/spring-ai/tree/v2.0.1)
- [OpenAI Java SDK source at v4.49.0](https://github.com/openai/openai-java/tree/v4.49.0)
- [Microsoft Foundry documentation](https://learn.microsoft.com/en-us/azure/foundry/)
- [Azure OpenAI v1 lifecycle and authentication](https://learn.microsoft.com/en-us/azure/foundry/openai/api-version-lifecycle)

