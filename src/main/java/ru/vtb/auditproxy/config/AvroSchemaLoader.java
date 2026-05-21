package ru.vtb.auditproxy.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import ru.vtb.omni.audit.core.avro.SchemaRepository;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class AvroSchemaLoader {

    private final SchemaRepository schemaRepository;

    @PostConstruct
    public void loadSchemas() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:avro-schemas/*.avsc");
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null) continue;
                String schemaType = filename.substring(0, filename.lastIndexOf('.'));
                String schemaContent = new BufferedReader(
                        new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))
                        .lines()
                        .collect(Collectors.joining("\n"));
                // Для эмулятора используем версию 1
                int version = 1;
                log.info("Loading local Avro schema: type={}, version={}", schemaType, version);
                schemaRepository.updateSchema(schemaType, version, schemaContent);
            }
        } catch (Exception e) {
            log.error("Failed to load Avro schemas from resources", e);
        }
    }
}