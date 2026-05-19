package ru.vtb.auditproxy.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.vtb.auditproxy.dto.AuditRequest;
import ru.vtb.auditproxy.dto.AuditResponse;
import ru.vtb.auditproxy.exception.AuditSendException;
import ru.vtb.omni.audit.lib.api.template.context.servlet.ServletAuditTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final ServletAuditTemplate auditTemplate;

    @Override
    public AuditResponse sendAuditEvent(AuditRequest request) {
        Map<String, Object> methodParams = new HashMap<>();
        if (request.getAdditionalFields() != null) {
            methodParams.putAll(request.getAdditionalFields());
        }
        if (request.getCorrelationId() != null) {
            methodParams.put("event_correlationId", request.getCorrelationId());
        }
        if (request.getInitiator() != null) {
            methodParams.putAll(request.getInitiator());
        }

        log.debug("Sending audit event via AuditTemplate: eventCode={}, class={}, correlationId={}",
                request.getEventCode(), request.getEventClass(), request.getCorrelationId());

        try {
            Object result = auditTemplate.execute(
                    request.getEventCode(),
                    methodParams,
                    () -> {
                        log.debug("Audit action executed for eventCode={}, class={}",
                                request.getEventCode(), request.getEventClass());
                        return "OK";
                    }
            );
            log.info("Audit event processed successfully: eventCode={}, correlationId={}",
                    request.getEventCode(), request.getCorrelationId());
            return new AuditResponse("accepted", "Audit event processed");
        } catch (Exception e) {
            log.error("Failed to send audit event: eventCode={}, correlationId={}",
                    request.getEventCode(), request.getCorrelationId(), e);
            throw new AuditSendException("Audit send failed: " + e.getMessage(), e);
        }
    }
}