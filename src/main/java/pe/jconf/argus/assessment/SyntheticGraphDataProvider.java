package pe.jconf.argus.assessment;

import pe.jconf.argus.domain.Transaction;

import java.util.Optional;

final class SyntheticGraphDataProvider implements GraphDataProvider {
    private final ScenarioCatalog catalog;

    SyntheticGraphDataProvider(ScenarioCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public Optional<ProviderEvidence> findEvidence(Transaction transaction) {
        return catalog.find(transaction).flatMap(scenario -> scenario.hotPath().graph().asEvidence(scenario, "graph"));
    }
}
