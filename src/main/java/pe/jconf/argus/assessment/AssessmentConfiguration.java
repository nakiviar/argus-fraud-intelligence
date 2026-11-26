package pe.jconf.argus.assessment;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
class AssessmentConfiguration {
    @Bean
    ScenarioCatalog scenarioCatalog() throws IOException {
        return new ScenarioCatalog(JsonMapper.builder().build());
    }

    @Bean
    GraphDataProvider graphDataProvider(ScenarioCatalog catalog) {
        return new SyntheticGraphDataProvider(catalog);
    }

    @Bean
    MlPredictor mlPredictor(ScenarioCatalog catalog) {
        return new SyntheticMlPredictor(catalog);
    }

    @Bean
    AuditRecorder auditRecorder() {
        return new AuditRecorder();
    }

    @Bean
    RiskAssessmentService riskAssessmentService(ScenarioCatalog catalog, GraphDataProvider graph,
                                                MlPredictor predictor, AuditRecorder audit) {
        return new RiskAssessmentService(catalog, new RulesEngine(), graph, predictor, new RiskDecisionEngine(),
                audit, Clock.systemUTC());
    }
}
