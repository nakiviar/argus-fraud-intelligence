package pe.jconf.argus.investigation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import pe.jconf.argus.assessment.RiskAssessmentService;
import pe.jconf.argus.domain.AgentEvidence;
import pe.jconf.argus.domain.AgentExecutionResult;
import pe.jconf.argus.domain.AgentExecutionStatus;
import pe.jconf.argus.domain.AgentType;
import pe.jconf.argus.domain.EvidenceReference;
import pe.jconf.argus.domain.EvidenceType;
import pe.jconf.argus.domain.InvestigationContext;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("demo-local")
class InvestigationOrchestratorTests {
    private static final AgentDiagnosticResult.Mode MODE = AgentDiagnosticResult.Mode.SIMULATED;
    private final RiskAssessmentService assessments;
    private final InvestigationFixtures fixtures;

    @Autowired
    InvestigationOrchestratorTests(RiskAssessmentService assessments, InvestigationFixtures fixtures) {
        this.assessments = assessments;
        this.fixtures = fixtures;
    }

    @Test
    void bothWorkersReachTheBarrierBeforeEitherCanFinishAndDecisionIsPreserved() throws Exception {
        var started = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var assessment = assessments.assess("additional-review").orElseThrow();
        try (var orchestrator = orchestrator(Duration.ofSeconds(20),
                gated(AgentType.GRAPH, started, release), gated(AgentType.BEHAVIORAL, started, release))) {
            var submission = orchestrator.start(assessment.assessmentId());
            var id = submission.snapshot().investigationId();
            assertEquals(InvestigationStatus.PENDING, submission.snapshot().status());
            assertTrue(started.await(5, TimeUnit.SECONDS), "both workers must reach the barrier");
            var running = orchestrator.find(id).orElseThrow();
            assertEquals(InvestigationStatus.RUNNING, running.status());
            assertTrue(running.agentResults().isEmpty());
            assertFalse(orchestrator.start(assessment.assessmentId()).created());

            release.countDown();
            var completed = await(orchestrator, id);
            assertEquals(InvestigationStatus.COMPLETED, completed.status());
            assertEquals(assessment.riskDecision(), completed.originalRiskDecision());
            assertEquals(assessment.riskDecision(), completed.report().context().riskDecision());
            assertEquals(List.of(AgentType.GRAPH, AgentType.BEHAVIORAL),
                    completed.report().results().stream().map(AgentExecutionResult::agent).toList());
            assertEquals(2, completed.verifiedEvidenceReferences().size());
            assertEquals(InvestigationEvent.Type.INVESTIGATION_COMPLETED, completed.events().getLast().type());
        } finally {
            release.countDown();
        }
    }

    @Test
    void preservesOneSuccessfulAgentWhenTheOtherThrows() throws Exception {
        try (var orchestrator = orchestrator(Duration.ofSeconds(10), successAgent(AgentType.GRAPH),
                agent(AgentType.BEHAVIORAL, context -> { throw new IllegalStateException("private failure"); }))) {
            var result = startAndAwait(orchestrator);
            assertEquals(InvestigationStatus.PARTIAL, result.status());
            assertEquals(List.of(AgentExecutionStatus.SUCCESS, AgentExecutionStatus.ERROR),
                    result.agentResults().stream().map(AgentExecutionResult::status).toList());
            assertEquals(1, result.verifiedEvidenceReferences().size());
        }
    }

    @Test
    void timesOutUnfinishedAgentAndDiscardsItsLateCompletion() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var orchestrator = orchestrator(Duration.ofSeconds(2), successAgent(AgentType.GRAPH),
                gated(AgentType.BEHAVIORAL, entered, release))) {
            var result = startAndAwait(orchestrator);
            assertEquals(0, entered.getCount());
            assertEquals(InvestigationStatus.PARTIAL, result.status());
            assertEquals(AgentExecutionStatus.TIMEOUT, result.agentResults().getLast().status());
            release.countDown();
            orchestrator.close();
            assertEquals(result, orchestrator.find(result.investigationId()).orElseThrow());
        } finally {
            release.countDown();
        }
    }

    @Test
    void parallelDuplicateRequestsUseOneRunAndOtherAssessmentsHaveSeparateContexts() throws Exception {
        var assessment = assessments.assess("additional-review").orElseThrow();
        var second = assessments.assess("additional-review").orElseThrow();
        var launches = new AtomicInteger();
        var ready = new CountDownLatch(1);
        try (var orchestrator = orchestrator(Duration.ofSeconds(10), agent(AgentType.GRAPH, context -> {
            launches.incrementAndGet();
            return success(context, AgentType.GRAPH);
        }), successAgent(AgentType.BEHAVIORAL));
             var callers = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<java.util.concurrent.Future<InvestigationOrchestrator.Submission>>();
            for (int index = 0; index < 8; index++) {
                futures.add(callers.submit(() -> {
                    ready.await();
                    return orchestrator.start(assessment.assessmentId());
                }));
            }
            ready.countDown();
            var submissions = new ArrayList<InvestigationOrchestrator.Submission>();
            for (var future : futures) {
                submissions.add(future.get(5, TimeUnit.SECONDS));
            }
            var id = submissions.getFirst().snapshot().investigationId();
            assertEquals(1, submissions.stream().filter(InvestigationOrchestrator.Submission::created).count());
            assertTrue(submissions.stream().allMatch(value -> value.snapshot().investigationId().equals(id)));
            var firstReport = await(orchestrator, id);
            assertEquals(1, launches.get());

            var secondId = orchestrator.start(second.assessmentId()).snapshot().investigationId();
            var secondReport = await(orchestrator, secondId);
            assertNotEquals(id, secondId);
            assertTrue(secondReport.agentResults().stream().allMatch(result -> result.investigationId().equals(secondId)));
            assertTrue(java.util.Collections.disjoint(firstReport.verifiedEvidenceReferences(),
                    secondReport.verifiedEvidenceReferences()));
        }
    }

    @Test
    void capacityIsBoundedAndShutdownInterruptsWorkers() throws Exception {
        var started = new CountDownLatch(4);
        var interrupted = new CountDownLatch(4);
        var release = new CountDownLatch(1);
        var first = assessments.assess("additional-review").orElseThrow();
        var second = assessments.assess("additional-review").orElseThrow();
        var third = assessments.assess("additional-review").orElseThrow();
        Function<InvestigationContext, AgentDiagnosticResult> blocking = context -> {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException exception) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
            }
            return success(context, AgentType.GRAPH);
        };
        try (var orchestrator = orchestrator(Duration.ofSeconds(20), agent(AgentType.GRAPH, blocking),
                agent(AgentType.BEHAVIORAL, blocking))) {
            var id = orchestrator.start(first.assessmentId()).snapshot().investigationId();
            orchestrator.start(second.assessmentId());
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertEquals(InvestigationOrchestrator.Rejection.CAPACITY, assertThrows(
                    InvestigationOrchestrator.Rejected.class, () -> orchestrator.start(third.assessmentId())).reason());
            orchestrator.close();
            assertTrue(interrupted.await(5, TimeUnit.SECONDS));
            assertEquals(InvestigationStatus.FAILED, orchestrator.find(id).orElseThrow().status());
            assertEquals(InvestigationOrchestrator.Rejection.SHUTTING_DOWN, assertThrows(
                    InvestigationOrchestrator.Rejected.class, () -> orchestrator.start(third.assessmentId())).reason());
        } finally {
            release.countDown();
        }
    }

    @Test
    void repositoryRejectsInvalidTransitionsAndAggregatesDuplicatesDeterministically() {
        var repository = new InvestigationRepository();
        var context = fixtures.open("additional-review").orElseThrow();
        var id = context.investigationId();
        repository.register(UUID.randomUUID(), context, MODE);
        assertTrue(repository.beginAggregation(id, AgentExecutionStatus.ERROR).isEmpty());
        repository.start(id);
        assertThrows(IllegalStateException.class, () -> repository.start(id));

        var reference = new EvidenceReference(UUID.randomUUID(), "fixture-shared-record", EvidenceType.GRAPH);
        var graph = success(context, AgentType.GRAPH, reference).execution();
        var behavior = success(context, AgentType.BEHAVIORAL, reference).execution();
        repository.agentFinished(id, behavior);
        repository.agentFinished(id, graph);
        var results = repository.beginAggregation(id, AgentExecutionStatus.ERROR).orElseThrow();
        var aggregate = new EvidenceAggregator().aggregate(context, results.reversed(), Instant.now());
        repository.complete(id, aggregate);
        assertThrows(IllegalStateException.class, () -> repository.complete(id, aggregate));
        assertEquals(List.of(reference), repository.find(id).orElseThrow().verifiedEvidenceReferences());
        assertEquals(List.of(graph, behavior), aggregate.report().results());
    }

    private InvestigationSnapshot startAndAwait(InvestigationOrchestrator orchestrator) throws Exception {
        var assessment = assessments.assess("additional-review").orElseThrow();
        return await(orchestrator, orchestrator.start(assessment.assessmentId()).snapshot().investigationId());
    }

    private static InvestigationSnapshot await(InvestigationOrchestrator orchestrator, UUID id) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        do {
            var snapshot = orchestrator.find(id).orElseThrow();
            if (snapshot.status().isTerminal()) {
                return snapshot;
            }
            Thread.sleep(10);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Investigation did not reach a terminal status.");
    }

    private InvestigationOrchestrator orchestrator(Duration deadline, InvestigationAgent... agents) {
        return new InvestigationOrchestrator(new InvestigationRepository(), assessments, fixtures, List.of(agents),
                MODE, null, deadline);
    }

    private static InvestigationAgent gated(AgentType type, CountDownLatch started, CountDownLatch release) {
        return agent(type, context -> {
            started.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Barrier was not released.");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted.", exception);
            }
            return success(context, type);
        });
    }

    private static InvestigationAgent successAgent(AgentType type) {
        return agent(type, context -> success(context, type));
    }

    private static InvestigationAgent agent(AgentType type, Function<InvestigationContext, AgentDiagnosticResult> run) {
        return new InvestigationAgent() {
            @Override
            public AgentType type() {
                return type;
            }

            @Override
            public AgentDiagnosticResult investigate(InvestigationContext context) {
                return run.apply(context);
            }
        };
    }

    private static AgentDiagnosticResult success(InvestigationContext context, AgentType agent) {
        var type = agent == AgentType.GRAPH ? EvidenceType.GRAPH : EvidenceType.BEHAVIORAL;
        return success(context, agent, new EvidenceReference(UUID.randomUUID(), "fixture-record", type));
    }

    private static AgentDiagnosticResult success(InvestigationContext context, AgentType agent,
                                                 EvidenceReference reference) {
        var evidence = new AgentEvidence("Verified fixture observation.", BigDecimal.ONE, List.of(reference));
        var now = context.openedAt();
        var execution = new AgentExecutionResult(UUID.randomUUID(), context.investigationId(), agent,
                AgentExecutionStatus.SUCCESS, now, now, Optional.of(evidence), Set.of(reference));
        return new AgentDiagnosticResult(MODE, execution, AgentDiagnosticResult.Validation.VERIFIED, List.of(),
                List.of(reference), 0);
    }
}
