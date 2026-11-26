package pe.jconf.argus.investigation;

import pe.jconf.argus.domain.AgentExecutionResult;
import pe.jconf.argus.domain.AgentExecutionStatus;
import pe.jconf.argus.domain.AgentType;
import pe.jconf.argus.domain.EvidenceReference;
import pe.jconf.argus.domain.InvestigationContext;
import pe.jconf.argus.domain.InvestigationReport;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static pe.jconf.argus.investigation.InvestigationEvent.Type.*;

final class InvestigationRepository {
    private final Map<UUID, Entry> byId = new HashMap<>();
    private final Map<UUID, UUID> byAssessment = new HashMap<>();

    synchronized InvestigationSnapshot register(UUID assessmentId, InvestigationContext context,
                                                 AgentDiagnosticResult.Mode mode) {
        if (byId.containsKey(context.investigationId()) || byAssessment.containsKey(assessmentId)) {
            throw new IllegalStateException("Investigation or assessment already registered.");
        }
        var entry = new Entry(assessmentId, context, mode);
        byId.put(context.investigationId(), entry);
        byAssessment.put(assessmentId, context.investigationId());
        entry.event(INVESTIGATION_CREATED, null, null, null);
        return entry.snapshot();
    }

    synchronized Optional<InvestigationSnapshot> find(UUID investigationId) {
        return Optional.ofNullable(byId.get(investigationId)).map(Entry::snapshot);
    }

    synchronized Optional<InvestigationSnapshot> findByAssessment(UUID assessmentId) {
        return Optional.ofNullable(byAssessment.get(assessmentId)).map(byId::get).map(Entry::snapshot);
    }

    synchronized void start(UUID id) {
        var entry = byId.get(id);
        entry.transition(InvestigationStatus.RUNNING);
        entry.event(INVESTIGATION_STARTED, null, null, null);
    }

    synchronized boolean agentStarted(UUID id, AgentType agent) {
        var entry = byId.get(id);
        if (entry.status != InvestigationStatus.RUNNING || entry.started.containsKey(agent)) {
            return false;
        }
        entry.started.put(agent, entry.event(AGENT_STARTED, agent, null, null));
        return true;
    }

    synchronized boolean agentFinished(UUID id, AgentExecutionResult result) {
        var entry = byId.get(id);
        if (entry.status == InvestigationStatus.RUNNING && !entry.results.containsKey(result.agent())) {
            entry.record(result);
            return true;
        }
        return false;
    }

    synchronized Optional<List<AgentExecutionResult>> beginAggregation(UUID id, AgentExecutionStatus missingStatus) {
        var entry = byId.get(id);
        if (entry.status != InvestigationStatus.RUNNING) {
            return Optional.empty();
        }
        for (var agent : AgentType.values()) {
            if (!entry.results.containsKey(agent)) {
                var finished = entry.now();
                var started = entry.started.getOrDefault(agent, finished);
                entry.record(new AgentExecutionResult(UUID.randomUUID(), id, agent, missingStatus, started, finished,
                        Optional.empty(), Set.of()));
            }
        }
        entry.transition(InvestigationStatus.AGGREGATING);
        return Optional.of(List.copyOf(entry.results.values()));
    }

    synchronized void complete(UUID id, EvidenceAggregator.Aggregation aggregation) {
        var entry = byId.get(id);
        if (entry.status != InvestigationStatus.AGGREGATING || !aggregation.status().isTerminal()
                || !entry.context.equals(aggregation.report().context())
                || !List.copyOf(entry.results.values()).equals(aggregation.report().results())) {
            throw new IllegalStateException("Invalid investigation completion.");
        }
        entry.report = aggregation.report();
        entry.references = aggregation.verified();
        entry.event(EVIDENCE_AGGREGATED, null, null, null);
        entry.transition(aggregation.status());
        entry.completedAt = entry.event(INVESTIGATION_COMPLETED, null, null, null);
    }

    synchronized List<InvestigationContext> running() {
        return byId.values().stream().filter(entry -> entry.status == InvestigationStatus.RUNNING)
                .map(entry -> entry.context).toList();
    }

    private static final class Entry {
        private final UUID assessmentId;
        private final InvestigationContext context;
        private final AgentDiagnosticResult.Mode mode;
        private final Map<AgentType, Instant> started = new EnumMap<>(AgentType.class);
        private final Map<AgentType, AgentExecutionResult> results = new EnumMap<>(AgentType.class);
        private final List<InvestigationEvent> events = new ArrayList<>();
        private InvestigationStatus status = InvestigationStatus.PENDING;
        private Instant completedAt;
        private InvestigationReport report;
        private List<EvidenceReference> references = List.of();

        private Entry(UUID assessmentId, InvestigationContext context, AgentDiagnosticResult.Mode mode) {
            this.assessmentId = assessmentId;
            this.context = context;
            this.mode = mode;
        }

        private void transition(InvestigationStatus next) {
            if (!status.canTransitionTo(next)) {
                throw new IllegalStateException("Invalid investigation status transition.");
            }
            status = next;
        }

        private void record(AgentExecutionResult result) {
            results.put(result.agent(), result);
            var type = result.status() == AgentExecutionStatus.SUCCESS ? AGENT_COMPLETED : AGENT_FAILED;
            event(type, result.agent(), result.executionId(), result.status());
        }

        private Instant now() {
            var floor = events.isEmpty() ? context.openedAt() : events.getLast().occurredAt();
            if (report != null && report.generatedAt().isAfter(floor)) {
                floor = report.generatedAt();
            }
            var observed = Instant.now();
            return observed.isBefore(floor) ? floor : observed;
        }

        private Instant event(InvestigationEvent.Type type, AgentType agent, UUID executionId,
                              AgentExecutionStatus executionStatus) {
            var instant = now();
            events.add(new InvestigationEvent(context.investigationId(), type, agent, executionId, executionStatus,
                    status, instant));
            return instant;
        }

        private InvestigationSnapshot snapshot() {
            return new InvestigationSnapshot(context.investigationId(), assessmentId, context.riskDecision(), mode,
                    status, context.openedAt(), completedAt, List.copyOf(results.values()), references, report, events);
        }
    }
}
