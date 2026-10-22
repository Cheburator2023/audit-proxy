package ru.vtb.auditproxy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;
import ru.vtb.omni.audit.kafka.avro.AvroKafkaConfig;

@SpringBootApplication
@ConfigurationPropertiesScan("ru.vtb.auditproxy.config")
@EnableKafka
@EnableScheduling
@Import(AvroKafkaConfig.class)
public class AuditProxyApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuditProxyApplication.class, args);
    }

}
