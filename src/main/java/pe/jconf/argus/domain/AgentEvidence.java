package pe.jconf.argus.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public record AgentEvidence(String observation, BigDecimal confidence,
                            List<EvidenceReference> citedReferences) {
    public AgentEvidence {
        DomainFields.requiredText(observation, "observation");
        Objects.requireNonNull(confidence, "confidence");
        if (confidence.signum() < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("self-reported confidence must be between 0 and 1");
        }
        citedReferences = List.copyOf(citedReferences);
    }
}
