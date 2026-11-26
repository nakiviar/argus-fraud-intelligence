package pe.jconf.argus.investigation;

import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.core.io.ClassPathResource;
import pe.jconf.argus.domain.AgentEvidence;
import pe.jconf.argus.domain.AgentExecutionResult;
import pe.jconf.argus.domain.AgentExecutionStatus;
import pe.jconf.argus.domain.AgentType;
import pe.jconf.argus.domain.EvidenceReference;
import tools.jackson.core.JacksonException;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

final class AgentExecution {
    private final AgentModel model;
    private final InvestigationFixtures fixtures;
    private final Duration agentTimeout;
    private final Consumer<ResilienceEvent> events;
    private final EvidenceValidator validator = new EvidenceValidator();

    AgentExecution(AgentModel model, InvestigationFixtures fixtures) {
        this(model, fixtures, Duration.ofSeconds(25), event -> { });
    }

    AgentExecution(AgentModel model, InvestigationFixtures fixtures, Duration agentTimeout,
                   Consumer<ResilienceEvent> events) {
        this.model = model;
        this.agentTimeout = agentTimeout;
        this.events = events;
        this.fixtures = fixtures;
    }

    ExecutionBudget standaloneBudget(UUID investigationId, AgentType type) {
        return new ExecutionBudget(agentTimeout, System::nanoTime, investigationId, events).agent(type, agentTimeout);
    }

    AgentDiagnosticResult run(AgentType type, String instructions, ScopedTool tool) {
        var context = tool.investigation();
        var fixture = fixtures.require(context);
        var executionId = UUID.randomUUID();
        var started = atLeast(context.openedAt());
        long startNanos = System.nanoTime();
        ModelFinding finding = null;
        Optional<AgentEvidence> evidence = Optional.empty();
        AgentExecutionStatus status;
        AgentDiagnosticResult.Validation validation;
        try {
            tool.budget().requireTime();
            finding = model.generate(instructions, tool, fixture);
            tool.budget().requireTime();
            var validated = validator.validate(finding, tool);
            tool.budget().requireTime();
            evidence = validated;
            status = evidence.isPresent() ? AgentExecutionStatus.SUCCESS : AgentExecutionStatus.UNAVAILABLE;
            validation = evidence.isPresent() ? AgentDiagnosticResult.Validation.VERIFIED
                    : AgentDiagnosticResult.Validation.NO_EVIDENCE;
        } catch (JacksonException | EvidenceValidator.InvalidFinding exception) {
            tool.budget().emit(ResilienceEvent.Type.EVIDENCE_VALIDATION_FAILED, "INVALID_RESPONSE");
            status = AgentExecutionStatus.INVALID_RESPONSE;
            validation = AgentDiagnosticResult.Validation.REJECTED;
        } catch (CallNotPermittedException | BulkheadFullException exception) {
            status = AgentExecutionStatus.UNAVAILABLE;
            validation = AgentDiagnosticResult.Validation.NOT_RUN;
        } catch (RuntimeException exception) {
            status = isTimeout(exception) ? AgentExecutionStatus.TIMEOUT : AgentExecutionStatus.ERROR;
            validation = AgentDiagnosticResult.Validation.NOT_RUN;
        }

        var verified = evidence.map(value -> Set.copyOf(value.citedReferences())).orElseGet(Set::of);
        var result = new AgentExecutionResult(executionId, context.investigationId(), type, status,
                started, atLeast(started), evidence, verified);
        return new AgentDiagnosticResult(model.mode(), result, validation, tool.traces(), safeCandidates(finding),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
    }

    private static List<EvidenceReference> safeCandidates(ModelFinding finding) {
        if (finding == null || finding.citedReferences() == null) {
            return List.of();
        }
        return finding.citedReferences().stream().filter(Objects::nonNull).limit(8)
                .map(reference -> reference.recordId().matches("synthetic-fixtures-v1/[a-z0-9-]{1,64}/(graph|prediction)")
                        ? reference : new EvidenceReference(reference.toolExecutionId(), "[redacted]", reference.type()))
                .toList();
    }

    static String instructions(String resource) throws IOException {
        return new ClassPathResource("prompts/" + resource).getContentAsString(StandardCharsets.UTF_8);
    }

    private static Instant atLeast(Instant minimum) {
        var now = Instant.now();
        return now.isBefore(minimum) ? minimum : now;
    }

    private static boolean isTimeout(Throwable failure) {
        for (var current = failure; current != null; current = current.getCause()) {
            if (current instanceof InterruptedIOException || current instanceof ExecutionBudget.Expired) {
                return true;
            }
        }
        return false;
    }
}
