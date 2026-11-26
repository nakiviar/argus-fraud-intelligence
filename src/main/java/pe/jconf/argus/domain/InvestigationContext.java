package pe.jconf.argus.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record InvestigationContext(UUID investigationId, Transaction transaction,
                                   RiskDecision riskDecision, Instant openedAt) {
    public InvestigationContext {
        Objects.requireNonNull(investigationId, "investigationId");
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(riskDecision, "riskDecision");
        Objects.requireNonNull(openedAt, "openedAt");
        if (!transaction.transactionId().equals(riskDecision.transactionId())
                || riskDecision.decision() != Decision.REVIEW
                || openedAt.isBefore(riskDecision.decidedAt())) {
            throw new IllegalArgumentException("investigation requires a prior REVIEW of the same transaction");
        }
    }
}
