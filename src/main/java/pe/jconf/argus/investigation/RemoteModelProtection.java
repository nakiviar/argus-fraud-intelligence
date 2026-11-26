package pe.jconf.argus.investigation;

import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

import pe.jconf.argus.domain.AgentType;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.time.Clock;
import java.time.InstantSource;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class RemoteModelProtection {
    private final Bulkhead bulkhead;
    private final Map<AgentType, Bulkhead> agentLimits;
    private final CircuitBreaker breaker;

    RemoteModelProtection(int concurrentCalls, Duration openInterval, Consumer<ResilienceEvent> events) {
        this(concurrentCalls, openInterval, events, monotonicClock());
    }

    RemoteModelProtection(int concurrentCalls, Duration openInterval, Consumer<ResilienceEvent> events, Clock clock) {
        bulkhead = Bulkhead.of("foundry-model", BulkheadConfig.custom()
                .maxConcurrentCalls(concurrentCalls).maxWaitDuration(Duration.ZERO).build());
        var limits = new EnumMap<AgentType, Bulkhead>(AgentType.class);
        for (var agent : AgentType.values()) {
            limits.put(agent, Bulkhead.of(agent.name(), BulkheadConfig.custom()
                    .maxConcurrentCalls(1).maxWaitDuration(Duration.ZERO).build()));
        }
        agentLimits = Map.copyOf(limits);
        breaker = CircuitBreaker.of("foundry-model", CircuitBreakerConfig.custom()
                .clock(clock).slidingWindowSize(4).minimumNumberOfCalls(4).failureRateThreshold(50)
                .waitDurationInOpenState(openInterval).permittedNumberOfCallsInHalfOpenState(2)
                .ignoreException(error -> !classify(error).countsTowardsBreaker()).build());
        breaker.getEventPublisher().onStateTransition(event -> events.accept(new ResilienceEvent(
                ResilienceEvent.Type.CIRCUIT_TRANSITION, null, null, event.getStateTransition().name(), Instant.now())));
    }

    <T> T execute(ExecutionBudget budget, Supplier<T> modelCall) {
        budget.requireTime();
        try {
            Supplier<T> protectedCall = () -> bulkhead.executeSupplier(() -> {
                budget.requireTime();
                return breaker.executeSupplier(() -> {
                    budget.requireTime();
                    return modelCall.get();
                });
            });
            return budget.agentType() == null ? protectedCall.get()
                    : agentLimits.get(budget.agentType()).executeSupplier(protectedCall);
        } catch (BulkheadFullException error) {
            budget.emit(ResilienceEvent.Type.BULKHEAD_REJECTED, "SATURATED");
            throw error;
        } catch (CallNotPermittedException error) {
            budget.emit(ResilienceEvent.Type.CIRCUIT_REJECTED, "OPEN_OR_PROBE_LIMIT");
            throw error;
        } catch (RuntimeException error) {
            var failure = classify(error);
            if (failure != Failure.APPLICATION) {
                budget.emit(ResilienceEvent.Type.REMOTE_FAILURE, failure.name());
            }
            throw error;
        }
    }

    CircuitBreaker circuitBreaker() {
        return breaker;
    }

    static Failure classify(Throwable error) {
        boolean network = false;
        for (var current = error; current != null; current = current.getCause()) {
            if (current instanceof OpenAIServiceException response) {
                int status = response.statusCode();
                if (status == 429) {
                    return Failure.RATE_LIMIT;
                }
                if (status == 408 || status >= 500 && status <= 599) {
                    return Failure.TRANSIENT_PROVIDER;
                }
                if (status == 401 || status == 403) {
                    return Failure.AUTHENTICATION;
                }
                return Failure.CONFIGURATION_OR_REQUEST;
            }
            if (current instanceof InterruptedIOException) {
                return Thread.currentThread().isInterrupted() ? Failure.APPLICATION : Failure.HTTP_TIMEOUT;
            }
            if (current instanceof OpenAIIoException || current instanceof IOException) {
                network = true;
            }
        }
        return network ? Failure.NETWORK : Failure.APPLICATION;
    }

    private static Clock monotonicClock() {
        var wallTime = Instant.now();
        long ticks = System.nanoTime();
        InstantSource source = () -> wallTime.plusNanos(System.nanoTime() - ticks);
        return source.withZone(ZoneOffset.UTC);
    }

    enum Failure {
        NETWORK, HTTP_TIMEOUT, TRANSIENT_PROVIDER, RATE_LIMIT, AUTHENTICATION, CONFIGURATION_OR_REQUEST, APPLICATION;

        boolean countsTowardsBreaker() {
            return this == NETWORK || this == HTTP_TIMEOUT || this == TRANSIENT_PROVIDER || this == RATE_LIMIT;
        }
    }
}
