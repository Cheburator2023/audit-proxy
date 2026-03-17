package ru.vtb.auditproxy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuditResponse {
    private String status;
    private String message;
}