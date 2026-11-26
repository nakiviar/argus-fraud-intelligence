package pe.jconf.argus.assessment.web;

import pe.jconf.argus.assessment.Assessment;
import pe.jconf.argus.domain.Decision;
import pe.jconf.argus.domain.RiskSignal;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AssessmentResponse(UUID assessmentId, String transactionId, Decision decision, String policyId,
                                 String policyVersion,
                                 List<Assessment.VerifiedReference> verifiedEvidenceReferences, List<String> reasonCodes,
                                 Map<RiskSignal.Source, EvidenceAvailability> evidenceAvailability,
                                 Instant assessedAt, boolean investigationRequired) {
    public enum EvidenceAvailability {
        AVAILABLE, UNAVAILABLE
    }

    static AssessmentResponse from(Assessment assessment) {
        var risk = assessment.riskDecision();
        var availability = new EnumMap<RiskSignal.Source, EvidenceAvailability>(RiskSignal.Source.class);
        for (var source : RiskSignal.Source.values()) {
            availability.put(source, risk.unavailableSources().contains(source)
                    ? EvidenceAvailability.UNAVAILABLE : EvidenceAvailability.AVAILABLE);
        }
        return new AssessmentResponse(assessment.assessmentId(), risk.transactionId(), risk.decision(),
                assessment.policyId(), risk.policyVersion(), assessment.verifiedEvidenceReferences(),
                assessment.reasonCodes(),
                Map.copyOf(availability), risk.decidedAt(), risk.decision() == Decision.REVIEW);
    }
}

