package pe.jconf.argus.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;

public record Transaction(String transactionId, String sourceAccountId, String destinationAccountId,
                          BigDecimal amount, Currency currency, Instant occurredAt) {
    public Transaction {
        DomainFields.requiredText(transactionId, "transactionId");
        DomainFields.requiredText(sourceAccountId, "sourceAccountId");
        DomainFields.requiredText(destinationAccountId, "destinationAccountId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }
}
