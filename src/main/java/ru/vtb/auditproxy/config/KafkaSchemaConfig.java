package ru.vtb.auditproxy.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.vtb.omni.audit.core.avro.SchemaRepository;
import ru.vtb.omni.audit.kafka.avro.SchemaKafkaReceiver;

/**
 * Конфигурация для подключения слушателя топика схем аудита.
 * Использует готовый компонент SchemaKafkaReceiver из библиотеки tsau-audit-lib-kafka-sender.
 */
@Configuration
public class KafkaSchemaConfig {

    @Bean
    public SchemaKafkaReceiver schemaKafkaReceiver(SchemaRepository schemaRepository) {
        return new SchemaKafkaReceiver(schemaRepository);
    }
}