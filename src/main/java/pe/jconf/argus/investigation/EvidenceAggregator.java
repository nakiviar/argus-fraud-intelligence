package pe.jconf.argus.investigation;

import pe.jconf.argus.domain.AgentExecutionResult;
import pe.jconf.argus.domain.AgentExecutionStatus;
import pe.jconf.argus.domain.EvidenceReference;
import pe.jconf.argus.domain.InvestigationContext;
import pe.jconf.argus.domain.InvestigationReport;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

final class EvidenceAggregator {
    Aggregation aggregate(InvestigationContext context, List<AgentExecutionResult> results, Instant aggregatedAt) {
        var ordered = results.stream().sorted(Comparator.comparing(AgentExecutionResult::agent)).toList();
        var generatedAt = ordered.stream().map(AgentExecutionResult::finishedAt)
                .reduce(aggregatedAt, (left, right) -> left.isAfter(right) ? left : right);
        var report = new InvestigationReport(context, generatedAt, ordered);
        var verified = ordered.stream().filter(result -> result.status() == AgentExecutionStatus.SUCCESS)
                .flatMap(result -> result.evidence().orElseThrow().citedReferences().stream())
                .distinct().sorted(Comparator.comparing(EvidenceReference::type)
                        .thenComparing(EvidenceReference::recordId).thenComparing(EvidenceReference::toolExecutionId))
                .toList();
        long successes = ordered.stream().filter(result -> result.status() == AgentExecutionStatus.SUCCESS).count();
        var status = successes == ordered.size() ? InvestigationStatus.COMPLETED
                : successes == 0 ? InvestigationStatus.FAILED : InvestigationStatus.PARTIAL;
        return new Aggregation(report, verified, status);
    }

    record Aggregation(InvestigationReport report, List<EvidenceReference> verified, InvestigationStatus status) {
    }
}
