package pe.jconf.argus.ai;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import pe.jconf.argus.ArgusApplication;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectivityProfilesTests {
    @Test
    void localIsExplicitlySimulatedAndCreatesNoProviderClients() {
        try (var context = application().run("--spring.profiles.active=demo-local",
                "--spring.ai.openai.base-url=https://unused.openai.azure.com")) {
            assertTrue(context.getBeansOfType(ChatClient.class).isEmpty());
            assertTrue(context.getBeansOfType(ChatClient.Builder.class).isEmpty());
            assertTrue(context.getBeansOfType(ChatModel.class).isEmpty());
            assertTrue(context.getBeansOfType(SpringAiOpenAiHttpClient.class).isEmpty());
            var simulation = context.getBean(ModelConnectivityService.class).check();
            assertEquals(ModelConnectivityResult.Mode.SIMULATED, simulation.mode());
            assertTrue(simulation.connected());
            assertEquals("SIMULATED_CONNECTIVITY_OK", simulation.response());
        }
    }

    @Test
    void azureStartsWithTheOnlyConnectivityAdapterWithoutInvokingIt() {
        try (var context = application().run("--spring.profiles.active=demo-azure",
                "--spring.ai.openai.base-url=https://fixture.services.ai.azure.com/",
                "--spring.ai.openai.chat.model=fixture-deployment",
                "--AZURE_OPENAI_ACCESS_TOKEN=test-only-bearer")) {
            assertEquals(1, context.getBeansOfType(ChatClient.class).size());
            assertTrue(context.getBeansOfType(LocalModelConnectivityService.class).isEmpty());
            assertEquals("fixture-deployment", context.getBean(FoundrySettings.class).deployment());
        }
    }

    @Test
    void rejectsMissingConfigurationAndConcurrentProfiles() {
        assertConfigurationError("AZURE_OPENAI_DEPLOYMENT", "--spring.profiles.active=demo-azure",
                "--spring.ai.openai.chat.model=");
        assertConfigurationError("AZURE_OPENAI_ENDPOINT", "--spring.profiles.active=demo-azure",
                "--spring.ai.openai.chat.model=fixture-deployment", "--spring.ai.openai.base-url=");
        assertConfigurationError("AZURE_OPENAI_ACCESS_TOKEN", "--spring.profiles.active=demo-azure",
                "--spring.ai.openai.chat.model=fixture-deployment",
                "--spring.ai.openai.base-url=https://fixture.openai.azure.com", "--AZURE_OPENAI_ACCESS_TOKEN=");
        assertConfigurationError("exactly one", "--spring.profiles.active=demo-local,demo-azure");
    }

    @Test
    void canonicalizesOnlySupportedResourceEndpointsWithoutEchoingInvalidInput() {
        var canonical = URI.create("https://fixture.openai.azure.com/openai/v1");
        assertEquals(canonical, FoundrySettings.normalize("https://fixture.openai.azure.com/"));
        assertEquals(canonical, FoundrySettings.normalize(canonical + "/"));
        for (var invalid : List.of("https://api.openai.com/v1", "http://fixture.openai.azure.com",
                "https://fixture.services.ai.azure.com/api/projects/demo",
                canonical + "/openai/v1", canonical + "?secret=fixture", "https://bad input")) {
            var exception = assertThrows(FoundrySettings.ConfigurationException.class,
                    () -> FoundrySettings.normalize(invalid));
            assertFalse(exception.getMessage().contains(invalid));
        }
    }

    private static void assertConfigurationError(String expected, String... args) {
        var exception = assertThrows(RuntimeException.class, () -> application().run(args).close());
        Throwable cause = exception;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        assertTrue(cause instanceof FoundrySettings.ConfigurationException, cause::toString);
        assertTrue(cause.getMessage().contains(expected), cause::getMessage);
    }

    private static SpringApplicationBuilder application() {
        return new SpringApplicationBuilder(ArgusApplication.class)
                .web(WebApplicationType.NONE).bannerMode(Banner.Mode.OFF).logStartupInfo(false)
                .properties("logging.level.root=OFF");
    }
}
