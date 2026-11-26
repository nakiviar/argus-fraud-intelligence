package pe.jconf.argus.ai;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;

record FoundrySettings(URI baseUrl, String deployment, Duration timeout, int maxRetries, int maxCompletionTokens) {
    static FoundrySettings from(Environment environment) {
        if (environment.acceptsProfiles(Profiles.of("demo-local"))) {
            throw new ConfigurationException("Select exactly one of demo-local and demo-azure.");
        }
        var deployment = environment.getProperty("spring.ai.openai.chat.model", "");
        if (!deployment.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")) {
            throw new ConfigurationException("Set AZURE_OPENAI_DEPLOYMENT to a valid deployment name.");
        }

        try {
            var timeout = Duration.parse(environment.getProperty("spring.ai.openai.timeout"));
            int retries = Integer.parseInt(environment.getProperty("spring.ai.openai.max-retries"));
            int maxTokens = Integer.parseInt(environment.getProperty("spring.ai.openai.chat.max-completion-tokens"));
            if (timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofSeconds(30)) > 0
                    || retries != 0 || maxTokens < 1 || maxTokens > 128) {
                throw new IllegalArgumentException();
            }
            return new FoundrySettings(normalize(environment.getProperty("spring.ai.openai.base-url", "")),
                    deployment, timeout, retries, maxTokens);
        } catch (ConfigurationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ConfigurationException("Invalid diagnostic timeout, retry count, or completion token limit.");
        }
    }

    static URI normalize(String endpoint) {
        try {
            var uri = URI.create(endpoint);
            var host = uri.getHost();
            var path = uri.getRawPath();
            if ("https".equalsIgnoreCase(uri.getScheme()) && host != null
                    && host.toLowerCase(Locale.ROOT).matches("[a-z0-9-]+\\.(openai|services\\.ai)\\.azure\\.com")
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && uri.getRawQuery() == null && uri.getRawFragment() == null && uri.getRawUserInfo() == null
                    && (path.isEmpty() || path.equals("/") || path.equals("/openai/v1") || path.equals("/openai/v1/"))) {
                return URI.create("https://" + host + "/openai/v1");
            }
        } catch (IllegalArgumentException exception) {
            // Avoid propagating the supplied URL in the exception's message.
        }
        throw new ConfigurationException("Set AZURE_OPENAI_ENDPOINT to the HTTPS Foundry resource endpoint.");
    }

    static final class ConfigurationException extends IllegalArgumentException {
        ConfigurationException(String message) {
            super(message);
        }
    }
}
