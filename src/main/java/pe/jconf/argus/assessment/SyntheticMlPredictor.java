package pe.jconf.argus.assessment;

import pe.jconf.argus.domain.Transaction;

import java.util.Optional;

/** Reads predefined fixture outcomes for the demo. */
final class SyntheticMlPredictor implements MlPredictor {
    private final ScenarioCatalog catalog;

    SyntheticMlPredictor(ScenarioCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public Optional<ProviderEvidence> predict(Transaction transaction) {
        return catalog.find(transaction)
                .flatMap(scenario -> scenario.hotPath().prediction().asEvidence(scenario, "prediction"));
    }
}
