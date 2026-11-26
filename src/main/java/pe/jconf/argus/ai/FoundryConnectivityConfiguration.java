package pe.jconf.argus.ai;

import com.openai.azure.AzureUrlPathMode;
import com.openai.client.OpenAIClientAsyncImpl;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import com.openai.credential.BearerTokenCredential;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@Profile("demo-azure")
class FoundryConnectivityConfiguration {
    @Bean
    FoundrySettings foundrySettings(Environment environment) {
        return FoundrySettings.from(environment);
    }

    @Bean(destroyMethod = "close")
    SpringAiOpenAiHttpClient foundryTransport(FoundrySettings settings) {
        return SpringAiOpenAiHttpClient.builder().timeout(settings.timeout()).build();
    }

    @Bean
    ChatClient foundryChatClient(FoundrySettings settings, SpringAiOpenAiHttpClient transport, Environment environment) {
        var accessToken = environment.getProperty("AZURE_OPENAI_ACCESS_TOKEN", "");
        if (!accessToken.matches("[!-~]+")) {
            throw new FoundrySettings.ConfigurationException("Set AZURE_OPENAI_ACCESS_TOKEN to a short-lived Entra token.");
        }
        return chatClient(settings, transport, accessToken);
    }

    static ChatClient chatClient(FoundrySettings settings, SpringAiOpenAiHttpClient transport, String accessToken) {
        var sdkOptions = ClientOptions.builder()
                .httpClient(transport)
                .baseUrl(settings.baseUrl().toString())
                .azureUrlPathMode(AzureUrlPathMode.UNIFIED)
                .credential(BearerTokenCredential.create(accessToken))
                .timeout(settings.timeout())
                .maxRetries(settings.maxRetries())
                .build();
        var chatOptions = OpenAiChatOptions.builder().model(settings.deployment())
                .maxCompletionTokens(settings.maxCompletionTokens()).timeout(settings.timeout()).build();
        var model = OpenAiChatModel.builder().openAiClient(new OpenAIClientImpl(sdkOptions))
                .openAiClientAsync(new OpenAIClientAsyncImpl(sdkOptions)).options(chatOptions).build();
        return ChatClient.create(model);
    }

    @Bean
    ModelConnectivityService azureModelConnectivityService(ChatClient foundryChatClient, FoundrySettings settings) {
        return new AzureModelConnectivityService(foundryChatClient, settings.deployment());
    }
}
