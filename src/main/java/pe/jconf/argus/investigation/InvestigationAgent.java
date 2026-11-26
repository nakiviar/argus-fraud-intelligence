package pe.jconf.argus.investigation;

import pe.jconf.argus.domain.AgentType;
import pe.jconf.argus.domain.InvestigationContext;

public interface InvestigationAgent {
    AgentType type();

    AgentDiagnosticResult investigate(InvestigationContext context);
}
