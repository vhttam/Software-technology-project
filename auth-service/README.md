# auth-service

Independent Spring Boot 3.5 / Java 17 application. Run locally with .\mvnw.cmd spring-boot:run or package with .\mvnw.cmd clean verify.

The default H2 database runs in MSSQL compatibility mode so CI and local smoke checks need no database. Set DB_URL, DB_USER, DB_PASSWORD, DB_DRIVER to use this service's SQL Server database. Flyway owns the schema. Set AMQP_ENABLED=true only when RabbitMQ is available; GET /health is a liveness endpoint and does not check dependencies.

Configuration is in src/main/resources/application.yml. Internal routes require the caller-specific X-Service-Token. See docs/contract-matrix.md for endpoint and event coverage.
