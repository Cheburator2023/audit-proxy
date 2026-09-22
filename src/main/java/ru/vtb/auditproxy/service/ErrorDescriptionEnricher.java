package ru.vtb.auditproxy.service;

import ru.vtb.auditproxy.dto.AuditRequest;
import ru.vtb.auditproxy.dto.EventClass;
import ru.vtb.omni.audit.lib.api.FieldsConstant;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Обогащает oper_description события аудита согласно шаблонам
 * 43_1_СС_Аудит_Описание_схем_данных_СУМ:
 *
 *  - для FAILURE: "<oper_description> : <errorMessage>";
 *  - для SUCCESS: добавляет [model=<modelAlias|modelId>] [staff_id=<staffId>].
 */
@Slf4j
@Component
public class ErrorDescriptionEnricher {

    private static final String KEY_ERROR_MESSAGE = "errorMessage";
    private static final String KEY_MODEL_ID = "modelId";
    private static final String KEY_MODEL_ALIAS = "modelAlias";
    private static final String KEY_STAFF_ID = "staffId";

    public void enrich(Map<String, Object> event,
                       AuditRequest request,
                       Map<String, Object> additionalFields) {

        Object currentDescription = event.get(FieldsConstant.OPER_DESCRIPTION_FIELD_NAME);

        if (request.getEventClass() == EventClass.FAILURE) {
            enrichFailure(event, currentDescription, additionalFields);
        } else if (request.getEventClass() == EventClass.SUCCESS) {
            enrichSuccess(event, currentDescription, additionalFields);
        }
    }

    private void enrichFailure(Map<String, Object> event,
                               Object currentDescription,
                               Map<String, Object> additionalFields) {

        Object errorMessage = additionalFields.get(KEY_ERROR_MESSAGE);
        if (errorMessage == null || errorMessage.toString().isEmpty()) {
            return;
        }

        String description = currentDescription != null && !currentDescription.toString().isEmpty()
                ? currentDescription + " : " + errorMessage
                : "Описание ошибки: " + errorMessage;

        event.put(FieldsConstant.OPER_DESCRIPTION_FIELD_NAME, description);
    }

    private void enrichSuccess(Map<String, Object> event,
                               Object currentDescription,
                               Map<String, Object> additionalFields) {

        Object modelId = additionalFields.get(KEY_MODEL_ID);
        Object modelAlias = additionalFields.get(KEY_MODEL_ALIAS);
        Object staffId = additionalFields.get(KEY_STAFF_ID);

        String modelRef = modelAlias != null ? modelAlias.toString()
                : (modelId != null ? modelId.toString() : null);

        if (modelRef == null && (staffId == null || staffId.toString().isEmpty())) {
            return;
        }

        StringBuilder builder = new StringBuilder();
        if (currentDescription != null && !currentDescription.toString().isEmpty()) {
            builder.append(currentDescription);
        }

        if (modelRef != null) {
            appendPart(builder, "[model=" + modelRef + "]");
        }
        if (staffId != null && !staffId.toString().isEmpty()) {
            appendPart(builder, "[staff_id=" + staffId + "]");
        }

        event.put(FieldsConstant.OPER_DESCRIPTION_FIELD_NAME, builder.toString());
    }

    private void appendPart(StringBuilder builder, String part) {
        if (builder.length() > 0) {
            builder.append(' ');
        }
        builder.append(part);
    }
}