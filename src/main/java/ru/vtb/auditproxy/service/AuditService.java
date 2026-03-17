package ru.vtb.auditproxy.service;

import ru.vtb.auditproxy.dto.AuditRequest;
import ru.vtb.auditproxy.dto.AuditResponse;

public interface AuditService {
    AuditResponse sendAuditEvent(AuditRequest request);
}