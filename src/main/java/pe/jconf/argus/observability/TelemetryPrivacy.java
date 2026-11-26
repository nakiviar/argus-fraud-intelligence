package pe.jconf.argus.observability;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.ObservationFilter;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.SpanExporters;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class TelemetryPrivacy {
    @Bean
    ObservationFilter restrictObservationMetadata() {
        return context -> {
            for (var value : context.getHighCardinalityKeyValues()) {
                context.removeHighCardinalityKeyValue(value.getKey());
            }
            context.removeLowCardinalityKeyValue("gen_ai.response.model");
            if (context instanceof ChatModelObservationContext model) {
                var response = model.getResponse();
                var usage = response == null ? null : response.getMetadata().getUsage();
                context.addLowCardinalityKeyValue(KeyValue.of("token.usage",
                        usage == null || usage instanceof EmptyUsage ? "unavailable" : "available"));
            }
            return context;
        };
    }

    @Bean
    SpanExporters sanitizedSpanExporters(ObjectProvider<SpanExporter> exporters) {
        return SpanExporters.of(exporters.orderedStream().map(PrivacySpanExporter::new).toList());
    }
}
