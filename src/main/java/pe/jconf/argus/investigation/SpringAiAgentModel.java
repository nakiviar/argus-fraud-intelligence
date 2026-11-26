package pe.jconf.argus.investigation;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallLimitBehavior;
import org.springframework.ai.openai.OpenAiChatOptions;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

final class SpringAiAgentModel implements AgentModel {
    private final ChatClient client;
    private final RemoteModelProtection protection;
    private final Duration httpTimeout;
    private final ToolCallingAdvisor toolAdvisor;
    private final BeanOutputConverter<ModelFinding> converter = new BeanOutputConverter<>(ModelFinding.class) {
        @Override
        protected JsonMapper getJsonMapper() {
            return JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                    DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).build();
        }
    };

    SpringAiAgentModel(ChatClient client) {
        this(client, new RemoteModelProtection(2, Duration.ofSeconds(20), event -> { }), Duration.ofSeconds(10));
    }

    SpringAiAgentModel(ChatClient client, RemoteModelProtection protection, Duration httpTimeout) {
        this(client, protection, httpTimeout, ObservationRegistry.NOOP);
    }

    SpringAiAgentModel(ChatClient client, RemoteModelProtection protection, Duration httpTimeout,
                       ObservationRegistry observations) {
        this.client = client;
        this.protection = protection;
        this.httpTimeout = httpTimeout;
        this.toolAdvisor = ToolCallingAdvisor.builder()
                .toolCallingManager(DefaultToolCallingManager.builder()
                        .observationRegistry(observations)
                        .maxCallsPerTool(ScopedTool.BUDGET).maxTotalToolCalls(ScopedTool.BUDGET)
                        .onLimitExceeded(ToolCallLimitBehavior.THROW).resolutionFallbackEnabled(false).build())
                .build();
    }

    @Override
    public AgentDiagnosticResult.Mode mode() {
        return AgentDiagnosticResult.Mode.AZURE;
    }

    @Override
    public ModelFinding generate(String instructions, ScopedTool tool, InvestigationFixtures.Scenario fixture) {
        return client.prompt().system(instructions).user("Investigate the active fictional case using your scoped tool.")
                .advisors(toolAdvisor, new ModelRoundAdvisor(protection, tool.budget(), httpTimeout))
                .toolCallbacks(tool).toolContext(tool.toolContext())
                .options(OpenAiChatOptions.builder().maxCompletionTokens(384))
                .call().entity(converter);
    }
}
