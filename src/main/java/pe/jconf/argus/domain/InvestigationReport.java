package pe.jconf.argus.domain;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

public record InvestigationReport(InvestigationContext context, Instant generatedAt,
                                  List<AgentExecutionResult> results) {
    public InvestigationReport {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(generatedAt, "generatedAt");
        results = List.copyOf(results);
        var agents = EnumSet.noneOf(AgentType.class);
        for (var result : results) {
            if (!result.investigationId().equals(context.investigationId())
                    || result.startedAt().isBefore(context.openedAt())
                    || result.finishedAt().isAfter(generatedAt)
                    || !agents.add(result.agent())) {
                throw new IllegalArgumentException("report requires one correlated result per agent within its time window");
            }
        }
        if (!agents.equals(EnumSet.allOf(AgentType.class))) {
            throw new IllegalArgumentException("report must explicitly account for all agents");
        }
    }
}
