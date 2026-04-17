package ru.vtb.auditproxy.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.vtb.auditproxy.config.AuditEventsProperties;
import ru.vtb.auditproxy.dto.AuditRequest;
import ru.vtb.auditproxy.dto.AuditResponse;
import ru.vtb.auditproxy.exception.AuditSendException;
import ru.vtb.omni.audit.core.sender.AuditEventSender;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditEventSender<?> auditEventSender;
    private final TechFieldsProvider techFieldsProvider;
    private final AuditEventsProperties auditEventsProperties;

    @Override
    public AuditResponse sendAuditEvent(AuditRequest request) {
        // Формируем базовую мапу события
        Map<String, Object> eventMap = new HashMap<>();
        if (request.getAdditionalFields() != null) {
            eventMap.putAll(request.getAdditionalFields());
        }

        // Обязательные поля согласно схеме vtb
        eventMap.put("clazz", request.getEventClass().name());
        eventMap.put("event_code", request.getEventCode());

        // timestamp
        String timestamp = request.getTimestamp() != null ? request.getTimestamp() : Instant.now().toString();
        eventMap.putIfAbsent("timestamp", timestamp);
        // oper_time
        eventMap.putIfAbsent("oper_time", timestamp);

        // Технические поля (код и id системы)
        techFieldsProvider.enrich(eventMap);

        // Проверяем, блокирующее ли событие
        boolean isBlocking = auditEventsProperties.getBlockSettings()
                .getOrDefault(request.getEventCode(), false);

        log.debug("Sending audit event: eventCode={}, class={}, blocking={}, fields={}",
                request.getEventCode(), request.getEventClass(), isBlocking, eventMap);

        try {
            // Отправка события
            Object sendResult = auditEventSender.sendEvent(eventMap);
            String eventId = sendResult != null ? sendResult.toString() : "null";
            log.info("Audit event sent successfully: eventCode={}, id={}, blocking={}",
                    request.getEventCode(), eventId, isBlocking);
            if (isBlocking && sendResult instanceof String) {
                // Для блокирующих синхронно ждём id
                log.debug("Blocking audit event sent, id: {}", eventId);
            }
        } catch (Exception e) {
            log.error("Failed to send audit event: eventCode={}, class={}, blocking={}, error={}",
                    request.getEventCode(), request.getEventClass(), isBlocking, e.getMessage(), e);
            if (isBlocking) {
                throw new AuditSendException("Failed to send blocking audit event: " + e.getMessage(), e);
            } else {
                // Неблокирующее событие – только логируем, исключение не пробрасываем
                // Библиотека сама должна сохранить событие в буфер (in-memory-skipped-storage)
                log.warn("Non-blocking audit event lost (only logged): {}", request.getEventCode());
            }
        }

        return new AuditResponse("accepted", null);
    }
}