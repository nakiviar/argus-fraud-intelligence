package pe.jconf.argus.investigation;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallLimitBehavior;
import org.springframework.ai.openai.OpenAiChatOptions;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

final class SpringAiAgentModel implements AgentModel {
    private final ChatClient client;
    private final ToolCallingAdvisor toolAdvisor;
    private final BeanOutputConverter<ModelFinding> converter = new BeanOutputConverter<>(ModelFinding.class) {
        @Override
        protected JsonMapper getJsonMapper() {
            return JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                    DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                    DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY).build();
        }
    };

    SpringAiAgentModel(ChatClient client) {
        this.client = client;
        this.toolAdvisor = ToolCallingAdvisor.builder()
                .toolCallingManager(DefaultToolCallingManager.builder()
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
                .advisors(toolAdvisor).toolCallbacks(tool).toolContext(tool.toolContext())
                .options(OpenAiChatOptions.builder().maxCompletionTokens(384))
                .call().entity(converter);
    }
}
