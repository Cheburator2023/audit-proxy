import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    java
    id("org.springframework.boot") version "3.4.5"
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
        name = "vtbCorporate"
        url = uri("https://maven.repo-ci.sfera.inno.local/repository/tsau-maven-pub")
        credentials {
            username = project.findProperty("nexusUser") as String? ?: System.getenv("NEXUS_USER")
            password = project.findProperty("nexusPass") as String? ?: System.getenv("NEXUS_PASS")
        }
    }
}

dependencyManagement {
    imports {
        // BOM Spring Boot
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.4.5")
        // BOM Аудита 2.0
        mavenBom("ru.vtb.omni:tsau-audit-lib-bom:7.0.0.5")
    }
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Драйвер метрик Prometheus
    implementation("io.micrometer:micrometer-registry-prometheus")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Библиотеки СС Аудит 2.0 – версии управляются BOM tsau-audit-lib-bom
    implementation("ru.vtb.omni:tsau-audit-lib-in-memory-storage")
    implementation("ru.vtb.omni:tsau-audit-lib-kafka-sender")
    implementation("ru.vtb.omni:tsau-audit-lib-servlet-context")
    implementation("ru.vtb.omni:tsau-audit-lib-blocking-context")
    implementation("ru.vtb.omni:tsau-audit-lib-metric")
    implementation("ru.vtb.omni:tsau-audit-lib-starter")
    implementation("ru.vtb.omni:tsau-audit-lib-validation")

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
