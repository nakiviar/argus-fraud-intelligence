package pe.jconf.argus.ai;

public record ModelConnectivityResult(Mode mode, boolean connected, String deployment, long elapsedMillis,
                                      String response, TokenUsage tokenUsage, ErrorCategory errorCategory) {
    public enum Mode {
        SIMULATED, AZURE
    }

    public enum ErrorCategory {
        CONFIGURATION, AUTHENTICATION, DEPLOYMENT_UNAVAILABLE, RATE_LIMIT, CONNECTION, PROVIDER_ERROR,
        UNEXPECTED_RESPONSE
    }

    public record TokenUsage(Integer promptTokens, Integer completionTokens, Integer totalTokens) {
    }
}
