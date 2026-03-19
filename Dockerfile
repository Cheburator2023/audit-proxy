FROM gradle:8.5-jdk21 AS builder

ARG NEXUS_USER
ARG NEXUS_PASS

WORKDIR /app

COPY build.gradle.kts settings.gradle.kts ./

RUN gradle dependencies --no-daemon \
    -PnexusUser=$NEXUS_USER \
    -PnexusPass=$NEXUS_PASS

COPY src ./src
RUN gradle bootJar --no-daemon \
    -PnexusUser=$NEXUS_USER \
    -PnexusPass=$NEXUS_PASS

FROM builder AS extractor
WORKDIR /app
RUN java -Djarmode=layertools -jar build/libs/*.jar extract

FROM bellsoft/liberica-openjdk-debian:21 AS runtime

RUN addgroup --system --gid 1001 appgroup && \
    adduser --system --uid 1001 --gid 1001 --no-create-home appuser

WORKDIR /app

COPY --from=extractor --chown=appuser:appgroup /app/dependencies/ ./
COPY --from=extractor --chown=appuser:appgroup /app/spring-boot-loader/ ./
COPY --from=extractor --chown=appuser:appgroup /app/snapshot-dependencies/ ./
COPY --from=extractor --chown=appuser:appgroup /app/application/ ./

EXPOSE 8081

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]