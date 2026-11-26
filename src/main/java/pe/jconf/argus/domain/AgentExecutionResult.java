package pe.jconf.argus.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record AgentExecutionResult(UUID executionId, UUID investigationId, AgentType agent,
                                   AgentExecutionStatus status, Instant startedAt, Instant finishedAt,
                                   Optional<AgentEvidence> evidence, Set<EvidenceReference> verifiedReferences) {
    public AgentExecutionResult {
        Objects.requireNonNull(executionId, "executionId");
        Objects.requireNonNull(investigationId, "investigationId");
        Objects.requireNonNull(agent, "agent");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(finishedAt, "finishedAt");
        Objects.requireNonNull(evidence, "evidence");
        verifiedReferences = Set.copyOf(verifiedReferences);
        if (finishedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("finishedAt precedes startedAt");
        }
        if (status == AgentExecutionStatus.SUCCESS) {
            var citations = evidence.orElseThrow(
                    () -> new IllegalArgumentException("success requires evidence")).citedReferences();
            if (citations.isEmpty() || !verifiedReferences.containsAll(citations)) {
                throw new IllegalArgumentException("success requires verified citations");
            }
        } else if (evidence.isPresent() || !verifiedReferences.isEmpty()) {
            throw new IllegalArgumentException("unsuccessful execution cannot publish accepted evidence");
        }
    }
}
