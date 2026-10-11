FROM eclipse-temurin:21-alpine AS build
WORKDIR /app
COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle.kts settings.gradle.kts ./
RUN chmod +x ./gradlew && ./gradlew dependencies --no-daemon -q
COPY src ./src
RUN ./gradlew bootJar --no-daemon -q

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
# Unprivileged user (spec 001-migracion-aws RF-006).
RUN addgroup -S app && adduser -S -G app -H app
# Size the heap from the container memory limit (ECS task memory).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
COPY --from=build --chown=app:app /app/build/libs/*.jar app.jar
USER app
# Exec form: java is PID 1 and receives SIGTERM, so Spring's graceful shutdown runs (RF-005).
ENTRYPOINT ["java", "-jar", "app.jar"]
