package pe.jconf.argus.investigation;

import pe.jconf.argus.domain.EvidenceReference;

import java.math.BigDecimal;
import java.util.List;

/** Untrusted model content. Execution metadata is supplied separately by the application. */
record ModelFinding(String observation, BigDecimal confidence, List<EvidenceReference> citedReferences) {
    static final String MISSING = "No evidence was returned by the scoped tool.";
}
