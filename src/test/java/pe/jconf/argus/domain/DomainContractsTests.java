package pe.jconf.argus.domain;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.exc.ValueInstantiationException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DomainContractsTests {
    private static final Instant TIME = Instant.parse("2026-11-26T14:00:00Z");
    private static final UUID CASE_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID EXECUTION_ID = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final EvidenceReference REFERENCE = new EvidenceReference(
            UUID.fromString("00000000-0000-4000-8000-000000000003"), "SYN-GRAPH-1", EvidenceType.GRAPH);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void preservesExactMonetaryValueAndCurrencyThroughJson() {
        var transaction = transaction(new BigDecimal("12345678901234567890.1200"));
        var decoded = JSON.readValue(JSON.writeValueAsString(transaction), Transaction.class);
        assertEquals(transaction, decoded);
        assertEquals(4, decoded.amount().scale());
        assertEquals("PEN", decoded.currency().getCurrencyCode());
    }

    @Test
    void rejectsInvalidAmountsAndRequiredFields() {
        assertThrows(IllegalArgumentException.class, () -> transaction(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> transaction(new BigDecimal("-0.01")));
        assertThrows(NullPointerException.class, () -> transaction(null));
        assertThrows(NullPointerException.class,
                () -> new Transaction("SYN-TXN-1", "SYN-A", "SYN-B", BigDecimal.ONE, null, TIME));
        assertThrows(IllegalArgumentException.class,
                () -> new Transaction(" ", "SYN-A", "SYN-B", BigDecimal.ONE, Currency.getInstance("PEN"), TIME));
        assertThrows(IllegalArgumentException.class,
                () -> new AgentEvidence("Observation", new BigDecimal("1.01"), List.of(REFERENCE)));
    }

    @Test
    void takesDefensiveCopies() {
        var references = new ArrayList<>(List.of(REFERENCE));
        var evidence = new AgentEvidence("Synthetic observation", BigDecimal.ONE, references);
        var signals = new ArrayList<>(List.of(new RiskSignal(RiskSignal.Source.RULES, "DEMO", "Fixture")));
        var decision = new RiskDecision("SYN-TXN-1", Decision.REVIEW, "synthetic-v1", TIME, signals, Set.of());
        references.clear();
        signals.clear();
        assertEquals(List.of(REFERENCE), evidence.citedReferences());
        assertEquals(1, decision.signals().size());
        assertThrows(UnsupportedOperationException.class, () -> evidence.citedReferences().clear());
        assertThrows(UnsupportedOperationException.class, () -> decision.signals().clear());
    }

    @Test
    void marksUnavailableEvidenceWithoutAConfidenceOrAcceptedReferences() {
        var unavailable = result(AgentExecutionStatus.UNAVAILABLE, Optional.empty(), Set.of());
        assertEquals(AgentExecutionStatus.UNAVAILABLE, unavailable.status());
        assertTrue(unavailable.evidence().isEmpty());
        assertTrue(unavailable.verifiedReferences().isEmpty());
        assertEquals(unavailable, JSON.readValue(JSON.writeValueAsString(unavailable), AgentExecutionResult.class));
        assertThrows(IllegalArgumentException.class,
                () -> result(AgentExecutionStatus.SUCCESS, Optional.empty(), Set.of()));
    }

    @Test
    void requiresIndependentVerificationAndKeepsExecutionMetadataOutsideModelContent() {
        var evidence = new AgentEvidence("Synthetic observation", new BigDecimal("0.70"), List.of(REFERENCE));
        var modelJson = JSON.readTree(JSON.writeValueAsString(evidence));
        assertFalse(modelJson.has("executionId"));
        assertFalse(modelJson.has("investigationId"));
        assertFalse(modelJson.has("status"));
        assertFalse(modelJson.has("finishedAt"));
        assertFalse(modelJson.has("verifiedReferences"));
        assertThrows(IllegalArgumentException.class,
                () -> result(AgentExecutionStatus.SUCCESS, Optional.of(evidence), Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> result(AgentExecutionStatus.INVALID_RESPONSE, Optional.of(evidence), Set.of(REFERENCE)));

        var accepted = result(AgentExecutionStatus.SUCCESS, Optional.of(evidence), Set.of(REFERENCE));
        assertEquals(accepted, JSON.readValue(JSON.writeValueAsString(accepted), AgentExecutionResult.class));
    }

    @Test
    void reportRequiresReviewAndExplicitCoverageOfBothAgents() {
        var transaction = transaction(BigDecimal.ONE);
        var approved = new RiskDecision(transaction.transactionId(), Decision.APPROVE, "synthetic-v1",
                TIME, List.of(), Set.of());
        assertThrows(IllegalArgumentException.class, () -> new InvestigationContext(CASE_ID, transaction, approved, TIME));
        var review = new RiskDecision(transaction.transactionId(), Decision.REVIEW, "synthetic-v1",
                TIME, List.of(), Set.of(RiskSignal.Source.GRAPH_DATA));
        var context = new InvestigationContext(CASE_ID, transaction, review, TIME);
        var graph = result(AgentExecutionStatus.UNAVAILABLE, Optional.empty(), Set.of());
        var behavioral = new AgentExecutionResult(UUID.fromString("00000000-0000-4000-8000-000000000004"),
                CASE_ID, AgentType.BEHAVIORAL, AgentExecutionStatus.INVALID_RESPONSE,
                TIME, TIME, Optional.empty(), Set.of());

        assertThrows(IllegalArgumentException.class, () -> new InvestigationReport(context, TIME, List.of(graph)));
        var report = new InvestigationReport(context, TIME, List.of(graph, behavioral));
        assertEquals(report, JSON.readValue(JSON.writeValueAsString(report), InvestigationReport.class));
        assertEquals(Decision.REVIEW, report.context().riskDecision().decision());
    }

    @Test
    void reproducibleDatasetIncludesAllFiveDemonstrationCases() throws Exception {
        try (var stream = getClass().getResourceAsStream("/synthetic/scenarios.json")) {
            var fixtures = JSON.readValue(stream, Scenario[].class);
            assertEquals(5, fixtures.length);
            for (var fixture : fixtures) {
                assertEquals(fixture.transaction().transactionId(), fixture.expectedDecision().transactionId());
                assertTrue(fixture.transaction().transactionId().startsWith("SYN-"));
            }
            assertEquals(Decision.APPROVE, fixtures[0].expectedDecision().decision());
            assertEquals(Decision.REVIEW, fixtures[1].expectedDecision().decision());
            assertEquals(Decision.BLOCK, fixtures[2].expectedDecision().decision());
            assertEquals(Set.of(RiskSignal.Source.GRAPH_DATA), fixtures[3].expectedDecision().unavailableSources());
            assertEquals(AgentExecutionStatus.UNAVAILABLE, fixtures[3].expectedAgentStatuses().get(AgentType.GRAPH));
            assertEquals(AgentExecutionStatus.INVALID_RESPONSE, fixtures[4].expectedAgentStatuses().get(AgentType.GRAPH));
            assertThrows(ValueInstantiationException.class,
                    () -> JSON.treeToValue(fixtures[4].invalidAgentPayload(), AgentEvidence.class));
        }
    }

    private static Transaction transaction(BigDecimal amount) {
        return new Transaction("SYN-TXN-1", "SYN-A", "SYN-B", amount, Currency.getInstance("PEN"), TIME);
    }

    private static AgentExecutionResult result(AgentExecutionStatus status, Optional<AgentEvidence> evidence,
                                               Set<EvidenceReference> verifiedReferences) {
        return new AgentExecutionResult(EXECUTION_ID, CASE_ID, AgentType.GRAPH, status,
                TIME, TIME, evidence, verifiedReferences);
    }

    record Scenario(String scenario, Transaction transaction, RiskDecision expectedDecision,
                    Map<AgentType, AgentExecutionStatus> expectedAgentStatuses, JsonNode invalidAgentPayload) {
    }
}
