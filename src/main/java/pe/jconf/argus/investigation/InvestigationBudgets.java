package pe.jconf.argus.investigation;

import java.time.Duration;

record InvestigationBudgets(Duration global, Duration agent, Duration http) {
    InvestigationBudgets {
        if (http.compareTo(Duration.ofMillis(1)) < 0 || http.compareTo(agent) > 0 || agent.compareTo(global) > 0) {
            throw new IllegalArgumentException("Require 1 ms <= HTTP budget <= agent budget <= investigation deadline.");
        }
        // Fail during configuration if the monotonic clock cannot represent these budgets.
        global.toNanos();
    }
}
