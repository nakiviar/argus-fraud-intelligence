package pe.jconf.argus.investigation;

interface AgentModel {
    AgentDiagnosticResult.Mode mode();

    ModelFinding generate(String instructions, ScopedTool tool, InvestigationFixtures.Scenario fixture);
}
