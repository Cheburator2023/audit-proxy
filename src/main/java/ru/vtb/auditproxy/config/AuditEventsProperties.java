package ru.vtb.auditproxy.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "audit-events")
public class AuditEventsProperties {
    private List<AuditEventCode> auditEventCodeList = new ArrayList<>();
    private Map<String, Boolean> blockSettings = new HashMap<>();

    @Data
    public static class AuditEventCode {
        private String eventCode;
        private String schema;
    }
}