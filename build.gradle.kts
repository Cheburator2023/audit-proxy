import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    java
    id("org.springframework.boot") version "3.5.11"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "ru.vtb"
version = "0.0.1-SNAPSHOT"
description = "audit-proxy"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenLocal()
    mavenCentral()
    maven {
        url = uri("https://sfera.inno.local/repo-ci-maven/repositories/browse/tsau-maven-pub/")
        credentials {
            username = project.findProperty("nexusUser") as String? ?: System.getenv("VGabbasov")
            password = project.findProperty("nexusPass") as String? ?: System.getenv("!@#$%^&Gabb987Inno")
        }
    }
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.11")
        // mavenBom("ru.vtb.omni:omni-dependencies:7.0.0") // версия BOM платформы
    }
}

dependencies {
    // Spring Boot starters
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Библиотеки аудита (обязательные)
    // implementation("ru.vtb.omni:tsau-audit-lib-in-memory-storage")
    // implementation("ru.vtb.omni:tsau-audit-lib-kafka-sender")
    // implementation("ru.vtb.omni:tsau-audit-lib-servlet-context") // для резолверов HTTP
    // implementation("ru.vtb.omni:tsau-audit-lib-blocking-context") // для блокирующих событий
    // implementation("ru.vtb.omni:tsau-audit-lib-metric")           // для метрик

    // Драйвер метрик Prometheus
    implementation("io.micrometer:micrometer-registry-prometheus")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Тестирование
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<BootJar> {
    archiveFileName.set("audit-sidecar.jar")
}