package pe.jconf.argus.investigation;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.time.Duration;

final class ModelRoundAdvisor implements CallAdvisor {
    private final RemoteModelProtection protection;
    private final ExecutionBudget budget;
    private final Duration httpTimeout;

    ModelRoundAdvisor(RemoteModelProtection protection, ExecutionBudget budget, Duration httpTimeout) {
        this.protection = protection;
        this.budget = budget;
        this.httpTimeout = httpTimeout;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        return protection.execute(budget, () -> {
            var options = (OpenAiChatOptions) request.prompt().getOptions();
            var timeout = budget.requestTimeout(httpTimeout);
            var prompt = new Prompt(request.prompt().getInstructions(), options.mutate().timeout(timeout).build());
            return chain.nextCall(request.mutate().prompt(prompt).build());
        });
    }

    @Override
    public int getOrder() {
        return ToolCallingAdvisor.DEFAULT_ORDER + 1;
    }

    @Override
    public String getName() {
        return "ARGUS model round containment";
    }
}
