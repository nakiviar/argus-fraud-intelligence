package pe.jconf.argus.ai;

import java.util.stream.Stream;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
class ModelProfileConfiguration {
    ModelProfileConfiguration(Environment environment) {
        if (Stream.of("demo-local", "demo-ollama", "demo-azure").filter(environment::matchesProfiles).count() > 1) {
            throw new FoundrySettings.ConfigurationException("Select exactly one model execution profile.");
        }
    }
}

