package ru.vtb.auditproxy.serializer;

import ru.vtb.omni.audit.core.avro.SchemaRepository;
import ru.vtb.omni.audit.kafka.client.AuditReliableProducer;
import ru.vtb.omni.audit.kafka.config.properties.OmniAuditKafkaProperties;
import ru.vtb.omni.audit.kafka.sender.context.servlet.ServletKafkaProducerInitializer;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.ssl.SslBundles;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Расширение {@link ServletKafkaProducerInitializer} из библиотеки tsau-audit-lib
 * (версия 8.0.0.16+). Использует собственный {@link AuditMapSerializer}, который
 * корректно сериализует MAP-поля ({@code additionalParams}) и ARRAY-поля
 * ({@code event_techCodes}).
 * Регистрируется в
 * {@link ru.vtb.auditproxy.config.AuditKafkaProducerOverrideConfig} с {@code @Primary},
 * чтобы подменить бин библиотеки без её модификации.
 *
 * <p>ВАЖНО (8.0.0.16): начиная с этой версии конструктор
 * {@code ServletKafkaProducerInitializer} требует {@link SslBundles} — они
 * используются для применения SSL-конфигурации из Spring Boot. Avro-схемы
 * встроены в библиотеку, обращения к топику {@code audit-v2-schema} больше нет.</p>
 *
 * <p>ВАЖНО (Java-контракт): родительский конструктор
 * {@code ServletKafkaProducerInitializer} вызывает {@code get()}
 * (а значит и наш {@link #initialize()}) до того, как тело конструктора
 * наследника будет выполнено. Поэтому {@link SchemaRepository} передаётся
 * через статический holder.</p>
 */
@Slf4j
public class AuditProxyKafkaProducerInitializer extends ServletKafkaProducerInitializer {

    private static final AtomicReference<SchemaRepository> SCHEMA_HOLDER = new AtomicReference<>();

    public static void setSchemaRepository(SchemaRepository schemaRepository) {
        SCHEMA_HOLDER.set(schemaRepository);
    }

    public AuditProxyKafkaProducerInitializer(OmniAuditKafkaProperties defaultKafkaProperties,
                                              SchemaRepository schemaRepository,
                                              SslBundles sslBundles) throws Exception {
        super(defaultKafkaProperties, schemaRepository, sslBundles);
        log.info("AuditProxyKafkaProducerInitializer initialized with AuditMapSerializer");
    }

    @Override
    protected Producer<String, Map<String, Object>> initialize() {
        SchemaRepository schemaRepository = SCHEMA_HOLDER.get();
        if (schemaRepository == null) {
            throw new IllegalStateException(
                    "SchemaRepository is not initialized in AuditProxyKafkaProducerInitializer");
        }

        AuditMapSerializer serializer = new AuditMapSerializer(schemaRepository);
        Map<String, Object> kafkaProperties = getKafkaProperties();
        kafkaProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        kafkaProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, serializer.getClass().getName());

        boolean sliderEnabled = "true".equalsIgnoreCase(
                String.valueOf(kafkaProperties.getOrDefault("slider.enable", "true")).toLowerCase(Locale.ROOT));

        if (sliderEnabled) {
            kafkaProperties.putIfAbsent(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG,
                    "ru.vtb.omni.audit.kafka.client.AuditSlider");
            kafkaProperties.putIfAbsent("partitioner.ignore.keys", "true");
            kafkaProperties.putIfAbsent(ProducerConfig.ACKS_CONFIG, "all");
            kafkaProperties.putIfAbsent(ProducerConfig.RETRIES_CONFIG, "1");
            kafkaProperties.putIfAbsent(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "10000");
            kafkaProperties.putIfAbsent(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, "20000");
            return new AuditReliableProducer<>(kafkaProperties, new StringSerializer(), serializer);
        } else {
            return new KafkaProducer<>(kafkaProperties, new StringSerializer(), serializer);
        }
    }
}