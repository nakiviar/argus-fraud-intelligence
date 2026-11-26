package pe.jconf.argus.investigation;

import java.net.URI;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.jconf.argus.domain.AgentType;

@RestController
@Profile({"demo-local & !demo-azure", "demo-azure & !demo-local"})
class DashboardMetadataController {
    private final AgentModel model;
    private final Environment environment;

    DashboardMetadataController(AgentModel model, Environment environment) {
        this.model = model;
        this.environment = environment;
    }

    @GetMapping("/api/v1/dashboard/runtime")
    RuntimeMetadata runtime() {
        var simulated = model.mode() == AgentDiagnosticResult.Mode.SIMULATED;
        return new RuntimeMetadata(model.mode(), "NOT_RECORDED_IN_THIS_PROCESS",
                environment.getProperty("management.tracing.export.enabled", Boolean.class, false)
                        && environment.getProperty("management.tracing.export.otlp.enabled", Boolean.class, false),
                grafanaUrl(), simulated ? simulationControls() : null);
    }

    private SimulationControls simulationControls() {
        var failure = environment.getProperty("argus.investigation.simulated-failure", AgentType.class);
        var delayAgent = environment.getProperty("argus.investigation.simulated-delay-agent", AgentType.class);
        return new SimulationControls(failure, delayAgent, delayAgent == null ? null
                : environment.getProperty("argus.investigation.simulated-delay", "PT0S"));
    }

    private String grafanaUrl() {
        try {
            var uri = URI.create(environment.getProperty("argus.dashboard.grafana-url", ""));
            if (("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null && uri.getRawQuery() == null
                    && uri.getRawFragment() == null) {
                return uri.toASCIIString();
            }
        } catch (IllegalArgumentException ignored) {
            // An invalid optional dashboard link is omitted.
        }
        return null;
    }

    record RuntimeMetadata(AgentDiagnosticResult.Mode mode, String liveModelVerification, boolean traceExportEnabled,
                           String grafanaUrl, SimulationControls simulation) {}
    record SimulationControls(AgentType failureAgent, AgentType delayAgent, String delay) {}
}
