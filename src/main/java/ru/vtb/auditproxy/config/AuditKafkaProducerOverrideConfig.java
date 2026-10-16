package ru.vtb.auditproxy.config;

import ru.vtb.auditproxy.serializer.AuditProxyKafkaProducerInitializer;
import ru.vtb.omni.audit.core.avro.SchemaRepository;
import ru.vtb.omni.audit.kafka.config.properties.OmniAuditKafkaProperties;
import ru.vtb.omni.audit.kafka.sender.context.servlet.ServletKafkaProducerInitializer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Переопределяет бин {@link ServletKafkaProducerInitializer} из библиотеки
 * tsau-audit-lib (версия 8.0.0.16+), чтобы использовать
 * {@link ru.vtb.auditproxy.serializer.AuditMapSerializer}, корректно
 * сериализующий MAP-поля ({@code additionalParams}) и ARRAY-поля
 * ({@code event_techCodes}).
 *
 * <p>В 8.0.0.16 {@link ServletKafkaProducerInitializer} требует
 * {@link SslBundles} — они используются для применения SSL-конфигурации
 * через Spring Boot ({@code audit.kafka.ssl.bundle}).</p>
 */
@Slf4j
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AuditKafkaProducerOverrideConfig {

    @Bean("auditProxyServletKafkaProducerInitializer")
    @Primary
    public ServletKafkaProducerInitializer auditProxyServletKafkaProducerInitializer(
            @Qualifier("omniAuditKafkaProps") OmniAuditKafkaProperties defaultKafkaProperties,
            SchemaRepository schemaRepository,
            SslBundles sslBundles) throws Exception {

        log.info("Registering AuditProxyKafkaProducerInitializer (with AuditMapSerializer) as @Primary");
        AuditProxyKafkaProducerInitializer.setSchemaRepository(schemaRepository);
        return new AuditProxyKafkaProducerInitializer(defaultKafkaProperties, schemaRepository, sslBundles);
    }
}