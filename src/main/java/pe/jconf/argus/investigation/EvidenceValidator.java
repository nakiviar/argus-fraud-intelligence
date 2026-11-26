package pe.jconf.argus.investigation;

import pe.jconf.argus.domain.AgentEvidence;

import java.math.BigDecimal;
import java.util.Optional;

final class EvidenceValidator {
    Optional<AgentEvidence> validate(ModelFinding finding, ScopedTool tool) {
        if (finding == null || finding.observation() == null || finding.confidence() == null
                || finding.confidence().signum() < 0 || finding.confidence().compareTo(BigDecimal.ONE) > 0
                || finding.citedReferences() == null || tool.traces().stream().anyMatch(trace ->
                        trace.status() == ScopedTool.Status.INVALID_ARGUMENTS
                                || trace.status() == ScopedTool.Status.WRONG_SCOPE)) {
            throw new InvalidFinding();
        }

        var citations = finding.citedReferences();
        if (citations.isEmpty() && ModelFinding.MISSING.equals(finding.observation())
                && finding.confidence().signum() == 0 && !tool.traces().isEmpty()
                && tool.traces().stream().allMatch(trace -> trace.status() == ScopedTool.Status.UNAVAILABLE)) {
            return Optional.empty();
        }
        if (citations.size() != 1 || citations.getFirst() == null) {
            throw new InvalidFinding();
        }
        var observation = tool.observationFor(citations.getFirst()).orElseThrow(InvalidFinding::new);
        if (!observation.equals(finding.observation())) {
            throw new InvalidFinding();
        }
        return Optional.of(new AgentEvidence(observation, finding.confidence(), citations));
    }

    static final class InvalidFinding extends RuntimeException {
    }
}
