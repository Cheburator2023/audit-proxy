package ru.vtb.auditproxy.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import ru.vtb.auditproxy.dto.AuditRequest;
import ru.vtb.auditproxy.dto.AuditResponse;
import ru.vtb.auditproxy.dto.EventClass;
import ru.vtb.auditproxy.exception.AuditSendException;
import ru.vtb.omni.audit.core.properties.AuditLibProperties;
import ru.vtb.omni.audit.core.properties.AuditMsProperties;
import ru.vtb.omni.audit.core.sender.AuditEventSender;
import ru.vtb.omni.audit.lib.api.FieldsConstant;
import ru.vtb.omni.audit.lib.api.enums.AudLibEventClass;
import ru.vtb.omni.audit.lib.config.AuditEventDescriptionObject;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private static final String FIELD_CONTEXT_RECIPIENT_IP = "context_recipientIp";
    private static final String FIELD_ADDITIONAL_PARAMS = "additionalParams";
    private static final String FALLBACK_TRACE_ID_KEY = "traceId";
    private static final String FALLBACK_SPAN_ID_KEY = "spanId";

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

        // context_namespace / context_podName: записываются ТОЛЬКО при непустом значении.
        // Согласно 43_1_СС_Аудит… «Если не используется — не заполняется».
        putIfNotNull(event, FieldsConstant.NAMESPACE_FIELD_NAME, getPodNamespace());
        putIfNotNull(event, FieldsConstant.POD_NAME_FIELD_NAME, getPodName());

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
        Map<String, Object> additionalFields = request.getAdditionalFields() != null
                ? request.getAdditionalFields()
                : Collections.emptyMap();

        // Явное переопределение oper_description (если пришло от поставщика)
        Object operDescription = additionalFields.get(FieldsConstant.OPER_DESCRIPTION_FIELD_NAME);
        if (operDescription != null && !operDescription.toString().isEmpty()) {
            event.put(FieldsConstant.OPER_DESCRIPTION_FIELD_NAME, operDescription);
        }

        // Обогащение oper_description:
        //  - подстановка {placeholders} в шаблон из YAML;
        //  - fallback для FAILURE: добавление errorMessage в конец.
        // Вызывается ВСЕГДА — даже если additionalFields пустой, потому что
        // плейсхолдер {staff_id} может быть подставлен из event.staff_id.
        errorDescriptionEnricher.enrich(event, request, additionalFields);

        // additionalParams пишем только если есть данные.
        // traceId/spanId исключаются — они уже в context_traceId/context_spanId.
        if (!additionalFields.isEmpty()) {
            Map<String, Object> filtered = filterAdditionalFields(additionalFields, request);
            Map<String, String> encoded = additionalParamsEncoder.encode(filtered);
            event.put(FIELD_ADDITIONAL_PARAMS, encoded);
        }

        log.debug("Built audit event: schema='{}', version='{}'",
                schemaResolution.schema(), schemaResolution.version());
        return event;
    }

    /**
     * Исключает traceId/spanId из additionalParams, если они уже переданы
     * через заголовок traceparent (context_traceId/context_spanId).
     */
    private Map<String, Object> filterAdditionalFields(Map<String, Object> source, AuditRequest request) {
        if (!StringUtils.hasText(request.getTraceId()) && !StringUtils.hasText(request.getSpanId())) {
            return source;
        }
        Map<String, Object> result = new HashMap<>(source);
        if (StringUtils.hasText(request.getTraceId())) {
            result.remove(FALLBACK_TRACE_ID_KEY);
        }
        if (StringUtils.hasText(request.getSpanId())) {
            result.remove(FALLBACK_SPAN_ID_KEY);
        }
        return result;
    }

    /**
     * TraceId: приоритет — заголовок traceparent, fallback — additionalFields.traceId.
     */
    private String resolveTraceId(AuditRequest request) {
        if (StringUtils.hasText(request.getTraceId())) {
            return request.getTraceId();
        }
        if (request.getAdditionalFields() != null) {
            Object fallback = request.getAdditionalFields().get(FALLBACK_TRACE_ID_KEY);
            if (fallback != null && !fallback.toString().isEmpty()) {
                return fallback.toString();
            }
        }
        return null;
    }

    /**
     * SpanId: приоритет — заголовок traceparent, fallback — additionalFields.spanId.
     */
    private String resolveSpanId(AuditRequest request) {
        if (StringUtils.hasText(request.getSpanId())) {
            return request.getSpanId();
        }
        if (request.getAdditionalFields() != null) {
            Object fallback = request.getAdditionalFields().get(FALLBACK_SPAN_ID_KEY);
            if (fallback != null && !fallback.toString().isEmpty()) {
                return fallback.toString();
            }
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

    /**
     * Возвращает имя namespace пода или null, если переменная окружения
     * не задана или пуста.
     */
    private String getPodNamespace() {
        String envName = auditLibProperties.getPodNamespaceEnvName();
        if (!StringUtils.hasText(envName)) {
            return null;
        }
        String value = System.getenv(envName);
        return StringUtils.hasText(value) ? value : null;
    }

    /**
     * Возвращает имя пода или null, если переменная окружения
     * не задана или пуста.
     */
    private String getPodName() {
        String envName = auditLibProperties.getPodNameEnvName();
        if (!StringUtils.hasText(envName)) {
            return null;
        }
        String value = System.getenv(envName);
        return StringUtils.hasText(value) ? value : null;
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