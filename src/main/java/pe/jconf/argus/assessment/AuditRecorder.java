package pe.jconf.argus.assessment;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Process-local storage for the conference demo. */
public final class AuditRecorder {
    private final ConcurrentMap<UUID, Assessment> assessments = new ConcurrentHashMap<>();

    void record(Assessment assessment) {
        if (assessments.putIfAbsent(assessment.assessmentId(), assessment) != null) {
            throw new IllegalStateException("duplicate assessment identifier");
        }
    }

    public Optional<Assessment> find(UUID assessmentId) {
        return Optional.ofNullable(assessments.get(assessmentId));
    }
}
