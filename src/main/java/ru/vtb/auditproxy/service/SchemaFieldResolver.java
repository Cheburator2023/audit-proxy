package ru.vtb.auditproxy.service;

import ru.vtb.omni.audit.core.avro.SchemaRepository;
import ru.vtb.omni.audit.core.avro.SchemaModel;
import ru.vtb.omni.audit.lib.api.event.AuditEventCode;
import ru.vtb.omni.audit.lib.config.AuditEventDescriptionObject;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Разрешает Avro-схему и её актуальную версию для заданного кода события.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaFieldResolver {

    private final SchemaRepository schemaRepository;
    private final AuditEventDescriptionObject auditEventDescriptionObject;

    public SchemaResolution resolve(String eventCode) {
        String schema = auditEventDescriptionObject.getAuditEventCodeList().stream()
                .filter(ec -> ec.getEventCode().equals(eventCode))
                .findFirst()
                .map(AuditEventCode::getSchema)
                .orElseThrow(() -> new IllegalArgumentException("Unknown event code: " + eventCode));

        SchemaModel lastSchema = schemaRepository.getLastSchema(schema);
        int version;
        if (lastSchema != null) {
            version = lastSchema.getVersion();
            log.debug("Found latest schema for type '{}' with version {}", schema, version);
        } else {
            log.warn("Schema not found for type '{}', using fallback version 1", schema);
            version = 1;
        }
        return new SchemaResolution(schema, version);
    }

    public record SchemaResolution(String schema, int version) {
    }
}