package ru.vtb.auditproxy.validation;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.vtb.auditproxy.config.AuditEventsProperties;

import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventCodeValidator {
    private final AuditEventsProperties auditEventsProperties;
    private Set<String> validCodes;

    @PostConstruct
    public void init() {
        validCodes = auditEventsProperties.getAuditEventCodeList().stream()
                .map(AuditEventsProperties.AuditEventCode::getEventCode)
                .collect(Collectors.toSet());
        log.info("Loaded valid event codes: {}", validCodes);
    }

    public boolean isValid(String code) {
        boolean valid = validCodes.contains(code);
        if (!valid) {
            log.warn("Event code validation failed: {}", code);
        }
        return valid;
    }
}