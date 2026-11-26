package pe.jconf.argus.ai;

import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import org.springframework.ai.chat.client.ChatClient;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static pe.jconf.argus.ai.ModelConnectivityResult.ErrorCategory;
import static pe.jconf.argus.ai.ModelConnectivityResult.Mode.AZURE;

final class AzureModelConnectivityService implements ModelConnectivityService {
    static final String ACKNOWLEDGEMENT = "ARGUS_CONNECTIVITY_OK";
    private static final String PROMPT = "Reply with exactly these characters and nothing else: " + ACKNOWLEDGEMENT;

    private final ChatClient chatClient;
    private final String deployment;
    private final ModelConnectivityResult.Mode mode;

    AzureModelConnectivityService(ChatClient chatClient, String deployment) {
        this(chatClient, deployment, AZURE);
    }

    AzureModelConnectivityService(ChatClient chatClient, String deployment, ModelConnectivityResult.Mode mode) {
        this.mode = mode;
        this.chatClient = chatClient;
        this.deployment = deployment;
    }

    @Override
    public ModelConnectivityResult check() {
        long start = System.nanoTime();
        try {
            var reply = chatClient.prompt().user(PROMPT).call().chatResponse();
            var generation = reply == null ? null : reply.getResult();
            var text = generation == null ? null : generation.getOutput().getText();
            var usage = reply == null ? null : reply.getMetadata().getUsage();
            var tokens = usage == null ? null : new ModelConnectivityResult.TokenUsage(
                    usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
            boolean acknowledged = text != null && ACKNOWLEDGEMENT.equals(text.strip());
            return new ModelConnectivityResult(mode, acknowledged, deployment, elapsed(start),
                    acknowledged ? ACKNOWLEDGEMENT : null, tokens,
                    acknowledged ? null : ErrorCategory.UNEXPECTED_RESPONSE);
        } catch (RuntimeException exception) {
            return new ModelConnectivityResult(mode, false, deployment, elapsed(start), null, null, classify(exception));
        }
    }

    private static ErrorCategory classify(Throwable failure) {
        for (var current = failure; current != null; current = current.getCause()) {
            if (current instanceof OpenAIServiceException response) {
                return switch (response.statusCode()) {
                    case 401, 403 -> ErrorCategory.AUTHENTICATION;
                    case 404 -> ErrorCategory.DEPLOYMENT_UNAVAILABLE;
                    case 429 -> ErrorCategory.RATE_LIMIT;
                    default -> ErrorCategory.PROVIDER_ERROR;
                };
            }
            if (current instanceof OpenAIIoException || current instanceof IOException) {
                return ErrorCategory.CONNECTION;
            }
        }
        return ErrorCategory.PROVIDER_ERROR;
    }

    private static long elapsed(long start) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }
}
