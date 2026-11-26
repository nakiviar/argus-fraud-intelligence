package pe.jconf.argus.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record RiskDecision(String transactionId, Decision decision, String policyVersion,
                           Instant decidedAt, List<RiskSignal> signals,
                           Set<RiskSignal.Source> unavailableSources) {
    public RiskDecision {
        DomainFields.requiredText(transactionId, "transactionId");
        Objects.requireNonNull(decision, "decision");
        DomainFields.requiredText(policyVersion, "policyVersion");
        Objects.requireNonNull(decidedAt, "decidedAt");
        signals = List.copyOf(signals);
        unavailableSources = Set.copyOf(unavailableSources);
    }
}
