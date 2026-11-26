package pe.jconf.argus.investigation;

import pe.jconf.argus.domain.AgentType;

import java.time.Instant;
import java.util.UUID;

public record ResilienceEvent(Type type, UUID investigationId, AgentType agent, String outcome, Instant occurredAt) {
    public enum Type {
        AGENT_TIMEOUT, CIRCUIT_TRANSITION, CIRCUIT_REJECTED, BULKHEAD_REJECTED, REMOTE_FAILURE,
        FALLBACK, LATE_RESULT_DISCARDED, EVIDENCE_VALIDATION_FAILED, INVESTIGATION_DEADLINE_EXCEEDED
    }
}
