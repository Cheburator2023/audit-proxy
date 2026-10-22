package ru.vtb.auditproxy.service;

import ru.vtb.auditproxy.dto.AuditRequest;
import ru.vtb.omni.audit.core.properties.AuditLibProperties;
import ru.vtb.omni.audit.lib.api.FieldsConstant;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Маппит поля инициатора события аудита в Map события.
 * Соответствует разделу «Инициатор события» в
 * 43_1_СС_Аудит_Описание_схем_данных_СУМ и контракту 24. vtb:
 * initiator_sub, initiator_channel,
 * initiator_realm, initiator_userSessionId, initiator_clientAppId,
 * context_url, context_method, context_nearbyNodeIp, initiator_sourceIp,
 * staff_id, staff_roleId.
 */
@Slf4j
@Component
public class InitiatorFieldMapper {

    private static final String FIELD_CONTEXT_URL = "context_url";
    private static final String FIELD_CONTEXT_METHOD = "context_method";
    private static final String FIELD_CONTEXT_NEARBY_NODE_IP = "context_nearbyNodeIp";
    private static final String FIELD_INITIATOR_SOURCE_IP = "initiator_sourceIp";
    private static final String FIELD_STAFF_ID = "staff_id";
    private static final String FIELD_STAFF_ROLE_ID = "staff_roleId";

    /**
     * Допустимые значения initiator_channel согласно документации
     */
    private static final Set<String> ALLOWED_CHANNELS = Set.of(
            "internal", "external", "apic", "service", "smmb", "SMBGuest", "trust"
    );

    public void mapInitiator(AuditRequest request,
                             Map<String, Object> event,
                             AuditLibProperties auditLibProperties) {

        Map<String, Object> initiator = request.getInitiator();

        if (initiator == null) {
            event.put(FieldsConstant.LOGIN_FIELD_NAME, auditLibProperties.getSub());
            event.put(FieldsConstant.CHANNEL_FIELD_NAME, resolveChannel(null, auditLibProperties));
            return;
        }

        putIfNotNull(event, FieldsConstant.LOGIN_FIELD_NAME,
                initiator.getOrDefault("sub", auditLibProperties.getSub()));

        putIfNotNull(event, FieldsConstant.CHANNEL_FIELD_NAME,
                resolveChannel(initiator, auditLibProperties));

        putIfNotNull(event, FIELD_CONTEXT_URL, initiator.get("url"));
        putIfNotNull(event, FIELD_CONTEXT_METHOD, initiator.get("method"));
        putIfNotNull(event, FIELD_INITIATOR_SOURCE_IP, initiator.get("sourceIp"));
        putIfNotNull(event, FIELD_CONTEXT_NEARBY_NODE_IP, initiator.get("nearbyNodeIp"));
        putIfNotNull(event, FieldsConstant.EVENT_USER_SESSION_ID, initiator.get("userSessionId"));
        putIfNotNull(event, FieldsConstant.INITIATOR_REALM, initiator.get("realm"));
        putIfNotNull(event, FieldsConstant.CLIENT_APP_ID, initiator.get("clientAppId"));
        putIfNotNull(event, FIELD_STAFF_ID, initiator.get("staffId"));
        putIfNotNull(event, FIELD_STAFF_ROLE_ID, initiator.get("staffRoleId"));
    }

    /**
     * Разрешает initiator_channel по иерархии:
     *  1. значение от основного приложения (JWT/x-channel);
     *  2. audit.default-resolver.channel;
     *  3. "service" (безопасное значение по умолчанию, допустимое документацией).
     *
     * Если полученное значение не входит в {@link #ALLOWED_CHANNELS},
     * логируется предупреждение и используется "service".
     */
    private Object resolveChannel(Map<String, Object> initiator, AuditLibProperties auditLibProperties) {
        Object channel = null;

        if (initiator != null) {
            channel = initiator.get("channel");
        }

        if (channel == null || channel.toString().isEmpty()) {
            channel = auditLibProperties.getChannel();
        }

        if (channel == null || channel.toString().isEmpty()) {
            return "service";
        }

        String channelStr = channel.toString();
        if (!ALLOWED_CHANNELS.contains(channelStr)) {
            log.warn("Недопустимое значение initiator_channel: '{}'. Допустимые значения: {}. Используется 'service'.",
                    channelStr, ALLOWED_CHANNELS);
            return "service";
        }
        return channelStr;
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
}