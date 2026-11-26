package pe.jconf.argus.assessment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.jconf.argus.domain.Decision;
import pe.jconf.argus.domain.RiskDecision;
import pe.jconf.argus.domain.RiskSignal;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiskAssessmentTests {
    private static final Instant ASSESSED_AT = Instant.parse("2026-11-26T14:00:01Z");
    private static final Clock CLOCK = Clock.fixed(ASSESSED_AT, ZoneOffset.UTC);

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final RulesEngine rules = new RulesEngine();
    private final RiskDecisionEngine decisionEngine = new RiskDecisionEngine();
    private ScenarioCatalog catalog;
    private AuditRecorder audit;
    private RiskAssessmentService service;

    @BeforeEach
    void setUp() throws IOException {
        catalog = new ScenarioCatalog(mapper);
        audit = new AuditRecorder();
        service = serviceWith(new SyntheticGraphDataProvider(catalog), new SyntheticMlPredictor(catalog));
    }

    @Test
    void reproducesEveryFixtureOracleFromIndependentInputs() throws IOException {
        try (var stream = getClass().getResourceAsStream("/synthetic/scenarios.json")) {
            for (var fixture : mapper.readTree(stream)) {
                var scenarioId = fixture.path("scenario").stringValue();
                var expected = mapper.treeToValue(fixture.path("expectedDecision"), RiskDecision.class);
                assertEquals(expected, service.assess(scenarioId).orElseThrow().riskDecision(), scenarioId);
            }
        }
    }

    @Test
    void mandatoryViolationTakesPrecedenceOverReviewAndMissingEvidence() {
        var transaction = catalog.find("mandatory-policy").orElseThrow().transaction();
        var review = new ProviderEvidence(transaction.transactionId(), "SYN-GRAPH-REVIEW", true);
        var result = decisionEngine.decide(transaction, rules.evaluate(true), Optional.of(review), Optional.empty(),
                ASSESSED_AT);

        assertEquals(Decision.BLOCK, result.decision());
        assertEquals(Set.of(RiskSignal.Source.SYNTHETIC_ML), result.unavailableSources());
        assertEquals(List.of("FICTIONAL_POLICY_VIOLATION", "FIXTURE_REVIEW", "SYNTHETIC_ML_UNAVAILABLE"),
                decisionEngine.reasonCodes(result));
    }

    @Test
    void missingPredictionRequiresReviewWithNoReferenceForThatSource() {
        var result = serviceWith(new SyntheticGraphDataProvider(catalog), transaction -> Optional.empty())
                .assess("normal").orElseThrow();

        assertEquals(Decision.REVIEW, result.riskDecision().decision());
        assertEquals(Set.of(RiskSignal.Source.SYNTHETIC_ML), result.riskDecision().unavailableSources());
        assertEquals(List.of("SYNTHETIC_ML_UNAVAILABLE"), result.reasonCodes());
        assertFalse(result.verifiedEvidenceReferences().stream()
                .anyMatch(reference -> reference.source() == RiskSignal.Source.SYNTHETIC_ML));
    }

    @Test
    void auditStoresTheExactSnapshotAndEachAssessmentHasItsOwnIdentifier() {
        var first = service.assess("unavailable-evidence").orElseThrow();
        var second = service.assess("unavailable-evidence").orElseThrow();

        assertNotEquals(first.assessmentId(), second.assessmentId());
        assertEquals(first.riskDecision(), second.riskDecision());
        assertEquals(first.verifiedEvidenceReferences(), second.verifiedEvidenceReferences());
        assertEquals(first.reasonCodes(), second.reasonCodes());
        assertEquals(List.of("GRAPH_DATA_UNAVAILABLE"), first.reasonCodes());
        assertEquals(first, audit.find(first.assessmentId()).orElseThrow());
        assertEquals(second, audit.find(second.assessmentId()).orElseThrow());
    }

    @Test
    void rejectsProviderEvidenceFromAnotherTransaction() {
        var incorrect = new ProviderEvidence("SYN-TXN-OTHER", "SYN-GRAPH-OTHER", false);
        var service = serviceWith(transaction -> Optional.of(incorrect), new SyntheticMlPredictor(catalog));
        assertThrows(IllegalStateException.class, () -> service.assess("normal"));
    }

    @Test
    void unknownScenarioDoesNotInvokeProviders() {
        var service = serviceWith(transaction -> { throw new AssertionError("unexpected graph lookup"); },
                transaction -> { throw new AssertionError("unexpected prediction"); });
        assertTrue(service.assess("unknown-scenario").isEmpty());
    }

    private RiskAssessmentService serviceWith(GraphDataProvider graph, MlPredictor predictor) {
        return new RiskAssessmentService(catalog, rules, graph, predictor, decisionEngine, audit, CLOCK);
    }
}
