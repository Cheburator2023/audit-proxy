package ru.vtb.auditproxy.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;

@Slf4j
@Configuration
public class UncaughtExceptionHandlerConfig {

    @PostConstruct
    public void registerHandler() {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            log.error("Uncaught exception in thread {}: {}", thread.getName(), throwable.getMessage(), throwable);
        });
        log.info("Uncaught exception handler registered");
    }
}