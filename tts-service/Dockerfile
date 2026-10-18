# Multi-stage Dockerfile for tts-service
FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

FROM eclipse-temurin:17-jre-alpine AS runner
# Install curl for healthcheck and ffmpeg/ffprobe for audio quality validation
RUN apk add --no-cache curl ffmpeg \
    && addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
RUN mkdir -p /app/data /app/storage && chown -R app:app /app
USER app
EXPOSE 8085
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"
HEALTHCHECK --interval=10s --timeout=3s --retries=5 --start-period=20s \
  CMD curl -f http://localhost:8085/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
