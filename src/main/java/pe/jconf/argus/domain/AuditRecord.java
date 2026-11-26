package pe.jconf.argus.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AuditRecord(UUID auditId, String transactionId, Event event, Instant recordedAt) {
    public enum Event {
        RISK_DECIDED, INVESTIGATION_REPORTED
    }

    public AuditRecord {
        Objects.requireNonNull(auditId, "auditId");
        DomainFields.requiredText(transactionId, "transactionId");
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(recordedAt, "recordedAt");
    }
}
