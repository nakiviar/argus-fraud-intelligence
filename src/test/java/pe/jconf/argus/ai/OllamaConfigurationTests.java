package pe.jconf.argus.ai;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OllamaConfigurationTests {
    @Test
    void resolvesOnlyTheLocalV1Endpoint() {
        var settings = OllamaConfiguration.Settings.from(environment("http://127.0.0.1:11434/", "llama3.1:latest"));
        assertEquals(URI.create("http://127.0.0.1:11434/v1"), settings.baseUrl());
        assertEquals("llama3.1:latest", settings.model());
    }

    @Test
    void rejectsRemoteEndpointsAndCloudModelNames() {
        for (var address : new String[]{"https://api.openai.com", "http://127.0.0.1:11434/v1", "http://user@127.0.0.1:11434"}) {
            assertThrows(FoundrySettings.ConfigurationException.class,
                    () -> OllamaConfiguration.Settings.from(environment(address, "llama3.1:latest")));
        }
        assertThrows(FoundrySettings.ConfigurationException.class,
                () -> OllamaConfiguration.Settings.from(environment("http://127.0.0.1:11434", "test:cloud")));
    }

    @Test
    void rejectsMixedOllamaAndSimulationProfiles() {
        var config = environment("http://127.0.0.1:11434", "llama3.1:latest");
        config.setActiveProfiles("demo-local", "demo-ollama");
        assertThrows(FoundrySettings.ConfigurationException.class, () -> new ModelProfileConfiguration(config));
    }

    private StandardEnvironment environment(String endpoint, String model) {
        var environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "argus.ollama.endpoint", endpoint, "argus.ollama.model", model,
                "argus.investigation.http-request-timeout", "PT5S")));
        return environment;
    }
}

