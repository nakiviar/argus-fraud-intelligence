package pe.jconf.argus.assessment;

import pe.jconf.argus.domain.Transaction;

import java.util.Optional;

public interface GraphDataProvider {
    /** Returns empty when graph evidence is unavailable for the transaction. */
    Optional<ProviderEvidence> findEvidence(Transaction transaction);
}
