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
}