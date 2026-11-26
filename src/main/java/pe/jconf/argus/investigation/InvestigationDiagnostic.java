package pe.jconf.argus.investigation;

import org.springframework.boot.Banner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import pe.jconf.argus.domain.AgentExecutionStatus;
import pe.jconf.argus.domain.AgentType;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.Map;

public final class InvestigationDiagnostic {
    private static final String OPTION = "check-investigation-agent";

    private InvestigationDiagnostic() {
    }

    public static boolean requested(String[] args) {
        return Arrays.stream(args).anyMatch(argument ->
                argument.equals("--" + OPTION) || argument.startsWith("--" + OPTION + "="));
    }

    public static int run(Class<?> application, String[] args) {
        var json = JsonMapper.builder().build();
        var options = new DefaultApplicationArguments(args);
        var types = options.getOptionValues(OPTION);
        var scenarios = options.getOptionValues("scenario");
        if (types == null || types.size() != 1 || scenarios == null || scenarios.size() != 1) {
            return error(json, "Select one agent and one synthetic REVIEW scenario.");
        }
        var agentType = switch (types.getFirst()) {
            case "graph" -> AgentType.GRAPH;
            case "behavioral" -> AgentType.BEHAVIORAL;
            default -> null;
        };
        if (agentType == null) {
            return error(json, "Select graph or behavioral.");
        }

        try (var context = new SpringApplicationBuilder(application)
                .web(WebApplicationType.NONE).bannerMode(Banner.Mode.OFF).logStartupInfo(false)
                .properties("logging.level.root=OFF").run(args)) {
            var investigation = context.getBean(InvestigationFixtures.class).open(scenarios.getFirst());
            if (investigation.isEmpty()) {
                return error(json, "Select an existing synthetic REVIEW scenario.");
            }
            var selected = context.getBeansOfType(InvestigationAgent.class).values().stream()
                    .filter(agent -> agent.type() == agentType).findFirst().orElseThrow();
            var result = selected.investigate(investigation.orElseThrow());
            System.out.println(json.writeValueAsString(result));
            return result.execution().status() == AgentExecutionStatus.SUCCESS ? 0 : 1;
        } catch (RuntimeException exception) {
            return error(json, "Could not start the diagnostic. Check the profile and required configuration.");
        }
    }

    private static int error(JsonMapper json, String message) {
        System.out.println(json.writeValueAsString(Map.of("errorCategory", "CONFIGURATION", "message", message)));
        return 2;
    }
}
