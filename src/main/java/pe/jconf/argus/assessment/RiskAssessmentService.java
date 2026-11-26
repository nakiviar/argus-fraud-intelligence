package pe.jconf.argus.assessment;

import pe.jconf.argus.domain.RiskSignal;
import pe.jconf.argus.domain.Transaction;
import pe.jconf.argus.observability.ArgusTelemetry;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static pe.jconf.argus.domain.RiskSignal.Source.GRAPH_DATA;
import static pe.jconf.argus.domain.RiskSignal.Source.RULES;
import static pe.jconf.argus.domain.RiskSignal.Source.SYNTHETIC_ML;

public final class RiskAssessmentService {
    private final ScenarioCatalog catalog;
    private final RulesEngine rules;
    private final GraphDataProvider graphProvider;
    private final MlPredictor predictor;
    private final RiskDecisionEngine decisionEngine;
    private final AuditRecorder audit;
    private final Clock clock;
    private final ArgusTelemetry telemetry;

    RiskAssessmentService(ScenarioCatalog catalog, RulesEngine rules, GraphDataProvider graphProvider,
                          MlPredictor predictor, RiskDecisionEngine decisionEngine, AuditRecorder audit, Clock clock) {
        this(catalog, rules, graphProvider, predictor, decisionEngine, audit, clock, ArgusTelemetry.noop());
    }

    RiskAssessmentService(ScenarioCatalog catalog, RulesEngine rules, GraphDataProvider graphProvider,
                          MlPredictor predictor, RiskDecisionEngine decisionEngine, AuditRecorder audit, Clock clock,
                          ArgusTelemetry telemetry) {
        this.telemetry = telemetry;
        this.catalog = catalog;
        this.rules = rules;
        this.graphProvider = graphProvider;
        this.predictor = predictor;
        this.decisionEngine = decisionEngine;
        this.audit = audit;
        this.clock = clock;
    }

    public List<ScenarioSummary> listScenarios() {
        return catalog.scenarios().values().stream()
                .map(scenario -> new ScenarioSummary(scenario.scenario(), scenario.transaction().transactionId()))
                .toList();
    }

    public Optional<Transaction> scenario(String scenarioId) {
        return catalog.find(scenarioId).map(ScenarioCatalog.Scenario::transaction);
    }

    public Optional<Assessment> assess(String scenarioId) {
        var observation = telemetry.operation("argus.assessment");
        return observation.observe(() -> {
            var result = catalog.find(scenarioId).map(this::assess);
            result.ifPresent(value -> telemetry.rememberAssessmentTrace(value.assessmentId()));
            observation.lowCardinalityKeyValue("status",
                    result.map(value -> value.riskDecision().decision().name()).orElse("UNKNOWN_SCENARIO"));
            return result;
        });
    }

    public Optional<Assessment> find(UUID assessmentId) {
        return audit.find(assessmentId);
    }

    private Assessment assess(ScenarioCatalog.Scenario scenario) {
        var transaction = scenario.transaction();
        var graph = graphProvider.findEvidence(transaction);
        var prediction = predictor.predict(transaction);
        var references = new ArrayList<Assessment.VerifiedReference>();
        references.add(new Assessment.VerifiedReference(RULES, scenario.referenceId("rules")));
        graph.ifPresent(evidence -> references.add(verify(transaction, GRAPH_DATA, evidence)));
        prediction.ifPresent(evidence -> references.add(verify(transaction, SYNTHETIC_ML, evidence)));

        var evaluation = telemetry.operation("argus.assessment.decision");
        var decision = evaluation.observe(() -> {
            var result = decisionEngine.decide(transaction, rules.evaluate(scenario.hotPath().violatesMandatoryPolicy()),
                    graph, prediction, clock.instant());
            evaluation.lowCardinalityKeyValue("status", result.decision().name());
            return result;
        });
        var assessment = new Assessment(UUID.randomUUID(), RiskDecisionEngine.POLICY_ID, decision, references,
                decisionEngine.reasonCodes(decision));
        audit.record(assessment);
        return assessment;
    }

    private Assessment.VerifiedReference verify(Transaction transaction, RiskSignal.Source source,
                                                ProviderEvidence evidence) {
        if (!transaction.transactionId().equals(evidence.transactionId())) {
            throw new IllegalStateException("evidence does not belong to the assessed transaction");
        }
        return new Assessment.VerifiedReference(source, evidence.recordId());
    }

    public record ScenarioSummary(String scenarioId, String transactionId) {
    }
}
