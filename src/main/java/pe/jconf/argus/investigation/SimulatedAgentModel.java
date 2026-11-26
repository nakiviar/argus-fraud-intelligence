package pe.jconf.argus.investigation;

import org.springframework.ai.chat.model.ToolContext;
import pe.jconf.argus.domain.EvidenceType;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;

final class SimulatedAgentModel implements AgentModel {
    private final JsonMapper json = JsonMapper.builder().build();

    @Override
    public AgentDiagnosticResult.Mode mode() {
        return AgentDiagnosticResult.Mode.SIMULATED;
    }

    @Override
    public ModelFinding generate(String instructions, ScopedTool tool, InvestigationFixtures.Scenario fixture) {
        var reply = json.readValue(tool.call("{}", new ToolContext(tool.toolContext())), ScopedTool.Reply.class);
        if (fixture.invalidAgentPayload() != null && reply.reference() != null
                && reply.reference().type() == EvidenceType.GRAPH) {
            return json.treeToValue(fixture.invalidAgentPayload(), ModelFinding.class);
        }
        if (reply.status() == ScopedTool.Status.UNAVAILABLE) {
            return new ModelFinding(ModelFinding.MISSING, BigDecimal.ZERO, List.of());
        }
        return new ModelFinding(reply.observation(), BigDecimal.ONE, List.of(reply.reference()));
    }
}
