package pe.jconf.argus.investigation;

import pe.jconf.argus.assessment.MlPredictor;
import pe.jconf.argus.domain.AgentType;
import pe.jconf.argus.domain.EvidenceType;
import pe.jconf.argus.domain.InvestigationContext;

import java.io.IOException;

final class BehavioralInvestigationAgent implements InvestigationAgent {
    private final MlPredictor predictor;
    private final InvestigationFixtures fixtures;
    private final AgentExecution execution;
    private final String instructions;

    BehavioralInvestigationAgent(MlPredictor predictor, InvestigationFixtures fixtures, AgentExecution execution)
            throws IOException {
        this.predictor = predictor;
        this.fixtures = fixtures;
        this.execution = execution;
        this.instructions = AgentExecution.instructions("behavioral-investigation-agent.st");
    }

    @Override
    public AgentType type() {
        return AgentType.BEHAVIORAL;
    }

    @Override
    public AgentDiagnosticResult investigate(InvestigationContext context) {
        return investigate(context, ExecutionBudget.standalone(context.investigationId()));
    }

    @Override
    public AgentDiagnosticResult investigate(InvestigationContext context, ExecutionBudget budget) {
        return execution.run(type(), instructions, behavioralTool(context, budget));
    }

    ScopedTool behavioralTool(InvestigationContext context) {
        return behavioralTool(context, ExecutionBudget.standalone(context.investigationId()));
    }

    private ScopedTool behavioralTool(InvestigationContext context, ExecutionBudget budget) {
        var transaction = context.transaction();
        return new ScopedTool("read_behavioral_summary",
                "Read the current fictional activity summary and fixture-based prediction. Input: {}.",
                context, budget, EvidenceType.BEHAVIORAL, () -> fixtures.historicalCount(transaction)
                        .flatMap(count -> predictor.predict(transaction).map(result -> {
                            if (!result.transactionId().equals(transaction.transactionId())) {
                                throw new IllegalStateException("Prediction is outside the scoped transaction.");
                            }
                            var outcome = result.requiresReview() ? "REVIEW_MARKER" : "CLEAR_MARKER";
                            return new ScopedTool.Fact(result.recordId(), "The behavioral fixture lists " + count
                                    + " prior synthetic activity events and a simulated prediction of " + outcome + ".");
                        })));
    }
}
