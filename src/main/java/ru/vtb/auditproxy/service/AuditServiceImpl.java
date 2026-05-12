package ru.vtb.auditproxy.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.vtb.auditproxy.config.AuditEventsProperties;
import ru.vtb.auditproxy.dto.AuditRequest;
import ru.vtb.auditproxy.dto.AuditResponse;
import ru.vtb.auditproxy.dto.EventClass;
import ru.vtb.auditproxy.exception.AuditSendException;
import ru.vtb.omni.audit.core.sender.AuditEventSender;
import ru.vtb.omni.audit.lib.api.enums.AudLibEventClass;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static ru.vtb.omni.audit.lib.api.FieldsConstant.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditEventSender<?> auditEventSender;
    private final TechFieldsProvider techFieldsProvider;
    private final AuditEventsProperties auditEventsProperties;

    @Override
    public AuditResponse sendAuditEvent(AuditRequest request) {
        // 1. Получить схему события из конфигурации (по умолчанию "vtb")
        String schema = auditEventsProperties.getAuditEventCodeList().stream()
                .filter(ev -> ev.getEventCode().equals(request.getEventCode()))
                .findFirst()
                .map(AuditEventsProperties.AuditEventCode::getSchema)
                .orElse("vtb");

        // 2. Базовые поля
        Map<String, Object> eventMap = new HashMap<>();
        if (request.getAdditionalFields() != null) {
            eventMap.putAll(request.getAdditionalFields());
        }

        // 3. Обязательные поля согласно схеме
        eventMap.put(SCHEMA_TYPE_FIELD_NAME, schema);
        eventMap.put(SCHEMA_VERSION_FIELD_NAME, "1");  // начальная версия схемы

        String correlationId = UUID.randomUUID().toString();
        eventMap.put(CORRELATION_ID_FIELD_NAME, correlationId);  // сквозной идентификатор

        // 4. Класс события (enum, а не строка!)
        eventMap.put(EVENT_CLASS_FIELD_NAME, convertToAudLibEventClass(request.getEventClass()));

        eventMap.put(EVENT_CODE_FIELD_NAME, request.getEventCode());

        String timestamp = request.getTimestamp() != null ? request.getTimestamp() : Instant.now().toString();
        eventMap.putIfAbsent(TIMESTAMP_FIELD_NAME, timestamp);
        eventMap.putIfAbsent("oper_time", timestamp);

        // 5. Технические поля (код системы, id, namespace, podName, traceId и т.д.)
        techFieldsProvider.enrich(eventMap);

        // 6. Проверка, является ли событие блокирующим
        boolean isBlocking = auditEventsProperties.getBlockSettings()
                .getOrDefault(request.getEventCode(), false);

        log.debug("Sending audit event: eventCode={}, class={}, blocking={}, fields={}",
                request.getEventCode(), request.getEventClass(), isBlocking, eventMap);

        try {
            // 7. Отправка события (библиотека сама обрабатывает ретраи, переключение на StandIn, буферизацию)
            Object sendResult = auditEventSender.sendEvent(eventMap);
            String eventId = sendResult != null ? sendResult.toString() : "null";
            log.info("Audit event sent successfully: eventCode={}, id={}, correlationId={}, blocking={}",
                    request.getEventCode(), eventId, correlationId, isBlocking);
        } catch (Exception e) {
            log.error("Failed to send audit event: eventCode={}, class={}, correlationId={}, blocking={}, error={}",
                    request.getEventCode(), request.getEventClass(), correlationId, isBlocking, e.getMessage(), e);
            if (isBlocking) {
                throw new AuditSendException("Failed to send blocking audit event: " + e.getMessage(), e);
            } else {
                // Неблокирующее событие – только логируем, исключение не пробрасываем.
                // Библиотека сама сохранит событие в кольцевой буфер (in-memory-skipped-storage)
                log.warn("Non-blocking audit event was not sent (only logged): {}", request.getEventCode());
            }
        }

        return new AuditResponse("accepted", null);
    }

    /**
     * Преобразование внутреннего enum EventClass в библиотечный AudLibEventClass.
     */
    private AudLibEventClass convertToAudLibEventClass(EventClass eventClass) {
        if (eventClass == null) return null;
        switch (eventClass) {
            case START:
                return AudLibEventClass.START;
            case SUCCESS:
                return AudLibEventClass.SUCCESS;
            case FAILURE:
                return AudLibEventClass.FAILURE;
            default:
                throw new IllegalArgumentException("Unknown event class: " + eventClass);
        }
    }
}