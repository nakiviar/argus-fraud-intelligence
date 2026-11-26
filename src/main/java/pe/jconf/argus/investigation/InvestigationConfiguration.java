package pe.jconf.argus.investigation;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import pe.jconf.argus.assessment.GraphDataProvider;
import pe.jconf.argus.assessment.MlPredictor;
import pe.jconf.argus.assessment.RiskAssessmentService;
import pe.jconf.argus.domain.AgentType;
import pe.jconf.argus.observability.ArgusTelemetry;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@Profile({"demo-local", "demo-azure", "demo-ollama"})
class InvestigationConfiguration {
    @Bean
    InvestigationFixtures investigationFixtures() throws IOException {
        return new InvestigationFixtures(JsonMapper.builder().build());
    }

    @Bean
    @Profile("demo-local & !demo-azure")
    AgentModel simulatedAgentModel(ArgusTelemetry telemetry) {
        return new SimulatedAgentModel(telemetry);
    }

    @Bean
    @Profile({"demo-azure", "demo-ollama"})
    AgentModel springAiAgentModel(ChatClient chatClient, RemoteModelProtection protection, InvestigationBudgets budgets,
                                  ObservationRegistry observations, org.springframework.core.env.Environment environment) {
        return new SpringAiAgentModel(chatClient, protection, budgets.http(), observations, environment.matchesProfiles("demo-ollama") ? AgentDiagnosticResult.Mode.OLLAMA : AgentDiagnosticResult.Mode.AZURE);
    }

    @Bean
    AgentExecution agentExecution(AgentModel model, InvestigationFixtures fixtures, InvestigationBudgets budgets,
                                  ApplicationEventPublisher events, ArgusTelemetry telemetry) {
        return new AgentExecution(model, fixtures, budgets.agent(), events::publishEvent, telemetry);
    }

    @Bean
    GraphInvestigationAgent graphInvestigationAgent(GraphDataProvider graph, AgentExecution execution) throws IOException {
        return new GraphInvestigationAgent(graph, execution);
    }

    @Bean
    BehavioralInvestigationAgent behavioralInvestigationAgent(MlPredictor predictor, InvestigationFixtures fixtures,
                                                               AgentExecution execution) throws IOException {
        return new BehavioralInvestigationAgent(predictor, fixtures, execution);
    }

    @Bean
    InvestigationBudgets investigationBudgets(@Value("${argus.investigation.deadline}") String global,
            @Value("${argus.investigation.agent-timeout}") String agent,
            @Value("${argus.investigation.http-request-timeout}") String http) {
        return new InvestigationBudgets(Duration.parse(global), Duration.parse(agent), Duration.parse(http));
    }

    @Bean
    @Profile({"demo-azure", "demo-ollama"})
    RemoteModelProtection remoteModelProtection(ApplicationEventPublisher events) {
        return new RemoteModelProtection(2, Duration.ofSeconds(20), events::publishEvent);
    }

    @Bean(destroyMethod = "close")
    InvestigationOrchestrator investigationOrchestrator(RiskAssessmentService assessments, InvestigationFixtures fixtures,
            List<InvestigationAgent> agents, AgentModel model, InvestigationBudgets budgets, ApplicationEventPublisher events,
            ArgusTelemetry telemetry,
            @Value("${argus.investigation.simulated-failure:}") String simulatedFailure,
            @Value("${argus.investigation.simulated-delay-agent:}") String simulatedDelayAgent,
            @Value("${argus.investigation.simulated-delay:PT0S}") String simulatedDelay) {
        AgentType failure = model.mode() == AgentDiagnosticResult.Mode.SIMULATED && !simulatedFailure.isBlank()
                ? AgentType.valueOf(simulatedFailure) : null;
        AgentType delayAgent = model.mode() == AgentDiagnosticResult.Mode.SIMULATED && !simulatedDelayAgent.isBlank()
                ? AgentType.valueOf(simulatedDelayAgent) : null;
        return new InvestigationOrchestrator(new InvestigationRepository(telemetry::lifecycle), assessments, fixtures, agents, model.mode(),
                failure, budgets, events::publishEvent, delayAgent, delayAgent == null ? Duration.ZERO : Duration.parse(simulatedDelay), telemetry);
    }
}
