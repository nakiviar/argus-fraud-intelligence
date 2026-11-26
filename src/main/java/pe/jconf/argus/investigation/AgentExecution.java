package pe.jconf.argus.investigation;

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
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

final class AgentExecution {
    private final AgentModel model;
    private final InvestigationFixtures fixtures;
    private final EvidenceValidator validator = new EvidenceValidator();

    AgentExecution(AgentModel model, InvestigationFixtures fixtures) {
        this.model = model;
        this.fixtures = fixtures;
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
            finding = model.generate(instructions, tool, fixture);
            evidence = validator.validate(finding, tool);
            status = evidence.isPresent() ? AgentExecutionStatus.SUCCESS : AgentExecutionStatus.UNAVAILABLE;
            validation = evidence.isPresent() ? AgentDiagnosticResult.Validation.VERIFIED
                    : AgentDiagnosticResult.Validation.NO_EVIDENCE;
        } catch (JacksonException | EvidenceValidator.InvalidFinding exception) {
            status = AgentExecutionStatus.INVALID_RESPONSE;
            validation = AgentDiagnosticResult.Validation.REJECTED;
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
            if (current instanceof InterruptedIOException) {
                return true;
            }
        }
        return false;
    }
}
