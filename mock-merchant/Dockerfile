# syntax=docker/dockerfile:1
# 빌드 컨텍스트는 레포 루트다: docker build -f mock-merchant/Dockerfile .
FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY core/build.gradle.kts ./core/
COPY mock-merchant/build.gradle.kts ./mock-merchant/
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon :mock-merchant:dependencies > /dev/null
COPY mock-merchant/src ./mock-merchant/src
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon :mock-merchant:bootJar -x test \
    && cp mock-merchant/build/libs/*.jar /workspace/app.jar

FROM eclipse-temurin:25-jre-noble
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/app.jar ./app.jar
USER ubuntu
EXPOSE 9001
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=5 \
    CMD curl -fsS http://localhost:9001/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
