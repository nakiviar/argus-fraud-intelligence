package pe.jconf.argus.investigation;

import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.micrometer.observation.Observation;
import pe.jconf.argus.assessment.RiskAssessmentService;
import pe.jconf.argus.domain.AgentExecutionResult;
import pe.jconf.argus.domain.AgentExecutionStatus;
import pe.jconf.argus.domain.AgentType;
import pe.jconf.argus.domain.Decision;
import pe.jconf.argus.domain.InvestigationContext;
import pe.jconf.argus.observability.ArgusTelemetry;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

final class InvestigationOrchestrator implements AutoCloseable {
    private static final int MAX_IN_FLIGHT = 2;
    private static final int WORKERS = MAX_IN_FLIGHT * 2;
    private final InvestigationRepository repository;
    private final RiskAssessmentService assessments;
    private final InvestigationFixtures fixtures;
    private final Map<AgentType, InvestigationAgent> agents;
    private final EvidenceAggregator aggregator = new EvidenceAggregator();
    private final AgentDiagnosticResult.Mode mode;
    private final AgentType simulatedFailure;
    private final InvestigationBudgets budgets;
    private final Consumer<ResilienceEvent> events;
    private final AgentType delayAgent;
    private final Duration delay;
    private final ArgusTelemetry telemetry;
    private final Map<UUID, Observation> investigationObservations = new ConcurrentHashMap<>();
    private final Map<UUID, ExecutionBudget> active = new ConcurrentHashMap<>();
    private final Semaphore slots = new Semaphore(MAX_IN_FLIGHT);
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(WORKERS, WORKERS, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(WORKERS), Thread.ofPlatform().daemon().name("argus-agent-", 0).factory());
    private final ScheduledThreadPoolExecutor deadlines = new ScheduledThreadPoolExecutor(1,
            Thread.ofPlatform().daemon().name("argus-investigation-deadline-", 0).factory());
    private boolean closed;

    InvestigationOrchestrator(InvestigationRepository repository, RiskAssessmentService assessments,
                                InvestigationFixtures fixtures, List<InvestigationAgent> agents,
                                AgentDiagnosticResult.Mode mode, AgentType simulatedFailure, Duration deadline) {
        this(repository, assessments, fixtures, agents, mode, simulatedFailure,
                new InvestigationBudgets(deadline, deadline, Duration.ofMillis(1)), event -> { }, null, Duration.ZERO);
    }

    InvestigationOrchestrator(InvestigationRepository repository, RiskAssessmentService assessments,
                                InvestigationFixtures fixtures, List<InvestigationAgent> agents,
                                AgentDiagnosticResult.Mode mode, AgentType simulatedFailure,
                                InvestigationBudgets budgets, Consumer<ResilienceEvent> events,
                                AgentType delayAgent, Duration delay) {
        this(repository, assessments, fixtures, agents, mode, simulatedFailure, budgets, events, delayAgent, delay,
                ArgusTelemetry.noop());
    }

    InvestigationOrchestrator(InvestigationRepository repository, RiskAssessmentService assessments,
                                InvestigationFixtures fixtures, List<InvestigationAgent> agents,
                                AgentDiagnosticResult.Mode mode, AgentType simulatedFailure,
                                InvestigationBudgets budgets, Consumer<ResilienceEvent> events,
                                AgentType delayAgent, Duration delay, ArgusTelemetry telemetry) {
        this.telemetry = telemetry;
        this.repository = repository;
        this.assessments = assessments;
        this.fixtures = fixtures;
        this.mode = mode;
        this.simulatedFailure = mode == AgentDiagnosticResult.Mode.SIMULATED ? simulatedFailure : null;
        this.budgets = budgets;
        this.events = events;
        this.delayAgent = mode == AgentDiagnosticResult.Mode.SIMULATED ? delayAgent : null;
        this.delay = delay;
        var byType = new EnumMap<AgentType, InvestigationAgent>(AgentType.class);
        for (var agent : agents) {
            if (byType.putIfAbsent(agent.type(), agent) != null) {
                throw new IllegalArgumentException("Duplicate investigation agent.");
            }
        }
        if (byType.size() != AgentType.values().length || delay.isNegative()) {
            throw new IllegalArgumentException("Both agent types and a nonnegative simulation delay are required.");
        }
        this.agents = Map.copyOf(byType);
        deadlines.setRemoveOnCancelPolicy(true);
    }

    synchronized Submission start(UUID assessmentId) {
        return telemetry.operation("argus.investigation.create").lowCardinalityKeyValue("mode", mode.name())
                .observe(() -> registerAndStart(assessmentId));
    }

    private Submission registerAndStart(UUID assessmentId) {
        var existing = repository.findByAssessment(assessmentId);
        if (existing.isPresent()) {
            return new Submission(existing.orElseThrow(), false);
        }
        var assessment = assessments.find(assessmentId).orElseThrow(() -> new Rejected(Rejection.UNKNOWN_ASSESSMENT));
        if (assessment.riskDecision().decision() != Decision.REVIEW) {
            throw new Rejected(Rejection.ASSESSMENT_NOT_REVIEW);
        }
        if (closed || !slots.tryAcquire()) {
            throw new Rejected(closed ? Rejection.SHUTTING_DOWN : Rejection.CAPACITY);
        }
        var observation = telemetry.operation("argus.investigation").lowCardinalityKeyValue("mode", mode.name())
                .start();
        try (var scope = observation.openScope()) {
            telemetry.linkAssessment(assessmentId);
            var context = fixtures.authorize(assessment.riskDecision());
            var pending = repository.register(assessmentId, context, mode);
            var budget = new ExecutionBudget(budgets.global(), System::nanoTime, context.investigationId(), events);
            active.put(context.investigationId(), budget);
            investigationObservations.put(context.investigationId(), observation);
            repository.start(context.investigationId());
            fanOut(context, budget, observation);
            return new Submission(pending, true);
        } catch (RuntimeException exception) {
            observation.error(exception);
            observation.stop();
            slots.release();
            throw exception;
        }
    }

    Optional<InvestigationSnapshot> find(UUID id) {
        return repository.find(id);
    }

    private void fanOut(InvestigationContext context, ExecutionBudget global, Observation observation) {
        var work = java.util.Arrays.stream(AgentType.values())
                .map(type -> new AgentWork(type, global.agent(type, budgets.agent()))).toList();
        var timer = deadlines.schedule(() -> inScope(observation, () -> {
            global.close();
            if (fanIn(context, AgentExecutionStatus.TIMEOUT)) {
                global.emit(ResilienceEvent.Type.INVESTIGATION_DEADLINE_EXCEEDED, "TIMEOUT");
                global.emit(ResilienceEvent.Type.FALLBACK, "GLOBAL_DEADLINE");
            }
            work.forEach(AgentWork::interrupt);
        }), global.remainingNanos(), TimeUnit.NANOSECONDS);
        var tasks = new ArrayList<CompletableFuture<Void>>();
        var views = new ArrayList<CompletableFuture<Void>>();
        for (var job : work) {
            try {
                long waitNanos = Math.max(1, job.budget.remainingNanos());
                var actual = CompletableFuture.runAsync(() -> inScope(observation, () -> runAgent(context, job)), workers);
                tasks.add(actual);
                var limiter = TimeLimiter.of(TimeLimiterConfig.custom().timeoutDuration(Duration.ofMillis(1 + (waitNanos - 1) / 1_000_000))
                        .cancelRunningFuture(false).build());
                // The limiter owns a copy. Admission remains occupied until the actual work exits.
                var view = limiter.executeCompletionStage(deadlines, () -> actual.handle((value, error) -> (Void) null))
                        .whenComplete((ignored, failure) -> {
                    var cause = failure instanceof java.util.concurrent.CompletionException wrapped
                            ? wrapped.getCause() : failure;
                    if (cause instanceof java.util.concurrent.TimeoutException) {
                        inScope(observation, () -> {
                            job.budget.close();
                            publish(context, job, failure(context, job.type, AgentExecutionStatus.TIMEOUT), false);
                            job.interrupt();
                        });
                    }
                });
                views.add(view.toCompletableFuture());
            } catch (RejectedExecutionException exception) {
                job.budget.emit(ResilienceEvent.Type.BULKHEAD_REJECTED, "WORKER_REJECTED");
                publish(context, job, failed(context, job.type));
            }
        }
        var published = CompletableFuture.allOf(views.toArray(CompletableFuture[]::new)).whenComplete((ignored, failure) -> inScope(observation, () -> {
            try {
                timer.cancel(false);
                if (global.deadlineReached()) {
                    if (fanIn(context, AgentExecutionStatus.TIMEOUT)) {
                        global.emit(ResilienceEvent.Type.INVESTIGATION_DEADLINE_EXCEEDED, "TIMEOUT");
                        global.emit(ResilienceEvent.Type.FALLBACK, "GLOBAL_DEADLINE");
                    }
                } else {
                    fanIn(context, AgentExecutionStatus.ERROR);
                }
            } finally {
                global.close();
            }
        }));
        tasks.add(published);
        CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).whenComplete((ignored, failure) -> {
            active.remove(context.investigationId());
            investigationObservations.remove(context.investigationId());
            slots.release();
        });
    }

    private void runAgent(InvestigationContext context, AgentWork job) {
        var observation = telemetry.operation("argus.agent").lowCardinalityKeyValue("agent", job.type.name())
                .lowCardinalityKeyValue("mode", mode.name())
                .lowCardinalityKeyValue("token.usage", mode == AgentDiagnosticResult.Mode.SIMULATED
                        ? "unavailable" : "see.model")
                .start();
        try (var scope = observation.openScope()) {
            executeAgent(context, job, observation);
        } finally {
            observation.stop();
        }
    }

    private void executeAgent(InvestigationContext context, AgentWork job, Observation observation) {
        job.enter();
        try {
            if (!repository.agentStarted(context.investigationId(), job.type)) {
                return;
            }
            AgentExecutionResult result;
            try {
                job.budget.requireTime();
                if (job.type == simulatedFailure) {
                    throw new IllegalStateException("Controlled simulated agent failure.");
                }
                if (job.type == delayAgent) {
                    Thread.sleep(delay);
                }
                job.budget.requireTime();
                result = checkedResult(context, job.type, agents.get(job.type).investigate(context, job.budget));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                result = failure(context, job.type, AgentExecutionStatus.TIMEOUT);
            } catch (ExecutionBudget.Expired exception) {
                result = failure(context, job.type, AgentExecutionStatus.TIMEOUT);
            } catch (RuntimeException exception) {
                result = failed(context, job.type);
            }
            boolean expiredResult = job.budget.remainingNanos() == 0 && result.status() != AgentExecutionStatus.TIMEOUT;
            if (expiredResult) {
                job.budget.emit(ResilienceEvent.Type.LATE_RESULT_DISCARDED, result.status().name());
                result = failure(context, job.type, AgentExecutionStatus.TIMEOUT);
            }
            observation.lowCardinalityKeyValue("status", result.status().name())
                    .lowCardinalityKeyValue("late", Boolean.toString(job.budget.remainingNanos() == 0));
            publish(context, job, result, !expiredResult);
        } finally {
            job.exit();
        }
    }

    private void publish(InvestigationContext context, AgentWork job, AgentExecutionResult result) {
        publish(context, job, result, true);
    }

    private void publish(InvestigationContext context, AgentWork job, AgentExecutionResult result, boolean reportLate) {
        if (!repository.agentFinished(context.investigationId(), result)) {
            if (!reportLate) {
                return;
            }
            job.budget.emit(ResilienceEvent.Type.LATE_RESULT_DISCARDED, result.status().name());
        } else {
            if (result.status() == AgentExecutionStatus.SUCCESS) {
                return;
            }
            if (result.status() == AgentExecutionStatus.TIMEOUT) {
                job.budget.emit(ResilienceEvent.Type.AGENT_TIMEOUT, "TIMEOUT");
            }
            job.budget.emit(ResilienceEvent.Type.FALLBACK, result.status().name());
        }
    }

    private static final class AgentWork {
        private final AgentType type;
        private final ExecutionBudget budget;
        private Thread running;

        private AgentWork(AgentType type, ExecutionBudget budget) {
            this.type = type;
            this.budget = budget;
        }

        private synchronized void enter() {
            running = Thread.currentThread();
        }

        private synchronized void exit() {
            running = null;
        }

        private synchronized void interrupt() {
            if (running != null) {
                running.interrupt();
            }
        }
    }
    private AgentExecutionResult checkedResult(InvestigationContext context, AgentType type,
                                               AgentDiagnosticResult diagnostic) {
        var result = diagnostic.execution();
        if (diagnostic.mode() != mode || result.agent() != type
                || !context.investigationId().equals(result.investigationId())
                || result.startedAt().isBefore(context.openedAt())) {
            return failed(context, type);
        }
        if (result.status() == AgentExecutionStatus.SUCCESS && (diagnostic.validation()
                != AgentDiagnosticResult.Validation.VERIFIED
                || !result.verifiedReferences().equals(Set.copyOf(result.evidence().orElseThrow().citedReferences())))) {
            return failed(context, type);
        }
        return result;
    }

    private static AgentExecutionResult failed(InvestigationContext context, AgentType type) {
        return failure(context, type, AgentExecutionStatus.ERROR);
    }

    private static AgentExecutionResult failure(InvestigationContext context, AgentType type,
                                                AgentExecutionStatus status) {
        var now = Instant.now();
        var timestamp = now.isBefore(context.openedAt()) ? context.openedAt() : now;
        return new AgentExecutionResult(UUID.randomUUID(), context.investigationId(), type, status,
                timestamp, timestamp, Optional.empty(), Set.of());
    }

    private boolean fanIn(InvestigationContext context, AgentExecutionStatus missingStatus) {
        var observation = investigationObservations.getOrDefault(context.investigationId(), Observation.NOOP);
        try (var scope = observation.openScope()) {
            var results = repository.beginAggregation(context.investigationId(), missingStatus);
            results.ifPresent(values -> {
                var aggregate = telemetry.operation("argus.evidence.aggregation").lowCardinalityKeyValue("mode", mode.name())
                        .observe(() -> aggregator.aggregate(context, values, Instant.now()));
                telemetry.operation("argus.investigation.completion").lowCardinalityKeyValue("mode", mode.name())
                        .lowCardinalityKeyValue("status", aggregate.status().name())
                        .observe(() -> repository.complete(context.investigationId(), aggregate));
                observation.lowCardinalityKeyValue("status", aggregate.status().name()).stop();
            });
            return results.isPresent();
        }
    }

    private static void inScope(Observation observation, Runnable action) {
        try (var scope = observation.openScope()) {
            action.run();
        }
    }
    @Override
    public void close() {
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            active.values().forEach(ExecutionBudget::close);
            repository.running().forEach(context -> fanIn(context, AgentExecutionStatus.ERROR));
            deadlines.shutdownNow();
            workers.shutdownNow();
        }
        try {
            workers.awaitTermination(2, TimeUnit.SECONDS);
            deadlines.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    record Submission(InvestigationSnapshot snapshot, boolean created) {
    }

    enum Rejection {
        UNKNOWN_ASSESSMENT, ASSESSMENT_NOT_REVIEW, SHUTTING_DOWN, CAPACITY
    }

    static final class Rejected extends RuntimeException {
        private final Rejection reason;

        Rejected(Rejection reason) {
            super(reason.name());
            this.reason = reason;
        }

        Rejection reason() {
            return reason;
        }
    }
}
