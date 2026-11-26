package pe.jconf.argus.investigation;

import org.springframework.core.io.ClassPathResource;
import pe.jconf.argus.domain.InvestigationContext;
import pe.jconf.argus.domain.RiskDecision;
import pe.jconf.argus.domain.Transaction;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class InvestigationFixtures {
    private final Map<String, Scenario> scenarios;
    private final Map<String, Integer> historicalCounts;

    InvestigationFixtures(JsonMapper mapper) throws IOException {
        var found = new HashMap<String, Scenario>();
        try (var input = new ClassPathResource("synthetic/scenarios.json").getInputStream()) {
            for (var json : mapper.readTree(input)) {
                var transaction = mapper.treeToValue(json.required("transaction"), Transaction.class);
                var snapshot = mapper.treeToValue(json.required("expectedDecision"), RiskDecision.class);
                found.put(json.required("scenario").stringValue(),
                        new Scenario(transaction, snapshot, json.get("invalidAgentPayload")));
            }
        }
        scenarios = Map.copyOf(found);
        var counts = new HashMap<String, Integer>();
        try (var input = new ClassPathResource("synthetic/behavioral-history.json").getInputStream()) {
            mapper.readTree(input).forEachEntry((id, count) -> counts.put(id, count.intValue()));
        }
        historicalCounts = Map.copyOf(counts);
    }

    Optional<InvestigationContext> open(String scenarioId) {
        return Optional.ofNullable(scenarios.get(scenarioId))
                .filter(scenario -> scenario.snapshot().decision() == pe.jconf.argus.domain.Decision.REVIEW)
                .map(scenario -> new InvestigationContext(UUID.randomUUID(), scenario.transaction(), scenario.snapshot(),
                        Instant.now().isBefore(scenario.snapshot().decidedAt())
                                ? scenario.snapshot().decidedAt() : Instant.now()));
    }

    Scenario require(InvestigationContext context) {
        return scenarios.values().stream()
                .filter(scenario -> scenario.transaction().equals(context.transaction()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown synthetic transaction."));
    }

    Optional<Integer> historicalCount(Transaction transaction) {
        return Optional.ofNullable(historicalCounts.get(transaction.transactionId()));
    }

    record Scenario(Transaction transaction, RiskDecision snapshot, JsonNode invalidAgentPayload) {
    }
}
