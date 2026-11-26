package pe.jconf.argus.investigation;

import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class RemoteModelProtection {
    private final Bulkhead bulkhead;
    private final CircuitBreaker breaker;

    RemoteModelProtection(int concurrentCalls, Duration openInterval, Consumer<ResilienceEvent> events) {
        bulkhead = Bulkhead.of("foundry-model", BulkheadConfig.custom()
                .maxConcurrentCalls(concurrentCalls).maxWaitDuration(Duration.ZERO).build());
        breaker = CircuitBreaker.of("foundry-model", CircuitBreakerConfig.custom()
                .slidingWindowSize(4).minimumNumberOfCalls(4).failureRateThreshold(50)
                .waitDurationInOpenState(openInterval).permittedNumberOfCallsInHalfOpenState(2)
                .ignoreException(error -> !classify(error).countsTowardsBreaker()).build());
        breaker.getEventPublisher().onStateTransition(event -> events.accept(new ResilienceEvent(
                ResilienceEvent.Type.CIRCUIT_TRANSITION, null, null, event.getStateTransition().name(), Instant.now())));
    }

    <T> T execute(ExecutionBudget budget, Supplier<T> modelCall) {
        budget.requireTime();
        try {
            return bulkhead.executeSupplier(() -> {
                budget.requireTime();
                return breaker.executeSupplier(() -> {
                    budget.requireTime();
                    return modelCall.get();
                });
            });
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
            if (current instanceof OpenAIIoException || current instanceof IOException) {
                return Failure.NETWORK;
            }
        }
        return Failure.APPLICATION;
    }

    enum Failure {
        NETWORK, TRANSIENT_PROVIDER, RATE_LIMIT, AUTHENTICATION, CONFIGURATION_OR_REQUEST, APPLICATION;

        boolean countsTowardsBreaker() {
            return this == NETWORK || this == TRANSIENT_PROVIDER || this == RATE_LIMIT;
        }
    }
}
