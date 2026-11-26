package pe.jconf.argus.investigation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.core.io.ClassPathResource;
import pe.jconf.argus.domain.Decision;
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
            // Direct deserialization preserves the monetary scale used by the provider's fixture lookup.
            for (var scenario : mapper.readValue(input, Scenario[].class)) {
                found.put(scenario.scenario(), scenario);
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
                .filter(scenario -> scenario.expectedDecision().decision() == Decision.REVIEW)
                .map(scenario -> {
                    var snapshot = scenario.expectedDecision();
                    var now = Instant.now();
                    var openedAt = now.isBefore(snapshot.decidedAt()) ? snapshot.decidedAt() : now;
                    return new InvestigationContext(UUID.randomUUID(), scenario.transaction(), snapshot, openedAt);
                });
    }

    InvestigationContext authorize(RiskDecision decision) {
        var transaction = scenarios.values().stream().map(Scenario::transaction)
                .filter(value -> value.transactionId().equals(decision.transactionId()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown synthetic transaction."));
        var now = Instant.now();
        return new InvestigationContext(UUID.randomUUID(), transaction, decision,
                now.isBefore(decision.decidedAt()) ? decision.decidedAt() : now);
    }

    Scenario require(InvestigationContext context) {
        return scenarios.values().stream()
                .filter(scenario -> scenario.transaction().equals(context.transaction()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown synthetic transaction."));
    }

    Optional<Integer> historicalCount(Transaction transaction) {
        return Optional.ofNullable(historicalCounts.get(transaction.transactionId()));
    }

    @JsonIgnoreProperties({"hotPath", "expectedAgentStatuses"})
    record Scenario(String scenario, Transaction transaction, RiskDecision expectedDecision,
                    JsonNode invalidAgentPayload) {
    }
}
