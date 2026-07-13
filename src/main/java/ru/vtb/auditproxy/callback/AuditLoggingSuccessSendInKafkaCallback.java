package ru.vtb.auditproxy.callback;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.vtb.omni.audit.lib.api.enums.KafkaProducerType;
import ru.vtb.omni.audit.lib.api.service.AuditSuccessSendInKafkaCallback;

@Slf4j
@Component
public class AuditLoggingSuccessSendInKafkaCallback implements AuditSuccessSendInKafkaCallback {

    @Override
    public void onSuccessSendInKafkaCallback(KafkaProducerType kafkaProducerType) {
        log.info("Audit event successfully delivered to Kafka (producer: {})", kafkaProducerType);
    }
}