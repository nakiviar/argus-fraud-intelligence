package pe.jconf.argus.investigation;

import pe.jconf.argus.domain.AgentExecutionResult;
import pe.jconf.argus.domain.EvidenceReference;
import pe.jconf.argus.domain.InvestigationReport;
import pe.jconf.argus.domain.RiskDecision;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InvestigationSnapshot(UUID investigationId, UUID assessmentId, RiskDecision originalRiskDecision,
                                    AgentDiagnosticResult.Mode mode, InvestigationStatus status, Instant createdAt,
                                    Instant completedAt, List<AgentExecutionResult> agentResults,
                                    List<EvidenceReference> verifiedEvidenceReferences, InvestigationReport report,
                                    List<InvestigationEvent> events) {
    public InvestigationSnapshot {
        agentResults = List.copyOf(agentResults);
        verifiedEvidenceReferences = List.copyOf(verifiedEvidenceReferences);
        events = List.copyOf(events);
    }
}
