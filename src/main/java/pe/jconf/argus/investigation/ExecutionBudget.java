package pe.jconf.argus.investigation;

import pe.jconf.argus.domain.AgentType;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

final class ExecutionBudget {
    private final long started;
    private final long limit;
    private final LongSupplier clock;
    private final ExecutionBudget parent;
    private final UUID investigationId;
    private final AgentType agent;
    private final Consumer<ResilienceEvent> events;
    private volatile boolean closed;

    ExecutionBudget(Duration duration, LongSupplier clock, UUID investigationId, Consumer<ResilienceEvent> events) {
        this(duration, clock, investigationId, events, null, null);
    }

    private ExecutionBudget(Duration duration, LongSupplier clock, UUID investigationId,
                            Consumer<ResilienceEvent> events, ExecutionBudget parent, AgentType agent) {
        this.started = clock.getAsLong();
        this.limit = duration.toNanos();
        this.clock = clock;
        this.investigationId = investigationId;
        this.events = events;
        this.parent = parent;
        this.agent = agent;
    }

    static ExecutionBudget standalone(UUID investigationId) {
        return new ExecutionBudget(Duration.ofSeconds(25), System::nanoTime, investigationId, event -> { });
    }

    ExecutionBudget agent(AgentType type, Duration duration) {
        return new ExecutionBudget(duration, clock, investigationId, events, this, type);
    }

    long remainingNanos() {
        if (closed) {
            return 0;
        }
        long remaining = Math.max(0, limit - (clock.getAsLong() - started));
        return parent == null ? remaining : Math.min(remaining, parent.remainingNanos());
    }

    void requireTime() {
        if (remainingNanos() == 0) {
            throw new Expired();
        }
    }

    Duration requestTimeout(Duration maximum) {
        long remaining = Math.min(remainingNanos(), maximum.toNanos());
        // The transport counts milliseconds. A truncated zero timeout would disable its limit.
        if (remaining < 1_000_000) {
            throw new Expired();
        }
        return Duration.ofMillis(remaining / 1_000_000);
    }

    void close() {
        closed = true;
    }

    void emit(ResilienceEvent.Type type, String outcome) {
        events.accept(new ResilienceEvent(type, investigationId, agent, outcome, Instant.now()));
    }

    static final class Expired extends RuntimeException {
    }
}
