package pe.jconf.argus.assessment;

import java.util.Objects;

/** One available result for a transaction, including the source record to which it was verified. */
public record ProviderEvidence(String transactionId, String recordId, boolean requiresReview) {
    public ProviderEvidence {
        if (Objects.requireNonNull(transactionId, "transactionId").isBlank()
                || Objects.requireNonNull(recordId, "recordId").isBlank()) {
            throw new IllegalArgumentException("evidence identifiers must be nonblank");
        }
    }
}
