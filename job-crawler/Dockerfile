# Stage 1: Build
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app
COPY gradle/ gradle/
COPY gradlew build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test

# Stage 2: Runtime (Playwright 의존성 포함)
FROM mcr.microsoft.com/playwright/java:v1.49.0-noble

WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

# uploads 디렉토리 생성
RUN mkdir -p /app/uploads /app/logs

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
