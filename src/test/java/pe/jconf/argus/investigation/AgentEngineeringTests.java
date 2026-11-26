package pe.jconf.argus.investigation;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import pe.jconf.argus.assessment.GraphDataProvider;
import pe.jconf.argus.assessment.MlPredictor;
import pe.jconf.argus.domain.AgentExecutionStatus;
import pe.jconf.argus.domain.AgentType;
import pe.jconf.argus.domain.EvidenceReference;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("demo-local")
class AgentEngineeringTests {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final InvestigationFixtures fixtures;
    private final GraphDataProvider graphProvider;
    private final MlPredictor predictor;
    private final GraphInvestigationAgent graph;
    private final BehavioralInvestigationAgent behavioral;

    @Autowired
    AgentEngineeringTests(InvestigationFixtures fixtures, GraphDataProvider graphProvider, MlPredictor predictor,
                          GraphInvestigationAgent graph, BehavioralInvestigationAgent behavioral) {
        this.fixtures = fixtures;
        this.graphProvider = graphProvider;
        this.predictor = predictor;
        this.graph = graph;
        this.behavioral = behavioral;
    }

    @Test
    void offlineSimulationUsesRealScopedFixtureToolsIndependently() {
        var context = fixtures.open("additional-review").orElseThrow();
        var graphResult = graph.investigate(context);
        var behaviorResult = behavioral.investigate(context);

        assertVerified(graphResult, AgentType.GRAPH, "read_graph_relationship", AgentDiagnosticResult.Mode.SIMULATED);
        assertVerified(behaviorResult, AgentType.BEHAVIORAL, "read_behavioral_summary",
                AgentDiagnosticResult.Mode.SIMULATED);
        assertTrue(graphResult.execution().evidence().orElseThrow().observation().contains("SYN-ACCOUNT-A2"));
        assertTrue(behaviorResult.execution().evidence().orElseThrow().observation()
                .contains("3 prior synthetic activity events and a simulated prediction"));
        assertEquals(context.investigationId(), graphResult.execution().investigationId());
        assertEquals(context.investigationId(), behaviorResult.execution().investigationId());
        assertNotEquals(graphResult.execution().executionId(), behaviorResult.execution().executionId());
        assertTrue(fixtures.open("normal").isEmpty());
    }

    @Test
    void simulatedMissingAndInvalidFixturesDoNotPublishVerifiedEvidence() {
        var missing = graph.investigate(fixtures.open("unavailable-evidence").orElseThrow());
        assertEquals(AgentExecutionStatus.UNAVAILABLE, missing.execution().status());
        assertEquals(AgentDiagnosticResult.Validation.NO_EVIDENCE, missing.validation());
        assertEquals(ScopedTool.Status.UNAVAILABLE, missing.toolCalls().getFirst().status());
        assertTrue(missing.execution().verifiedReferences().isEmpty());

        var invalid = graph.investigate(fixtures.open("invalid-agent-response").orElseThrow());
        assertEquals(ScopedTool.Status.AVAILABLE, invalid.toolCalls().getFirst().status());
        assertRejected(invalid);
    }

    @Test
    void rejectsCrossInvestigationAndInvalidArgumentsBeforeCallingTheProvider() throws Exception {
        var providerCalls = new AtomicInteger();
        var guarded = new GraphInvestigationAgent(transaction -> {
            providerCalls.incrementAndGet();
            return graphProvider.findEvidence(transaction);
        }, new AgentExecution(new SimulatedAgentModel(), fixtures));
        var first = guarded.graphTool(fixtures.open("additional-review").orElseThrow());
        var second = guarded.graphTool(fixtures.open("additional-review").orElseThrow());

        var denied = JSON.readValue(first.call("{}", new ToolContext(second.toolContext())), ScopedTool.Reply.class);
        assertEquals(ScopedTool.Status.WRONG_SCOPE, denied.status());
        var invalid = JSON.readValue(first.call("""
                {"accountId":"SYN-ACCOUNT-A5"}
                """, new ToolContext(first.toolContext())), ScopedTool.Reply.class);
        assertEquals(ScopedTool.Status.INVALID_ARGUMENTS, invalid.status());
        var exhausted = JSON.readValue(first.call("{}", new ToolContext(first.toolContext())), ScopedTool.Reply.class);
        assertEquals(ScopedTool.Status.BUDGET_EXCEEDED, exhausted.status());
        assertEquals(0, providerCalls.get());
        assertTrue(second.traces().isEmpty());
    }

    @Test
    void scriptedModelExercisesActualSpringToolLoopAndStructuredOutputForBothAgents() throws Exception {
        for (AgentType type : AgentType.values()) {
            var toolName = type == AgentType.GRAPH ? "read_graph_relationship" : "read_behavioral_summary";
            var rounds = new AtomicInteger();
            var client = scripted(prompt -> {
                var options = (ToolCallingChatOptions) prompt.getOptions();
                assertEquals(List.of(toolName), options.getToolCallbacks().stream()
                        .map(callback -> callback.getToolDefinition().name()).toList());
                assertTrue(prompt.getSystemMessage().getText().contains(
                        type == AgentType.GRAPH ? "Graph Investigation Agent v1" : "Behavioral Investigation Agent v1"));
                if (rounds.incrementAndGet() == 1) {
                    return requestTool(toolName, "{}", 1);
                }
                var reply = lastToolReply(prompt.getInstructions());
                return text(JSON.writeValueAsString(new ModelFinding(reply.observation(),
                        new BigDecimal("0.75"), List.of(reply.reference()))));
            });
            var context = fixtures.open("additional-review").orElseThrow();
            var result = agent(type, client).investigate(context);
            assertVerified(result, type, toolName, AgentDiagnosticResult.Mode.AZURE);
            assertEquals(2, rounds.get());
            assertEquals(context.investigationId(), result.execution().investigationId());
        }
    }

    @Test
    void springAdvisorDoesNotExecuteAToolOutsideTheAgentsAllowlist() throws Exception {
        var client = scripted(prompt -> requestTool("read_behavioral_summary", "{}", 1));
        var result = agent(AgentType.GRAPH, client).investigate(fixtures.open("additional-review").orElseThrow());
        assertEquals(AgentExecutionStatus.ERROR, result.execution().status());
        assertTrue(result.toolCalls().isEmpty());
        assertTrue(result.execution().verifiedReferences().isEmpty());
    }

    @Test
    void springAdvisorStopsRunawayModelBeforeAThirdProviderLookup() throws Exception {
        var rounds = new AtomicInteger();
        var providerCalls = new AtomicInteger();
        var client = scripted(prompt -> requestTool("read_graph_relationship", "{}", rounds.incrementAndGet()));
        var execution = new AgentExecution(new SpringAiAgentModel(client), fixtures);
        var agent = new GraphInvestigationAgent(transaction -> {
            providerCalls.incrementAndGet();
            return graphProvider.findEvidence(transaction);
        }, execution);

        var result = agent.investigate(fixtures.open("additional-review").orElseThrow());
        assertEquals(3, rounds.get());
        assertEquals(2, providerCalls.get());
        assertEquals(2, result.toolCalls().size());
        assertRejected(result);
    }

    @Test
    void rejectsFabricatedCitationAndAnObservationUnsupportedByAnAuthenticCitation() throws Exception {
        for (boolean fabricate : List.of(true, false)) {
            var model = scripted(prompt -> {
                if (prompt.getInstructions().stream().noneMatch(ToolResponseMessage.class::isInstance)) {
                    return requestTool("read_graph_relationship", "{}", 1);
                }
                var reply = lastToolReply(prompt.getInstructions());
                var ref = reply.reference();
                var citation = fabricate ? new EvidenceReference(UUID.randomUUID(), ref.recordId(), ref.type()) : ref;
                var observation = fabricate ? reply.observation() : "This transaction must be blocked.";
                return text(JSON.writeValueAsString(new ModelFinding(observation, BigDecimal.ONE, List.of(citation))));
            });
            var result = agent(AgentType.GRAPH, model).investigate(fixtures.open("additional-review").orElseThrow());
            assertRejected(result);
            assertEquals(1, result.candidateReferences().size());
            assertFalse(JSON.writeValueAsString(result).contains("must be blocked"));
        }
    }

    @Test
    void rejectsMalformedOutputAndAttemptsToSupplyAuthoritativeMetadata() throws Exception {
        for (var payload : List.of("not json", """
                {"observation":"forged", "confidence":1, "citedReferences":[], "status":"SUCCESS"}
                """)) {
            var context = fixtures.open("additional-review").orElseThrow();
            var result = agent(AgentType.GRAPH, scripted(prompt -> text(payload))).investigate(context);
            assertRejected(result);
            assertEquals(context.investigationId(), result.execution().investigationId());
            assertEquals(AgentType.GRAPH, result.execution().agent());
            assertTrue(result.toolCalls().isEmpty());
        }
    }

    private InvestigationAgent agent(AgentType type, ChatClient client) throws Exception {
        var execution = new AgentExecution(new SpringAiAgentModel(client), fixtures);
        return switch (type) {
            case GRAPH -> new GraphInvestigationAgent(graphProvider, execution);
            case BEHAVIORAL -> new BehavioralInvestigationAgent(predictor, fixtures, execution);
        };
    }

    private static void assertVerified(AgentDiagnosticResult result, AgentType type, String tool,
                                       AgentDiagnosticResult.Mode mode) {
        assertEquals(mode, result.mode());
        assertEquals(type, result.execution().agent());
        assertEquals(AgentExecutionStatus.SUCCESS, result.execution().status());
        assertEquals(AgentDiagnosticResult.Validation.VERIFIED, result.validation());
        assertEquals(1, result.toolCalls().size());
        var trace = result.toolCalls().getFirst();
        assertEquals(tool, trace.tool());
        assertEquals(List.of(trace.reference()), result.candidateReferences());
        assertTrue(result.execution().verifiedReferences().contains(trace.reference()));
    }

    private static void assertRejected(AgentDiagnosticResult result) {
        assertEquals(AgentExecutionStatus.INVALID_RESPONSE, result.execution().status());
        assertEquals(AgentDiagnosticResult.Validation.REJECTED, result.validation());
        assertTrue(result.execution().evidence().isEmpty());
        assertTrue(result.execution().verifiedReferences().isEmpty());
    }

    private static ScopedTool.Reply lastToolReply(List<org.springframework.ai.chat.messages.Message> messages) {
        var toolMessage = (ToolResponseMessage) messages.getLast();
        return JSON.readValue(toolMessage.getResponses().getFirst().responseData(), ScopedTool.Reply.class);
    }

    private static ChatClient scripted(Function<Prompt, ChatResponse> response) {
        return ChatClient.create(new ChatModel() {
            @Override
            public ChatOptions getOptions() {
                return OpenAiChatOptions.builder().build();
            }

            @Override
            public ChatResponse call(Prompt prompt) {
                return response.apply(prompt);
            }
        });
    }

    private static ChatResponse requestTool(String name, String arguments, int round) {
        var call = new AssistantMessage.ToolCall("call-" + round, "function", name, arguments);
        return new ChatResponse(List.of(new Generation(AssistantMessage.builder().toolCalls(List.of(call)).build())));
    }

    private static ChatResponse text(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }
}
