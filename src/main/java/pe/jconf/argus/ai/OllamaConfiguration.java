package pe.jconf.argus.ai;

import com.openai.client.OpenAIClientAsyncImpl;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import io.micrometer.observation.ObservationRegistry;
import java.net.URI;
import java.time.Duration;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@Profile("demo-ollama")
class OllamaConfiguration {
    record Settings(URI baseUrl, String model, Duration timeout) {
        static Settings from(Environment environment) {
            URI endpoint;
            try {
                endpoint = URI.create(environment.getRequiredProperty("argus.ollama.endpoint"));
            } catch (IllegalArgumentException exception) {
                throw new FoundrySettings.ConfigurationException("Set OLLAMA_ENDPOINT to a loopback HTTP origin.");
            }
            if (!"http".equals(endpoint.getScheme()) || !"127.0.0.1".equals(endpoint.getHost())
                    || endpoint.getPort() < 1 || endpoint.getPort() > 65535
                    || endpoint.getRawUserInfo() != null || endpoint.getRawQuery() != null
                    || endpoint.getRawFragment() != null || !endpoint.getPath().matches("/?")) {
                throw new FoundrySettings.ConfigurationException("Set OLLAMA_ENDPOINT to http://127.0.0.1:<port>.");
            }
            var model = environment.getRequiredProperty("argus.ollama.model");
            if (!model.matches("[a-zA-Z0-9][a-zA-Z0-9._/-]{0,99}(:[a-zA-Z0-9._-]{1,64})?")
                    || model.toLowerCase(java.util.Locale.ROOT).contains("cloud")) {
                throw new FoundrySettings.ConfigurationException("Set OLLAMA_MODEL to a local model tag.");
            }
            return new Settings(URI.create("http://127.0.0.1:" + endpoint.getPort() + "/v1"), model,
                    Duration.parse(environment.getRequiredProperty("argus.investigation.http-request-timeout")));
        }
    }

    @Bean
    Settings ollamaSettings(Environment environment) {
        return Settings.from(environment);
    }

    @Bean(destroyMethod = "close")
    SpringAiOpenAiHttpClient ollamaTransport(Settings settings) {
        return SpringAiOpenAiHttpClient.builder().timeout(settings.timeout()).build();
    }

    @Bean
    ChatClient ollamaChatClient(Settings settings, SpringAiOpenAiHttpClient transport, ObservationRegistry observations) {
        var options = ClientOptions.builder().httpClient(transport).baseUrl(settings.baseUrl().toString())
                .apiKey("ollama").timeout(settings.timeout()).maxRetries(0).build();
        var chat = OpenAiChatModel.builder().openAiClient(new OpenAIClientImpl(options))
                .openAiClientAsync(new OpenAIClientAsyncImpl(options))
                .options(OpenAiChatOptions.builder().model(settings.model()).temperature(0.0)
                        .maxCompletionTokens(64).timeout(settings.timeout()).build())
                .observationRegistry(observations).build();
        return ChatClient.create(chat, observations);
    }

    @Bean
    ModelConnectivityService ollamaConnectivityService(ChatClient ollamaChatClient, Settings settings) {
        return new AzureModelConnectivityService(ollamaChatClient, settings.model(), ModelConnectivityResult.Mode.OLLAMA);
    }
}

