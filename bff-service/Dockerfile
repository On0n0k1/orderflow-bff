FROM gradle:9.7.1-jdk21-alpine AS builder
WORKDIR /home/gradle/project

COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle ./gradle
RUN gradle --no-daemon dependencies || true

COPY src ./src
RUN gradle --no-daemon buildFatJar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /home/gradle/project/build/libs/bff-service-all.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
