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

    public List<AuditEventCode> getAuditEventCodeList() {
        return auditEventCodeList;
    }

    public void setAuditEventCodeList(List<AuditEventCode> auditEventCodeList) {
        this.auditEventCodeList = auditEventCodeList;
    }

    public Map<String, Boolean> getBlockSettings() {
        return blockSettings;
    }

    public void setBlockSettings(Map<String, Boolean> blockSettings) {
        this.blockSettings = blockSettings;
    }

    @Data
    public static class AuditEventCode {
        private String eventCode;
        private String schema;
        private List<String> resolvers;
        private List<String> listResolvers;
        private List<String> excludeResolvers;

        public String getEventCode() {
            return eventCode;
        }

        public void setEventCode(String eventCode) {
            this.eventCode = eventCode;
        }

        public String getSchema() {
            return schema;
        }

        public void setSchema(String schema) {
            this.schema = schema;
        }

        public List<String> getResolvers() {
            return resolvers;
        }

        public void setResolvers(List<String> resolvers) {
            this.resolvers = resolvers;
        }

        public List<String> getListResolvers() {
            return listResolvers;
        }

        public void setListResolvers(List<String> listResolvers) {
            this.listResolvers = listResolvers;
        }

        public List<String> getExcludeResolvers() {
            return excludeResolvers;
        }

        public void setExcludeResolvers(List<String> excludeResolvers) {
            this.excludeResolvers = excludeResolvers;
        }
    }
}