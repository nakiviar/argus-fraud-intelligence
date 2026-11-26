package pe.jconf.argus.assessment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.core.io.ClassPathResource;
import pe.jconf.argus.domain.Transaction;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class ScenarioCatalog {
    private final Map<String, Scenario> scenarios;
    private final Map<String, Scenario> scenariosByTransaction;

    ScenarioCatalog(JsonMapper mapper) throws IOException {
        var byScenario = new LinkedHashMap<String, Scenario>();
        var byTransaction = new LinkedHashMap<String, Scenario>();
        try (var fixture = new ClassPathResource("synthetic/scenarios.json").getInputStream()) {
            for (var scenario : mapper.readValue(fixture, Scenario[].class)) {
                if (byScenario.putIfAbsent(scenario.scenario(), scenario) != null
                        || byTransaction.putIfAbsent(scenario.transaction().transactionId(), scenario) != null) {
                    throw new IllegalArgumentException("duplicate synthetic scenario or transaction identifier");
                }
            }
        }
        scenarios = Collections.unmodifiableMap(byScenario);
        scenariosByTransaction = Map.copyOf(byTransaction);
    }

    Map<String, Scenario> scenarios() {
        return scenarios;
    }

    Optional<Scenario> find(String scenarioId) {
        return Optional.ofNullable(scenarios.get(scenarioId));
    }

    Optional<Scenario> find(Transaction transaction) {
        return Optional.ofNullable(scenariosByTransaction.get(transaction.transactionId()))
                .filter(scenario -> scenario.transaction().equals(transaction));
    }

    @JsonIgnoreProperties({"expectedDecision", "expectedAgentStatuses", "invalidAgentPayload"})
    record Scenario(String scenario, Transaction transaction, Inputs hotPath) {
        Scenario {
            if (scenario == null || !scenario.matches("[a-z][a-z0-9-]{0,63}")) {
                throw new IllegalArgumentException("invalid synthetic scenario identifier");
            }
            Objects.requireNonNull(transaction, "transaction");
            Objects.requireNonNull(hotPath, "hotPath");
            if (!transaction.transactionId().startsWith("SYN-TXN-")
                    || !transaction.sourceAccountId().startsWith("SYN-ACCOUNT-")
                    || !transaction.destinationAccountId().startsWith("SYN-ACCOUNT-")) {
                throw new IllegalArgumentException("scenario requires synthetic identifiers");
            }
        }

        String referenceId(String source) {
            return "synthetic-fixtures-v1/" + scenario + "/" + source;
        }
    }

    record Inputs(Boolean violatesMandatoryPolicy, FixtureOutcome graph, FixtureOutcome prediction) {
        Inputs {
            Objects.requireNonNull(violatesMandatoryPolicy, "violatesMandatoryPolicy");
            Objects.requireNonNull(graph, "graph");
            Objects.requireNonNull(prediction, "prediction");
        }
    }

    enum FixtureOutcome {
        CLEAR, REVIEW, UNAVAILABLE;

        Optional<ProviderEvidence> asEvidence(Scenario scenario, String source) {
            return this == UNAVAILABLE ? Optional.empty()
                    : Optional.of(new ProviderEvidence(scenario.transaction().transactionId(),
                            scenario.referenceId(source), this == REVIEW));
        }
    }
}
