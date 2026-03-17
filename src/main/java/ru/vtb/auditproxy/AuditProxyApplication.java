package ru.vtb.auditproxy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan("ru.vtb.auditproxy.config")
public class AuditProxyApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuditProxyApplication.class, args);
    }

}
