package pe.jconf.argus.investigation;

import pe.jconf.argus.domain.AgentExecutionResult;
import pe.jconf.argus.domain.EvidenceReference;

import java.util.List;

public record AgentDiagnosticResult(Mode mode, AgentExecutionResult execution, Validation validation,
                                    List<ScopedTool.Trace> toolCalls, List<EvidenceReference> candidateReferences,
                                    long elapsedMillis) {
    public AgentDiagnosticResult {
        toolCalls = List.copyOf(toolCalls);
        candidateReferences = List.copyOf(candidateReferences);
    }

    public enum Mode {
        SIMULATED, AZURE
    }

    public enum Validation {
        VERIFIED, REJECTED, NO_EVIDENCE, NOT_RUN
    }
}
