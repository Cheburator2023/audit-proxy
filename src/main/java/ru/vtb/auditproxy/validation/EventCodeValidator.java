package ru.vtb.auditproxy.validation;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.vtb.auditproxy.config.AuditEventsProperties;

import java.util.Set;
import java.util.stream.Collectors;

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
    }

    public boolean isValid(String code) {
        return validCodes.contains(code);
    }
}