package ru.vtb.auditproxy.service;

import io.opentelemetry.api.trace.Span;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import ru.vtb.omni.audit.core.properties.AuditMsProperties;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TechFieldsProvider {
    private final AuditMsProperties auditMsProperties;
    private final Environment environment;

    public void enrich(Map<String, Object> eventMap) {
        // Код системы
        String infoSystemCode = auditMsProperties.getInfoSystemCode();
        String infoSystemId = auditMsProperties.getInfoSystemId();
        eventMap.putIfAbsent("event_infoSystemCode", infoSystemCode);
        eventMap.putIfAbsent("event_infoSystemId", infoSystemId);
        log.debug("TechFields: infoSystemCode={}, infoSystemId={}", infoSystemCode, infoSystemId);

        // Namespace и имя пода из переменных окружения
        String namespaceEnv = environment.getProperty(
                "audit.deployment-context.pod-namespace-environment",
                "AUDITOMNI_POD_NAMESPACE"
        );
        String podNameEnv = environment.getProperty(
                "audit.deployment-context.pod-name-environment",
                "AUDITOMNI_POD_NAME"
        );
        String namespace = System.getenv(namespaceEnv);
        String podName = System.getenv(podNameEnv);
        eventMap.putIfAbsent("context_namespace", namespace);
        eventMap.putIfAbsent("context_podName", podName);
        log.debug("TechFields: namespace={}, podName={} (from env {}/{}))", namespace, podName, namespaceEnv, podNameEnv);

        // Трассировка (OpenTelemetry)
        Span currentSpan = Span.current();
        if (currentSpan != null && currentSpan.getSpanContext().isValid()) {
            String traceId = currentSpan.getSpanContext().getTraceId();
            String spanId = currentSpan.getSpanContext().getSpanId();
            eventMap.putIfAbsent("context_traceId", traceId);
            eventMap.putIfAbsent("context_spanId", spanId);
            log.debug("TechFields: traceId={}, spanId={}", traceId, spanId);
        } else {
            log.debug("TechFields: no active OpenTelemetry span");
        }
    }
}