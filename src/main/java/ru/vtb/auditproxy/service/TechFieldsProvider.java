package ru.vtb.auditproxy.service;

import io.opentelemetry.api.trace.Span;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import ru.vtb.omni.audit.properties.AuditMsProperties;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class TechFieldsProvider {
    private final AuditMsProperties auditMsProperties;
    private final Environment environment;

    public void enrich(Map<String, Object> eventMap) {
        // Код системы
        eventMap.putIfAbsent("event_infoSystemCode", auditMsProperties.getInfoSystemCode());
        eventMap.putIfAbsent("event_infoSystemId", auditMsProperties.getInfoSystemId());

        // Namespace и имя пода из переменных окружения
        String namespaceEnv = environment.getProperty(
                "audit.deployment-context.pod-namespace-environment",
                "AUDITOMNI_POD_NAMESPACE"
        );
        String podNameEnv = environment.getProperty(
                "audit.deployment-context.pod-name-environment",
                "AUDITOMNI_POD_NAME"
        );
        eventMap.putIfAbsent("context_namespace", System.getenv(namespaceEnv));
        eventMap.putIfAbsent("context_podName", System.getenv(podNameEnv));

        // Трассировка (OpenTelemetry)
        Span currentSpan = Span.current();
        if (currentSpan != null && currentSpan.getSpanContext().isValid()) {
            eventMap.putIfAbsent("context_traceId", currentSpan.getSpanContext().getTraceId());
            eventMap.putIfAbsent("context_spanId", currentSpan.getSpanContext().getSpanId());
        } else {
            // Если нет активного спана, можно оставить null или сгенерировать новые идентификаторы
            // В соответствии с рекомендациями оставляем null
        }
    }
}