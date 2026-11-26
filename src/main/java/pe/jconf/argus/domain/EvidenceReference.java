package pe.jconf.argus.domain;

import java.util.UUID;
import java.util.Objects;

public record EvidenceReference(UUID toolExecutionId, String recordId, EvidenceType type) {
    public EvidenceReference {
        Objects.requireNonNull(toolExecutionId, "toolExecutionId");
        DomainFields.requiredText(recordId, "recordId");
        Objects.requireNonNull(type, "type");
    }
}
