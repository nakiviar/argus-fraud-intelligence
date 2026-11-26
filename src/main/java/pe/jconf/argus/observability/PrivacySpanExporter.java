package pe.jconf.argus.observability;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.data.DelegatingSpanData;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import java.util.Collection;
import java.util.List;
import java.util.Set;

final class PrivacySpanExporter implements SpanExporter {
    private static final Set<String> ATTRIBUTES = Set.of(
            "agent", "mode", "status", "outcome", "late", "token.usage",
            "method", "uri", "exception", "error", "http.route", "http.request.method",
            "http.response.status_code", "gen_ai.operation.name", "gen_ai.provider.name",
            "gen_ai.request.model", "spring.ai.kind", "spring.ai.advisor.name",
            "spring.ai.tool.definition.name", "spring.ai.tool.type");
    private static final Resource RESOURCE = Resource.create(Attributes.builder()
            .put("service.name", "argus-fraud-intelligence").build());

    private final SpanExporter delegate;

    PrivacySpanExporter(SpanExporter delegate) {
        this.delegate = delegate;
    }

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
        return delegate.export(spans.stream().map(span -> (SpanData) new SanitizedSpan(span)).toList());
    }

    @Override
    public CompletableResultCode flush() {
        return delegate.flush();
    }

    @Override
    public CompletableResultCode shutdown() {
        return delegate.shutdown();
    }

    private static final class SanitizedSpan extends DelegatingSpanData {
        private SanitizedSpan(SpanData delegate) {
            super(delegate);
        }

        @Override
        public Attributes getAttributes() {
            var safe = Attributes.builder();
            super.getAttributes().forEach((key, value) -> {
                if (ATTRIBUTES.contains(key.getKey())) {
                    put(safe, key, value);
                }
            });
            return safe.build();
        }

        @SuppressWarnings("unchecked")
        private static <T> void put(io.opentelemetry.api.common.AttributesBuilder target,
                                    io.opentelemetry.api.common.AttributeKey<T> key, Object value) {
            target.put(key, (T) value);
        }

        @Override
        public List<EventData> getEvents() {
            return List.of();
        }

        @Override
        public int getTotalRecordedEvents() {
            return 0;
        }

        @Override
        public List<LinkData> getLinks() {
            return super.getLinks().stream()
                    .map(link -> LinkData.create(withoutTraceState(link.getSpanContext()))).toList();
        }

        @Override
        public int getTotalRecordedLinks() {
            return getLinks().size();
        }

        @Override
        public StatusData getStatus() {
            return super.getStatus().getStatusCode() == StatusCode.ERROR
                    ? StatusData.error() : super.getStatus();
        }

        @Override
        public SpanContext getSpanContext() {
            return withoutTraceState(super.getSpanContext());
        }

        @Override
        public SpanContext getParentSpanContext() {
            return withoutTraceState(super.getParentSpanContext());
        }

        @Override
        public Resource getResource() {
            return RESOURCE;
        }

        @Override
        public String toString() {
            return getName() + " [" + getTraceId() + "/" + getSpanId() + "]";
        }

        private static SpanContext withoutTraceState(SpanContext context) {
            if (context.isRemote()) {
                return SpanContext.createFromRemoteParent(context.getTraceId(), context.getSpanId(),
                        context.getTraceFlags(), TraceState.getDefault());
            }
            return SpanContext.create(context.getTraceId(), context.getSpanId(), context.getTraceFlags(),
                    TraceState.getDefault());
        }
    }
}
