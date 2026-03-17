package ru.vtb.auditproxy.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.vtb.auditproxy.config.AuditEventsProperties;
import ru.vtb.auditproxy.dto.AuditRequest;
import ru.vtb.auditproxy.dto.AuditResponse;
import ru.vtb.auditproxy.dto.EventClass;
import ru.vtb.auditproxy.exception.AuditSendException;
import ru.vtb.omni.audit.sender.AuditEventSender;

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
    private final AuditEventSender<Object> kafkaAuditEventSender;
    private final TechFieldsProvider techFieldsProvider;
    private final AuditEventsProperties auditEventsProperties;

    @Override
    public AuditResponse sendAuditEvent(AuditRequest request) {
        // 1. Формируем базовую мапу события
        Map<String, Object> eventMap = new HashMap<>();
        if (request.getAdditionalFields() != null) {
            eventMap.putAll(request.getAdditionalFields());
        }

        // 2. Добавляем класс события
        eventMap.put("event_class", request.getEventClass().name());

        // 3. Добавляем технические поля (не перетираем переданные)
        techFieldsProvider.enrich(eventMap);

        // 4. Устанавливаем временную метку, если не задана
        if (!eventMap.containsKey("oper_timestamp")) {
            if (request.getTimestamp() != null) {
                eventMap.put("oper_timestamp", request.getTimestamp());
            } else {
                eventMap.put("oper_timestamp", Instant.now().toString());
            }
        }

        // 5. Проверяем, блокирующее ли событие
        boolean isBlocking = auditEventsProperties.getBlockSettings()
                .getOrDefault(request.getEventCode(), false);

        try {
            // Отправка события
            CompletableFuture<?> future = kafkaAuditEventSender.sendEvent(eventMap);

            if (isBlocking) {
                // Для блокирующих событий дожидаемся подтверждения
                future.get(10, TimeUnit.SECONDS); // таймаут можно вынести в конфиг
            } else {
                // Для неблокирующих — не ждём, только логируем возможные ошибки
                future.whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Non-blocking audit event send failed asynchronously", ex);
                    }
                });
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AuditSendException("Interrupted while sending audit event", e);
        } catch (ExecutionException | TimeoutException e) {
            if (isBlocking) {
                throw new AuditSendException("Failed to send blocking audit event", e);
            } else {
                log.error("Non-blocking audit event send failed", e);
            }
        }

        return new AuditResponse("accepted", null);
    }
}