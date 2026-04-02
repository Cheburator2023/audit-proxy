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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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

        try {
            // Отправка события
            if (isBlocking) {
                // Для блокирующих событий вызываем синхронно – при ошибке будет исключение
                String eventId = (String) auditEventSender.sendEvent(eventMap);
                log.debug("Blocking audit event sent, id: {}", eventId);
            } else {
                // Для неблокирующих – вызываем и не ждём, ловим возможные ошибки
                auditEventSender.sendEvent(eventMap);
            }
        } catch (Exception e) {
            if (isBlocking) {
                throw new AuditSendException("Failed to send blocking audit event", e);
            } else {
                log.error("Non-blocking audit event send failed", e);
            }
        }

        return new AuditResponse("accepted", null);
    }
}