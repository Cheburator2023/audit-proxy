package ru.vtb.auditproxy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class AuditRequest {

    @NotBlank
    private String eventCode;

    @NotNull
    private EventClass eventClass;

    private String timestamp; // ISO 8601

    private Map<String, Object> additionalFields;

    // Явные геттеры/сеттеры для надёжности
    public String getEventCode() {
        return eventCode;
    }

    public void setEventCode(String eventCode) {
        this.eventCode = eventCode;
    }

    public EventClass getEventClass() {
        return eventClass;
    }

    public void setEventClass(EventClass eventClass) {
        this.eventClass = eventClass;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, Object> getAdditionalFields() {
        return additionalFields;
    }

    public void setAdditionalFields(Map<String, Object> additionalFields) {
        this.additionalFields = additionalFields;
    }
}