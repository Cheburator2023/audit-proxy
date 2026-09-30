package ru.vtb.auditproxy.service;

import ru.vtb.auditproxy.dto.AuditRequest;
import ru.vtb.auditproxy.dto.EventClass;
import ru.vtb.omni.audit.lib.api.FieldsConstant;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Обогащает oper_description события аудита согласно требованиям
 * 43_1_СС_Аудит_Описание_схем_данных_СУМ.
 *
 * Поддерживает два механизма:
 *   1. Подстановка плейсхолдеров {key} в шаблон oper_description
 *      (шаблон задаётся в application.yml в auditEventSuccess/auditEventFailure).
 *      Алиасы контекста: {model}, {staff_id}, {task_id}, {report}, {error_message}.
 *   2. Fallback для FAILURE: если после подстановки текст ошибки не попал
 *      в сообщение, он добавляется в конец через ": ".
 *
 * Примеры шаблонов (application.yml):
 *   SUCCESS: "Модель [{model}] успешно создана пользователем [{staff_id}]"
 *   FAILURE: "Ошибка создания модели пользователем [{staff_id}]: {error_message}"
 *
 * Алиасы:
 *   {model}         → additionalFields.modelAlias || additionalFields.modelId
 *   {task_id}       → additionalFields.taskId
 *   {report}        → additionalFields.reportName || additionalFields.report
 *   {staff_id}      → additionalFields.staffId || event.staff_id
 *   {error_message} → additionalFields.errorMessage
 *   {key}           → additionalFields.key (прямое значение)
 */
@Slf4j
@Component
public class ErrorDescriptionEnricher {

    private static final String KEY_ERROR_MESSAGE = "errorMessage";
    private static final String KEY_MODEL_ID = "modelId";
    private static final String KEY_MODEL_ALIAS = "modelAlias";
    private static final String KEY_STAFF_ID = "staffId";
    private static final String KEY_TASK_ID = "taskId";
    private static final String KEY_REPORT_NAME = "reportName";
    private static final String KEY_REPORT = "report";
    private static final String FIELD_STAFF_ID = "staff_id";

    /**
     * Точка входа: обогащает oper_description в event.
     *
     * @param event             карта события аудита (изменяется in-place)
     * @param request           исходный запрос (eventClass используется для FAILURE-фолбэка)
     * @param additionalFields  бизнес-данные от основного приложения
     */
    public void enrich(Map<String, Object> event,
                       AuditRequest request,
                       Map<String, Object> additionalFields) {

        Map<String, Object> safeAdditional = additionalFields != null
                ? additionalFields
                : new HashMap<>();

        Map<String, String> placeholders = buildPlaceholders(event, safeAdditional);

        Object currentDescription = event.get(FieldsConstant.OPER_DESCRIPTION_FIELD_NAME);
        String resolved = resolveTemplate(currentDescription, placeholders);

        if (request.getEventClass() == EventClass.FAILURE) {
            resolved = enrichFailureFallback(resolved, safeAdditional);
        }

        if (resolved != null) {
            event.put(FieldsConstant.OPER_DESCRIPTION_FIELD_NAME, resolved);
        }
    }

    /**
     * Формирует контекст подстановки для шаблонов oper_description.
     * Включает как «сырые» ключи additionalFields, так и алиасы.
     */
    private Map<String, String> buildPlaceholders(Map<String, Object> event,
                                                  Map<String, Object> additionalFields) {
        Map<String, String> placeholders = new HashMap<>();

        // «Сырые» значения additionalFields (примитивы превращаем в строки)
        additionalFields.forEach((key, value) -> {
            if (value != null) {
                placeholders.put(key, String.valueOf(value));
            }
        });

        // Алиас {model}: modelAlias → modelId
        Object modelAlias = additionalFields.get(KEY_MODEL_ALIAS);
        Object modelId = additionalFields.get(KEY_MODEL_ID);
        if (modelAlias != null && !modelAlias.toString().isEmpty()) {
            placeholders.put("model", modelAlias.toString());
        } else if (modelId != null && !modelId.toString().isEmpty()) {
            placeholders.put("model", modelId.toString());
        }

        // Алиас {task_id}
        Object taskId = additionalFields.get(KEY_TASK_ID);
        if (taskId != null && !taskId.toString().isEmpty()) {
            placeholders.put("task_id", taskId.toString());
        }

        // Алиас {report}: reportName → report
        Object reportName = additionalFields.get(KEY_REPORT_NAME);
        Object report = additionalFields.get(KEY_REPORT);
        if (reportName != null && !reportName.toString().isEmpty()) {
            placeholders.put("report", reportName.toString());
        } else if (report != null && !report.toString().isEmpty()) {
            placeholders.put("report", report.toString());
        }

        // Алиас {staff_id}: additionalFields.staffId → event.staff_id
        Object staffId = additionalFields.get(KEY_STAFF_ID);
        if (staffId != null && !staffId.toString().isEmpty()) {
            placeholders.put("staff_id", staffId.toString());
        } else {
            Object eventStaffId = event.get(FIELD_STAFF_ID);
            if (eventStaffId != null && !eventStaffId.toString().isEmpty()) {
                placeholders.put("staff_id", eventStaffId.toString());
            }
        }

        // Алиас {error_message}
        Object errorMessage = additionalFields.get(KEY_ERROR_MESSAGE);
        if (errorMessage != null && !errorMessage.toString().isEmpty()) {
            placeholders.put("error_message", errorMessage.toString());
        }

        return placeholders;
    }

    /**
     * Подставляет {key} в шаблон. Если ключ отсутствует в placeholders —
     * плейсхолдер остаётся в тексте как есть (для диагностики).
     */
    private String resolveTemplate(Object template, Map<String, String> placeholders) {
        if (template == null) {
            return null;
        }
        String templateStr = template.toString();
        if (templateStr.isEmpty()) {
            return templateStr;
        }

        StringBuilder result = new StringBuilder(templateStr.length());
        int i = 0;
        int len = templateStr.length();

        while (i < len) {
            char c = templateStr.charAt(i);
            if (c == '{') {
                int end = templateStr.indexOf('}', i + 1);
                if (end > 0) {
                    String key = templateStr.substring(i + 1, end);
                    String value = placeholders.get(key);
                    if (StringUtils.hasText(value)) {
                        result.append(value);
                    } else {
                        // Оставляем плейсхолдер как есть — видно, каких данных не хватает.
                        result.append(templateStr, i, end + 1);
                    }
                    i = end + 1;
                    continue;
                }
            }
            result.append(c);
            i++;
        }

        return result.toString();
    }

    /**
     * FAILURE-фолбэк: если после подстановки плейсхолдеров текст ошибки
     * не попал в сообщение (например, шаблон без {error_message}),
     * добавляем его в конец через ": ".
     */
    private String enrichFailureFallback(String resolved, Map<String, Object> additionalFields) {
        Object errorMessage = additionalFields.get(KEY_ERROR_MESSAGE);
        if (errorMessage == null || errorMessage.toString().isEmpty()) {
            return resolved;
        }

        String errorText = errorMessage.toString();

        if (resolved != null && resolved.contains(errorText)) {
            // Плейсхолдер {error_message} уже подставлен.
            return resolved;
        }

        if (StringUtils.hasText(resolved)) {
            return resolved + ": " + errorText;
        }
        return "Описание ошибки: " + errorText;
    }
}