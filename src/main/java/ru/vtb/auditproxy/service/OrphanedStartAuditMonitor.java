package ru.vtb.auditproxy.service;

import ru.vtb.omni.audit.core.sender.AuditEventSender;
import ru.vtb.omni.audit.lib.api.FieldsConstant;
import ru.vtb.omni.audit.lib.api.enums.AudLibEventClass;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Отслеживает START-события, для которых не пришло SUCCESS/FAILURE
 * в течение заданного таймаута, и генерирует FAILURE с причиной
 * "превышено время ожидания результата операции".
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrphanedStartAuditMonitor {

    private final AuditEventSender<Object> auditEventSender;

    /** correlationId -> время отправки START */
    private final Map<String, Instant> startEvents = new ConcurrentHashMap<>();

    /** Таймаут ожидания пары (мс). По умолчанию 2 минуты. */
    private static final long ORPHAN_TIMEOUT_MS = 120_000;

    /**
     * Регистрирует START-событие при его отправке.
     * Вызывается из AuditServiceImpl после успешной отправки START.
     */
    public void registerStart(String correlationId) {
        startEvents.put(correlationId, Instant.now());
    }

    /**
     * Удаляет запись при получении SUCCESS или FAILURE.
     */
    public void unregister(String correlationId) {
        startEvents.remove(correlationId);
    }

    /**
     * Периодическая проверка (каждые 30 секунд).
     */
    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void checkOrphanedStarts() {
        Instant now = Instant.now();
        startEvents.forEach((correlationId, startTime) -> {
            if (now.toEpochMilli() - startTime.toEpochMilli() > ORPHAN_TIMEOUT_MS) {
                log.warn("Обнаружено START-событие без пары: correlationId={}, время START={}, прошло={} мс. Генерируется FAILURE.",
                        correlationId, startTime, now.toEpochMilli() - startTime.toEpochMilli());

                Map<String, Object> failureEvent = Map.of(
                        FieldsConstant.EVENT_CLASS_FIELD_NAME, AudLibEventClass.FAILURE,
                        FieldsConstant.CORRELATION_ID_FIELD_NAME, correlationId,
                        FieldsConstant.ID_FIELD_NAME, UUID.randomUUID().toString(),
                        FieldsConstant.TIMESTAMP_FIELD_NAME, Instant.now(),
                        FieldsConstant.OPER_DESCRIPTION_FIELD_NAME,
                        "Превышено время ожидания результата операции (orphaned START)"
                );

                try {
                    auditEventSender.sendEvent(failureEvent, true);
                } catch (Exception e) {
                    log.error("Не удалось отправить FAILURE для осиротевшего START: correlationId={}", correlationId, e);
                }

                startEvents.remove(correlationId);
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        startEvents.clear();
    }
}