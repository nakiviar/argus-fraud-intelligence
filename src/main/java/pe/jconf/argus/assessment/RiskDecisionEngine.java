package pe.jconf.argus.assessment;

import pe.jconf.argus.domain.Decision;
import pe.jconf.argus.domain.RiskDecision;
import pe.jconf.argus.domain.RiskSignal;
import pe.jconf.argus.domain.Transaction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static pe.jconf.argus.domain.RiskSignal.Source.GRAPH_DATA;
import static pe.jconf.argus.domain.RiskSignal.Source.SYNTHETIC_ML;

public final class RiskDecisionEngine {
    public static final String POLICY_ID = "synthetic-risk";
    public static final String POLICY_VERSION = "synthetic-v1";

    RiskDecision decide(Transaction transaction, Optional<RiskSignal> mandatoryViolation,
                        Optional<ProviderEvidence> graph, Optional<ProviderEvidence> prediction,
                        Instant decidedAt) {
        var signals = new ArrayList<RiskSignal>();
        mandatoryViolation.ifPresent(signals::add);
        graph.filter(ProviderEvidence::requiresReview).ifPresent(evidence -> signals.add(
                new RiskSignal(GRAPH_DATA, "FIXTURE_REVIEW", "Synthetic graph fixture calls for review")));
        prediction.filter(ProviderEvidence::requiresReview).ifPresent(evidence -> signals.add(
                new RiskSignal(SYNTHETIC_ML, "FIXTURE_REVIEW", "Synthetic predictor fixture calls for review")));

        var unavailable = EnumSet.noneOf(RiskSignal.Source.class);
        if (graph.isEmpty()) {
            unavailable.add(GRAPH_DATA);
        }
        if (prediction.isEmpty()) {
            unavailable.add(SYNTHETIC_ML);
        }

        Decision decision = Decision.APPROVE;
        if (mandatoryViolation.isPresent()) {
            decision = Decision.BLOCK;
        } else if (!signals.isEmpty() || !unavailable.isEmpty()) {
            decision = Decision.REVIEW;
        }
        return new RiskDecision(transaction.transactionId(), decision, POLICY_VERSION, decidedAt, signals, unavailable);
    }
    List<String> reasonCodes(RiskDecision decision) {
        var reasons = new LinkedHashSet<String>();
        decision.signals().stream().map(RiskSignal::code).forEach(reasons::add);
        for (var source : RiskSignal.Source.values()) {
            if (decision.unavailableSources().contains(source)) {
                reasons.add(source.name() + "_UNAVAILABLE");
            }
        }
        return reasons.isEmpty() ? List.of("NO_POLICY_FINDINGS") : List.copyOf(reasons);
    }
}
