package pe.jconf.argus.ai;

import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.env.Profiles;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicReference;

public final class ConnectivityDiagnostic {
    private static final String SWITCH = "--check-model-connectivity";

    private ConnectivityDiagnostic() {
    }

    public static boolean requested(String[] args) {
        return Arrays.asList(args).contains(SWITCH);
    }

    public static int run(Class<?> application, String[] args) {
        var mapper = JsonMapper.builder().build();
        var mode = new AtomicReference<ModelConnectivityResult.Mode>();
        try (var context = new SpringApplicationBuilder(application)
                .web(WebApplicationType.NONE).bannerMode(Banner.Mode.OFF).logStartupInfo(false)
                .properties("logging.level.root=OFF")
                .initializers(initialized -> {
                    var environment = initialized.getEnvironment();
                    if (environment.acceptsProfiles(Profiles.of("demo-azure & !demo-local"))) {
                        mode.set(ModelConnectivityResult.Mode.AZURE);
                    } else if (environment.acceptsProfiles(Profiles.of("demo-local & !demo-azure"))) {
                        mode.set(ModelConnectivityResult.Mode.SIMULATED);
                    }
                }).run(args)) {
            var service = context.getBeanProvider(ModelConnectivityService.class).getIfUnique();
            if (service == null) {
                printConfigurationError(mapper, mode.get(), "Select a supported connectivity profile.");
                return 2;
            }
            var result = service.check();
            System.out.println(mapper.writeValueAsString(result));
            return result.connected() ? 0 : 1;
        } catch (RuntimeException exception) {
            printConfigurationError(mapper, mode.get(), configurationMessage(exception));
            return 2;
        }
    }

    private static String configurationMessage(Throwable failure) {
        for (var current = failure; current != null; current = current.getCause()) {
            if (current instanceof FoundrySettings.ConfigurationException configuration) {
                return configuration.getMessage();
            }
        }
        return "Diagnostic startup failed. Check the profile and diagnostic settings.";
    }

    private static void printConfigurationError(JsonMapper mapper, ModelConnectivityResult.Mode mode, String message) {
        var output = new LinkedHashMap<String, Object>();
        output.put("mode", mode);
        output.put("connected", false);
        output.put("errorCategory", "CONFIGURATION");
        output.put("message", message);
        System.out.println(mapper.writeValueAsString(output));
    }
}
