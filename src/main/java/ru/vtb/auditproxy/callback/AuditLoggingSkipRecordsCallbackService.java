package ru.vtb.auditproxy.callback;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.vtb.omni.audit.lib.api.service.AuditSkipRecordsCallbackService;

import java.util.Map;

@Slf4j
@Component
public class AuditLoggingSkipRecordsCallbackService implements AuditSkipRecordsCallbackService {

    @Override
    public void onSkippingRecordCallback(Map<String, Object> value) {
        log.warn("Audit event was skipped (not sent to Kafka): {}", value);
    }

    @Override
    public void onExceptionSendInKafkaCallback(Exception ex) {
        log.error("Failed to send audit event to Kafka", ex);
    }
}