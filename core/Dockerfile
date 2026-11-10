# syntax=docker/dockerfile:1
# 빌드 컨텍스트는 레포 루트다: docker build -f core/Dockerfile .
FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY core/build.gradle ./core/
COPY mock-merchant/build.gradle ./mock-merchant/
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon :core:dependencies > /dev/null
COPY core/src ./core/src
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon :core:bootJar -x test \
    && cp core/build/libs/*.jar /workspace/app.jar

FROM eclipse-temurin:25-jre-noble
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/app.jar ./app.jar
USER ubuntu
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=5 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
