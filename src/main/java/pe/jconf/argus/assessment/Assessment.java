package pe.jconf.argus.assessment;

import pe.jconf.argus.domain.RiskDecision;
import pe.jconf.argus.domain.RiskSignal;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** The immutable assessment snapshot stored by the audit recorder. */
public record Assessment(UUID assessmentId, String policyId, RiskDecision riskDecision,
                         List<VerifiedReference> verifiedEvidenceReferences,
                         List<String> reasonCodes) {
    public Assessment {
        Objects.requireNonNull(assessmentId, "assessmentId");
        Objects.requireNonNull(policyId, "policyId");
        Objects.requireNonNull(riskDecision, "riskDecision");
        verifiedEvidenceReferences = List.copyOf(verifiedEvidenceReferences);
        reasonCodes = List.copyOf(reasonCodes);
    }

    public record VerifiedReference(RiskSignal.Source source, String recordId) {
        public VerifiedReference {
            Objects.requireNonNull(source, "source");
            if (Objects.requireNonNull(recordId, "recordId").isBlank()) {
                throw new IllegalArgumentException("recordId must be nonblank");
            }
        }
    }
}

