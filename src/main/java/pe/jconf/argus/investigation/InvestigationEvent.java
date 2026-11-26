package pe.jconf.argus.investigation;

import pe.jconf.argus.domain.AgentExecutionStatus;
import pe.jconf.argus.domain.AgentType;

import java.time.Instant;
import java.util.UUID;

public record InvestigationEvent(UUID investigationId, Type type, AgentType agent, UUID executionId,
                                 AgentExecutionStatus executionStatus, InvestigationStatus status, Instant occurredAt) {
    public enum Type {
        INVESTIGATION_CREATED, INVESTIGATION_STARTED, AGENT_STARTED, AGENT_COMPLETED, AGENT_FAILED,
        EVIDENCE_AGGREGATED, INVESTIGATION_COMPLETED
    }
}
