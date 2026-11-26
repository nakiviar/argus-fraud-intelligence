# ARGUS Fraud Intelligence

**Agents investigate. Deterministic code decides.**

ARGUS is a fictional banking fraud investigation proof of concept for JConf Peru 2026. Phase 01 defines the domain contracts and synthetic scenarios for two paths. The Hot Path will assess transactions synchronously with deterministic code. The Investigation Path will gather evidence for human review after a REVIEW decision. The current application exposes health on localhost.

## Prerequisites

Use a Java 21 JDK installed outside the project build directory and Maven 3.9.x. Select the JDK for the current shell. For example, PowerShell can use a persistent JDK installed under the user profile:

```powershell
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\ms-21.0.8"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn -version
```

Adjust the path to match your installation. Maven needs access to Maven Central for the first build unless dependencies and plugins are cached.

```sh
mvn clean verify
java -jar target/argus-fraud-intelligence-0.1.0-SNAPSHOT.jar
```

Open http://127.0.0.1:8080/actuator/health. The default profile is `demo-local`. The packaged application runs offline and uses no cloud credentials. Use `mvn -o test` after caching the test dependencies and plugins.

## Microsoft Foundry profile

Set `AZURE_OPENAI_BASE_URL` to your Azure OpenAI v1 base URL, such as `https://RESOURCE.openai.azure.com/openai/v1/`. Set `AZURE_OPENAI_DEPLOYMENT` to the chat deployment name and supply `AZURE_OPENAI_API_KEY` through the environment or a secret provider. Launch with:

```sh
java -jar target/argus-fraud-intelligence-0.1.0-SNAPSHOT.jar --spring.profiles.active=demo-azure
```

Phase 0 configures the chat client. The health endpoint checks application health and does not call the deployment.

## Project documentation

- [Contributor instructions](AGENTS.md)
- [Architecture](docs/architecture.md)
- [Domain model](docs/domain-model.md)
- [Synthetic scenarios](src/main/resources/synthetic/scenarios.json)
- [Roadmap](docs/roadmap.md)
