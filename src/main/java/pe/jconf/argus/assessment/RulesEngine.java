package pe.jconf.argus.assessment;

import pe.jconf.argus.domain.RiskSignal;

import java.util.Optional;

public final class RulesEngine {
    public Optional<RiskSignal> evaluate(boolean violatesMandatoryPolicy) {
        return violatesMandatoryPolicy
                ? Optional.of(new RiskSignal(RiskSignal.Source.RULES, "FICTIONAL_POLICY_VIOLATION",
                        "Fixture is marked as violating the fictional mandatory policy"))
                : Optional.empty();
    }
}
