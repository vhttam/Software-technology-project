# content-service

Independent Spring Boot 3.5 / Java 17 application. Build and run via Docker (e.g. `docker compose up -d content-service` or Dockerfile), or run tests inside Docker with `docker run --rm -v "${PWD}:/app" -w /app maven:3.9-eclipse-temurin-17-alpine mvn test`.

The default H2 database runs in MSSQL compatibility mode so CI and local smoke checks need no database. Set DB_URL, DB_USER, DB_PASSWORD, DB_DRIVER to use this service's SQL Server database. Flyway owns the schema. Set AMQP_ENABLED=true only when RabbitMQ is available; GET /health is a liveness endpoint and does not check dependencies.

Configuration is in src/main/resources/application.yml. Internal routes require the caller-specific X-Service-Token. See docs/contract-matrix.md for endpoint and event coverage.
