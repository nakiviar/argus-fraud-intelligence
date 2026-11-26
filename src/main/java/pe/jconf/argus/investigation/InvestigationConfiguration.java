package pe.jconf.argus.investigation;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import pe.jconf.argus.assessment.GraphDataProvider;
import pe.jconf.argus.assessment.MlPredictor;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

@Configuration(proxyBeanMethods = false)
@Profile({"demo-local & !demo-azure", "demo-azure & !demo-local"})
class InvestigationConfiguration {
    @Bean
    InvestigationFixtures investigationFixtures() throws IOException {
        return new InvestigationFixtures(JsonMapper.builder().build());
    }

    @Bean
    @Profile("demo-local & !demo-azure")
    AgentModel simulatedAgentModel() {
        return new SimulatedAgentModel();
    }

    @Bean
    @Profile("demo-azure & !demo-local")
    AgentModel springAiAgentModel(ChatClient foundryChatClient) {
        return new SpringAiAgentModel(foundryChatClient);
    }

    @Bean
    AgentExecution agentExecution(AgentModel model, InvestigationFixtures fixtures) {
        return new AgentExecution(model, fixtures);
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
}
