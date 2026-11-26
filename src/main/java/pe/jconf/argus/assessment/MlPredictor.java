package pe.jconf.argus.assessment;

import pe.jconf.argus.domain.Transaction;

import java.util.Optional;

public interface MlPredictor {
    /** Returns empty when a prediction is unavailable for the transaction. */
    Optional<ProviderEvidence> predict(Transaction transaction);
}
