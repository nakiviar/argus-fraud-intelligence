package pe.jconf.argus.investigation;

import pe.jconf.argus.assessment.GraphDataProvider;
import pe.jconf.argus.domain.AgentType;
import pe.jconf.argus.domain.EvidenceType;
import pe.jconf.argus.domain.InvestigationContext;

import java.io.IOException;

final class GraphInvestigationAgent implements InvestigationAgent {
    private final GraphDataProvider graph;
    private final AgentExecution execution;
    private final String instructions;

    GraphInvestigationAgent(GraphDataProvider graph, AgentExecution execution) throws IOException {
        this.graph = graph;
        this.execution = execution;
        this.instructions = AgentExecution.instructions("graph-investigation-agent.st");
    }

    @Override
    public AgentType type() {
        return AgentType.GRAPH;
    }

    @Override
    public AgentDiagnosticResult investigate(InvestigationContext context) {
        return investigate(context, ExecutionBudget.standalone(context.investigationId()));
    }

    @Override
    public AgentDiagnosticResult investigate(InvestigationContext context, ExecutionBudget budget) {
        return execution.run(type(), instructions, graphTool(context, budget));
    }

    ScopedTool graphTool(InvestigationContext context) {
        return graphTool(context, ExecutionBudget.standalone(context.investigationId()));
    }

    private ScopedTool graphTool(InvestigationContext context, ExecutionBudget budget) {
        var transaction = context.transaction();
        return new ScopedTool("read_graph_relationship", "Read the current fictional account relationship. Input: {}.",
                context, budget, EvidenceType.GRAPH, () -> graph.findEvidence(transaction).map(result -> {
                    if (!result.transactionId().equals(transaction.transactionId())) {
                        throw new IllegalStateException("Graph result is outside the scoped transaction.");
                    }
                    return new ScopedTool.Fact(result.recordId(), "The graph fixture links synthetic accounts "
                            + transaction.sourceAccountId() + " and " + transaction.destinationAccountId()
                            + " for this case.");
                }));
    }
}
