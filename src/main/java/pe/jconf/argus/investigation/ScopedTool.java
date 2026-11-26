package pe.jconf.argus.investigation;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import pe.jconf.argus.domain.EvidenceReference;
import pe.jconf.argus.domain.EvidenceType;
import pe.jconf.argus.domain.InvestigationContext;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public final class ScopedTool implements ToolCallback {
    static final int BUDGET = 2;
    private static final String SCOPE = "argusInvestigationScope";
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    private final ToolDefinition definition;
    private final InvestigationContext context;
    private final EvidenceType type;
    private final Supplier<Optional<Fact>> lookup;
    private final List<Trace> traces = new ArrayList<>();
    private final List<Reply> returned = new ArrayList<>();

    ScopedTool(String name, String description, InvestigationContext context, EvidenceType type,
               Supplier<Optional<Fact>> lookup) {
        definition = ToolDefinition.builder().name(name).description(description)
                .inputSchema("""
                        {"type":"object","properties":{},"additionalProperties":false}
                        """).build();
        this.context = context;
        this.type = type;
        this.lookup = lookup;
    }

    Map<String, Object> toolContext() {
        return Map.of(SCOPE, this);
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return definition;
    }

    @Override
    public String call(String arguments) {
        return call(arguments, null);
    }

    @Override
    public synchronized String call(String arguments, ToolContext suppliedContext) {
        if (traces.size() >= BUDGET) {
            return JSON.writeValueAsString(new Reply(Status.BUDGET_EXCEEDED, null, null));
        }
        if (suppliedContext == null || suppliedContext.getContext().get(SCOPE) != this) {
            return report(new Reply(Status.WRONG_SCOPE, null, null));
        }
        if (!emptyObject(arguments)) {
            return report(new Reply(Status.INVALID_ARGUMENTS, null, null));
        }

        var fact = lookup.get();
        if (fact.isEmpty()) {
            return report(new Reply(Status.UNAVAILABLE, null, null));
        }
        var source = fact.orElseThrow();
        var reference = new EvidenceReference(UUID.randomUUID(), source.recordId(), type);
        return report(new Reply(Status.AVAILABLE, reference, source.observation()));
    }

    private static boolean emptyObject(String arguments) {
        try {
            var json = JSON.readTree(arguments);
            return json.isObject() && json.isEmpty();
        } catch (JacksonException | IllegalArgumentException exception) {
            return false;
        }
    }

    private String report(Reply reply) {
        var encoded = JSON.writeValueAsString(reply);
        traces.add(new Trace(definition.name(), reply.status(), reply.reference()));
        if (reply.status() == Status.AVAILABLE) {
            returned.add(reply);
        }
        return encoded;
    }

    InvestigationContext investigation() {
        return context;
    }

    List<Trace> traces() {
        return List.copyOf(traces);
    }

    Optional<String> observationFor(EvidenceReference reference) {
        return returned.stream().filter(reply -> reply.reference().equals(reference))
                .map(Reply::observation).findFirst();
    }

    record Fact(String recordId, String observation) {
    }

    record Reply(Status status, EvidenceReference reference, String observation) {
    }

    public record Trace(String tool, Status status, EvidenceReference reference) {
    }

    public enum Status {
        AVAILABLE, UNAVAILABLE, INVALID_ARGUMENTS, WRONG_SCOPE, BUDGET_EXCEEDED
    }
}
