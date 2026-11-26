package pe.jconf.argus.domain;

public record RiskSignal(Source source, String code, String detail) {
    public enum Source {
        RULES, GRAPH_DATA, SYNTHETIC_ML
    }

    public RiskSignal {
        java.util.Objects.requireNonNull(source, "source");
        DomainFields.requiredText(code, "code");
        DomainFields.requiredText(detail, "detail");
    }
}
