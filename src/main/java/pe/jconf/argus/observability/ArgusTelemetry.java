package pe.jconf.argus.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import pe.jconf.argus.domain.AgentExecutionStatus;
import pe.jconf.argus.domain.AgentType;
import pe.jconf.argus.investigation.AgentDiagnosticResult;
import pe.jconf.argus.investigation.InvestigationEvent;
import pe.jconf.argus.investigation.ResilienceEvent;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@Component
public final class ArgusTelemetry {
    static final int EVENT_LIMIT = 512;

    private final ObservationRegistry observations;
    private final MeterRegistry meters;
    private final Supplier<Tracer> tracer;
    private final ArrayDeque<Event> history = new ArrayDeque<>();
    private final LinkedHashMap<UUID, SpanContext> assessmentTraces = new LinkedHashMap<>();
    private final boolean retainEvents;

    @Autowired
    public ArgusTelemetry(ObservationRegistry observations, MeterRegistry meters, ObjectProvider<Tracer> tracer) {
        this(observations, meters, tracer::getIfUnique, true);
    }

    private ArgusTelemetry(ObservationRegistry observations, MeterRegistry meters, Supplier<Tracer> tracer,
                           boolean retainEvents) {
        this.observations = observations;
        this.meters = meters;
        this.tracer = tracer;
        this.retainEvents = retainEvents;
    }

    public static ArgusTelemetry noop() {
        return new ArgusTelemetry(ObservationRegistry.NOOP, null, () -> null, false);
    }

    public Observation operation(String name) {
        return Observation.createNotStarted(name, observations);
    }

    public void rememberAssessmentTrace(UUID assessmentId) {
        SpanContext context = Span.current().getSpanContext();
        if (!retainEvents || !context.isValid() || !context.isSampled()) {
            return;
        }
        synchronized (this) {
            if (assessmentTraces.size() == EVENT_LIMIT) {
                assessmentTraces.pollFirstEntry();
            }
            assessmentTraces.put(assessmentId, context);
        }
    }

    public void linkAssessment(UUID assessmentId) {
        SpanContext assessment;
        synchronized (this) {
            assessment = assessmentTraces.get(assessmentId);
        }
        if (assessment != null) {
            Span.current().addLink(assessment);
        }
    }

    public void simulatedTool(AgentType agent, Enum<?> result) {
        if (meters != null) {
            meters.counter("argus.tool.executions", "agent", agent.name(), "mode", "SIMULATED",
                    "status", result.name()).increment();
        }
    }

    private void agentOutcome(AgentType agent, AgentDiagnosticResult.Mode mode, AgentExecutionStatus status) {
        if (meters != null) {
            meters.counter("argus.agent.outcomes", "agent", agent.name(), "mode", mode.name(),
                    "status", status.name()).increment();
        }
    }

    public void lifecycle(InvestigationEvent event, AgentDiagnosticResult.Mode mode) {
        if (event.executionStatus() != null && event.agent() != null) {
            agentOutcome(event.agent(), mode, event.executionStatus());
        }
        record(event.investigationId(), event.agent(), event.type().name(), event.occurredAt(),
                event.executionStatus() == null ? null : event.executionStatus().name(),
                event.status().name(), null);
    }

    @EventListener
    public void resilience(ResilienceEvent event) {
        if (meters != null) {
            meters.counter("argus.resilience.events", "event", event.type().name()).increment();
        }
        Runnable capture = () -> record(event.investigationId(), event.agent(), event.type().name(),
                event.occurredAt(), executionStatus(event.outcome()), null, event.outcome());
        if (event.type() == ResilienceEvent.Type.FALLBACK) {
            operation("argus.investigation.fallback").lowCardinalityKeyValue("outcome", event.outcome())
                    .observe(capture);
        } else {
            capture.run();
        }
    }

    public synchronized List<Event> events(UUID investigationId) {
        return history.stream().filter(event -> investigationId.equals(event.investigationId())).toList();
    }

    private void record(UUID investigationId, AgentType agent, String type, Instant time, String executionStatus,
                        String investigationStatus, String outcome) {
        if (investigationId == null || !retainEvents) {
            return;
        }
        var configuredTracer = tracer.get();
        var span = configuredTracer == null ? null : configuredTracer.currentSpan();
        var event = new Event(investigationId, agent, type, time, executionStatus, investigationStatus, outcome,
                span == null ? null : span.context().traceId(), span == null ? null : span.context().spanId());
        synchronized (this) {
            if (history.size() == EVENT_LIMIT) {
                history.removeFirst();
            }
            history.addLast(event);
        }
    }

    private static String executionStatus(String outcome) {
        try {
            return AgentExecutionStatus.valueOf(outcome).name();
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return null;
        }
    }

    public record Event(UUID investigationId, AgentType agent, String eventType, Instant occurredAt,
                        String executionStatus, String investigationStatus, String outcome,
                        String traceId, String spanId) {
    }
}
