package ru.vtb.auditproxy.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.vtb.auditproxy.dto.AuditRequest;
import ru.vtb.auditproxy.dto.AuditResponse;
import ru.vtb.auditproxy.exception.AuditSendException;
import ru.vtb.omni.audit.core.properties.AuditLibProperties;
import ru.vtb.omni.audit.core.properties.AuditMsProperties;
import ru.vtb.omni.audit.core.sender.AuditEventSender;
import ru.vtb.omni.audit.lib.api.FieldsConstant;
import ru.vtb.omni.audit.lib.api.enums.AudLibEventClass;
import ru.vtb.omni.audit.lib.api.event.AuditEventCode;
import ru.vtb.omni.audit.lib.config.AuditEventDescriptionObject;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditEventSender<Object> auditEventSender;
    private final AuditEventDescriptionObject auditEventDescriptionObject;
    private final AuditLibProperties auditLibProperties;
    private final AuditMsProperties auditMsProperties;

    @Override
    public AuditResponse sendAuditEvent(AuditRequest request) {
        log.debug("Building audit event: eventCode={}, eventClass={}, correlationId={}",
                request.getEventCode(), request.getEventClass(), request.getCorrelationId());

        Map<String, Object> auditEvent = buildAuditEvent(request);

        try {
            // Асинхронная отправка – не блокируем ответ сайдкара
            auditEventSender.sendEvent(auditEvent, true);
            log.info("Audit event sent successfully: eventCode={}, correlationId={}",
                    request.getEventCode(), request.getCorrelationId());
            return new AuditResponse("accepted", "Audit event processed");
        } catch (Exception e) {
            log.error("Failed to send audit event: eventCode={}, correlationId={}",
                    request.getEventCode(), request.getCorrelationId(), e);
            throw new AuditSendException("Audit send failed: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> buildAuditEvent(AuditRequest request) {
        Map<String, Object> event = new HashMap<>();

        // ---- обязательные поля ----
        event.put(FieldsConstant.EVENT_CODE_FIELD_NAME, request.getEventCode());
        event.put(FieldsConstant.EVENT_CLASS_FIELD_NAME, AudLibEventClass.valueOf(request.getEventClass().name()));

        // timestamp – ISO 8601 с часовым поясом
        ZonedDateTime timestamp = request.getTimestamp() != null
                ? ZonedDateTime.parse(request.getTimestamp(), DateTimeFormatter.ISO_DATE_TIME)
                : ZonedDateTime.now(ZoneOffset.UTC);
        event.put(FieldsConstant.TIMESTAMP_FIELD_NAME, timestamp);

        // correlationId – если не передан, генерируем новый
        String correlationId = request.getCorrelationId() != null
                ? request.getCorrelationId()
                : UUID.randomUUID().toString();
        event.put(FieldsConstant.CORRELATION_ID_FIELD_NAME, correlationId);

        // схема и версия – берём схему из конфигурации события, версию фиксируем "1"
        String schema = getSchemaForEventCode(request.getEventCode());
        event.put(FieldsConstant.SCHEMA_TYPE_FIELD_NAME, schema);
        event.put(FieldsConstant.SCHEMA_VERSION_FIELD_NAME, "1");

        // ---- технические поля (из конфигурации и окружения) ----
        event.put(FieldsConstant.INFO_SYSTEM_CODE_FIELD_NAME, auditMsProperties.getInfoSystemCode());
        event.put(FieldsConstant.INFO_SYSTEM_ID_FIELD_NAME, auditMsProperties.getInfoSystemId());
        event.put(FieldsConstant.NAMESPACE_FIELD_NAME, getPodNamespace());
        event.put(FieldsConstant.POD_NAME_FIELD_NAME, getPodName());

        // ---- инициатор (из запроса или default) ----
        if (request.getInitiator() != null) {
            event.put(FieldsConstant.LOGIN_FIELD_NAME,
                    request.getInitiator().getOrDefault("sub", auditLibProperties.getSub()));
            event.put(FieldsConstant.CHANNEL_FIELD_NAME,
                    request.getInitiator().getOrDefault("channel", auditLibProperties.getChannel()));
        } else {
            event.put(FieldsConstant.LOGIN_FIELD_NAME, auditLibProperties.getSub());
            event.put(FieldsConstant.CHANNEL_FIELD_NAME, auditLibProperties.getChannel());
        }

        // ---- статические поля из YAML (в зависимости от класса события) ----
        addStaticFields(event, request.getEventCode(), request.getEventClass());

        // ---- дополнительные параметры от основного приложения ----
        if (request.getAdditionalFields() != null && !request.getAdditionalFields().isEmpty()) {
            event.put("additionalParams", request.getAdditionalFields());
        }

        return event;
    }

    private String getSchemaForEventCode(String eventCode) {
        return auditEventDescriptionObject.getAuditEventCodeList().stream()
                .filter(ec -> ec.getEventCode().equals(eventCode))
                .findFirst()
                .map(AuditEventCode::getSchema)
                .orElseThrow(() -> new IllegalArgumentException("Unknown event code: " + eventCode));
    }

    private String getPodNamespace() {
        String envName = auditLibProperties.getPodNamespaceEnvName();
        String value = System.getenv(envName);
        return value != null ? value : "";
    }

    private String getPodName() {
        String envName = auditLibProperties.getPodNameEnvName();
        String value = System.getenv(envName);
        return value != null ? value : "";
    }

    private void addStaticFields(Map<String, Object> event, String eventCode, ru.vtb.auditproxy.dto.EventClass eventClass) {
        auditEventDescriptionObject.getAuditEventCodeList().stream()
                .filter(ec -> ec.getEventCode().equals(eventCode))
                .findFirst()
                .ifPresent(ec -> {
                    // общие поля (auditEventGeneral)
                    ec.getAuditEventGeneral().forEach(event::putIfAbsent);

                    // поля в зависимости от класса события
                    switch (eventClass) {
                        case START -> ec.getAuditEventStart().forEach(event::putIfAbsent);
                        case SUCCESS -> ec.getAuditEventSuccess().forEach(event::putIfAbsent);
                        case FAILURE -> ec.getAuditEventFailure().forEach(event::putIfAbsent);
                    }
                });
    }
}