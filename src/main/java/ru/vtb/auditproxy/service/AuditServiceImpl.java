package ru.vtb.auditproxy.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.vtb.auditproxy.dto.AuditRequest;
import ru.vtb.auditproxy.dto.AuditResponse;
import ru.vtb.auditproxy.dto.EventClass;
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

    private static final String FIELD_CONTEXT_RECIPIENT_IP = "context_recipientIp";
    private static final String FIELD_ADDITIONAL_PARAMS = "additionalParams";

    private final AuditEventSender<Object> auditEventSender;
    private final AuditEventDescriptionObject auditEventDescriptionObject;
    private final AuditLibProperties auditLibProperties;
    private final AuditMsProperties auditMsProperties;

    private final InitiatorFieldMapper initiatorFieldMapper;
    private final AdditionalParamsEncoder additionalParamsEncoder;
    private final ErrorDescriptionEnricher errorDescriptionEnricher;
    private final SchemaFieldResolver schemaFieldResolver;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Override
    public AuditResponse sendAuditEvent(AuditRequest request) {
        log.debug("Building audit event: eventCode={}, eventClass={}, correlationId={}",
                request.getEventCode(), request.getEventClass(), request.getCorrelationId());

        Map<String, Object> auditEvent = buildAuditEvent(request);

        // --- Логирование содержимого сообщения перед отправкой ---
        if (log.isDebugEnabled()) {
            try {
                String eventJson = OBJECT_MAPPER.writeValueAsString(auditEvent);
                log.debug("Audit event payload (JSON): {}", eventJson);
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize audit event to JSON for logging", e);
                // В случае ошибки сериализации выводим как есть
                log.debug("Audit event payload (raw): {}", auditEvent);
            }
        }

        try {
            auditEventSender.sendEvent(auditEvent, true);
            log.info("Audit event sent successfully: eventCode={}, correlationId={}",
                    request.getEventCode(), request.getCorrelationId());
            return new AuditResponse("accepted", "Audit event processed");
        } catch (Exception e) {
            // Дополнительное логирование сообщения при ошибке
            log.error("Failed to send audit event: eventCode={}, correlationId={}, eventPayload={}",
                    request.getEventCode(), request.getCorrelationId(), auditEvent, e);
            throw new AuditSendException("Audit send failed: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> buildAuditEvent(AuditRequest request) {
        Map<String, Object> event = new HashMap<>();

        // ---- обязательные поля ----
        event.put(FieldsConstant.ID_FIELD_NAME, UUID.randomUUID().toString());
        event.put(FieldsConstant.EVENT_CODE_FIELD_NAME, request.getEventCode());

        AudLibEventClass audLibEventClass = AudLibEventClass.valueOf(request.getEventClass().name());
        event.put(FieldsConstant.EVENT_CLASS_FIELD_NAME, audLibEventClass);

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

        // ---- схема и версия ----
        SchemaFieldResolver.SchemaResolution schemaResolution =
                schemaFieldResolver.resolve(request.getEventCode());
        event.put(FieldsConstant.SCHEMA_TYPE_FIELD_NAME, schemaResolution.schema());
        event.put(FieldsConstant.SCHEMA_VERSION_FIELD_NAME, String.valueOf(schemaResolution.version()));

        // ---- технические поля ----
        event.put(FieldsConstant.INFO_SYSTEM_CODE_FIELD_NAME, auditMsProperties.getInfoSystemCode());
        event.put(FieldsConstant.INFO_SYSTEM_ID_FIELD_NAME, auditMsProperties.getInfoSystemId());
        event.put(FieldsConstant.NAMESPACE_FIELD_NAME, getPodNamespace());
        event.put(FieldsConstant.POD_NAME_FIELD_NAME, getPodName());

        // ---- инициатор события ----
        initiatorFieldMapper.mapInitiator(request, event, auditLibProperties);

        // ---- context_recipientIp, context_traceId, context_spanId ----
        putIfNotNull(event, FIELD_CONTEXT_RECIPIENT_IP, request.getRecipientIp());
        putIfNotNull(event, FieldsConstant.TRACE_FIELD_NAME, resolveTraceId(request));
        putIfNotNull(event, FieldsConstant.SPAN_FIELD_NAME, resolveSpanId(request));

        // ---- статические поля из YAML (auditEventGeneral/Start/Success/Failure) ----
        addStaticFields(event, request.getEventCode(), request.getEventClass());

        // ---- oper_resultStatus ----
        event.put("oper_resultStatus", resolveResultStatus(request.getEventClass()));

        // ---- additionalFields: кодирование + обогащение oper_description ----
        if (request.getAdditionalFields() != null && !request.getAdditionalFields().isEmpty()) {
            Map<String, Object> additionalFields = request.getAdditionalFields();

            // Явное переопределение oper_description (если пришло от поставщика)
            Object operDescription = additionalFields.get(FieldsConstant.OPER_DESCRIPTION_FIELD_NAME);
            if (operDescription != null && !operDescription.toString().isEmpty()) {
                event.put(FieldsConstant.OPER_DESCRIPTION_FIELD_NAME, operDescription);
            }

            // Обогащение oper_description по шаблонам 43_1_СС_Аудит...
            errorDescriptionEnricher.enrich(event, request, additionalFields);

            // Кодируем additionalFields в Map<String,String> для Avro (additionalParams)
            Map<String, String> encoded = additionalParamsEncoder.encode(additionalFields);
            event.put(FIELD_ADDITIONAL_PARAMS, encoded);
        }

        log.debug("Built audit event: schema='{}', version='{}'",
                schemaResolution.schema(), schemaResolution.version());
        return event;
    }

    /**
     * TraceId: приоритет — заголовок traceparent, fallback — additionalFields.traceId.
     */
    private String resolveTraceId(AuditRequest request) {
        if (request.getTraceId() != null && !request.getTraceId().isEmpty()) {
            return request.getTraceId();
        }
        if (request.getAdditionalFields() != null) {
            Object fallback = request.getAdditionalFields().get("traceId");
            return fallback != null ? fallback.toString() : null;
        }
        return null;
    }

    /**
     * SpanId: приоритет — заголовок traceparent, fallback — additionalFields.spanId.
     */
    private String resolveSpanId(AuditRequest request) {
        if (request.getSpanId() != null && !request.getSpanId().isEmpty()) {
            return request.getSpanId();
        }
        if (request.getAdditionalFields() != null) {
            Object fallback = request.getAdditionalFields().get("spanId");
            return fallback != null ? fallback.toString() : null;
        }
        return null;
    }

    private String resolveResultStatus(EventClass eventClass) {
        return switch (eventClass) {
            case START -> "NULL";
            case SUCCESS -> "SUCCESS";
            case FAILURE -> "FAILURE";
        };
    }

    private void putIfNotNull(Map<String, Object> event, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String s && s.isEmpty()) {
            return;
        }
        event.put(key, value);
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

    private void addStaticFields(Map<String, Object> event,
                                 String eventCode,
                                 EventClass eventClass) {
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