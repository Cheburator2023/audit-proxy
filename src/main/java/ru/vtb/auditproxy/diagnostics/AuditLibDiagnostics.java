package ru.vtb.auditproxy.diagnostics;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.errors.TopicExistsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.LivenessState;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import ru.vtb.omni.audit.core.properties.AuditLibProperties;

import java.util.Collections;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
public class AuditLibDiagnostics {

    private final AuditLibProperties auditKafkaProperties;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${audit.kafka.topic:audit-v2}")
    private String auditTopic;

    @Value("${audit.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${audit.kafka.security.protocol}")
    private String securityProtocol;

    private static final String META_TOPIC = "audit-v2-meta";
    private static final String SCHEMA_TOPIC = "audit-v2-schema";

    @PostConstruct
    public void checkKafkaConnectivity() {
        log.info("Starting Kafka connectivity check for dev environment...");
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                String.join(",", bootstrapServers));
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "10000");
        props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "10000");

        // Для PLAINTEXT не требуется SSL
        int maxRetries = 5;
        int retryDelay = 2000;
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try (AdminClient admin = AdminClient.create(props)) {
                // Проверяем доступность брокера
                Set<String> topics = admin.listTopics().names().get(15, TimeUnit.SECONDS);
                log.info("Kafka connection successful. Available topics: {}", topics);

                // Проверяем и создаём необходимые топики
                ensureTopicExists(admin, META_TOPIC);
                ensureTopicExists(admin, SCHEMA_TOPIC);
                ensureTopicExists(admin, auditTopic);

                log.info("All required topics exist or were created.");
                return;
            } catch (TimeoutException | InterruptedException | ExecutionException e) {
                lastException = e;
                log.warn("Kafka connectivity check attempt {}/{} failed: {}", attempt, maxRetries, e.getMessage());
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(retryDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (Exception e) {
                lastException = e;
                log.error("Unexpected error during Kafka connectivity check", e);
                break;
            }
        }

        log.error("Kafka connectivity check FAILED after {} attempts: {}", maxRetries, lastException.getMessage(), lastException);
        // Не убиваем приложение, но переводим в состояние BROKEN (liveness)
        AvailabilityChangeEvent.publish(eventPublisher, this, LivenessState.BROKEN);
    }

    private void ensureTopicExists(AdminClient admin, String topicName) {
        try {
            // Проверяем существование топика
            Set<String> existing = admin.listTopics().names().get(5, TimeUnit.SECONDS);
            if (existing.contains(topicName)) {
                log.info("Topic '{}' already exists", topicName);
                return;
            }
            // Создаём топик с 1 партицией и фактором репликации 1
            NewTopic newTopic = new NewTopic(topicName, 1, (short) 1);
            admin.createTopics(Collections.singletonList(newTopic)).all().get(5, TimeUnit.SECONDS);
            log.info("Topic '{}' created successfully", topicName);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof TopicExistsException) {
                log.info("Topic '{}' already exists (concurrent creation)", topicName);
            } else {
                log.error("Failed to create topic '{}'", topicName, e);
            }
        } catch (Exception e) {
            log.error("Failed to ensure existence of topic '{}'", topicName, e);
        }
    }
}
