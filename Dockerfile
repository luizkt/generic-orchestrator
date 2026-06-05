# syntax=docker/dockerfile:1
# ─── Build ────────────────────────────────────────────────────────────────────
FROM gradle:8.10-jdk21-alpine AS build
WORKDIR /app
COPY . .
RUN --mount=type=cache,target=/root/.gradle \
    gradle bootJar --no-daemon -x test

# ─── Runtime ──────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN apk add --no-cache curl

COPY --from=build /app/build/libs/generic-orchestrator.jar app.jar

# ── Infraestrutura ─────────────────────────────────────────────────────────────
# MongoDB foi removido — workflows não fazem mais operações em bancos arbitrários
# durante a execução. Persistência acontece via integrações HTTP/QUEUE downstream.
ENV REDIS_HOST=localhost \
    REDIS_PORT=6379 \
    REDIS_PASSWORD="" \
    REDIS_TIMEOUT_MS=2000 \
    RABBITMQ_HOST=localhost \
    RABBITMQ_PORT=5672 \
    RABBITMQ_USERNAME=guest \
    RABBITMQ_PASSWORD=guest \
    KAFKA_BOOTSTRAP_SERVERS=localhost:9092 \
    KAFKA_BOOTSTRAP_SERVERS_1=localhost:9092 \
    AWS_REGION=us-east-1 \
    AWS_SQS_ENDPOINT="" \
    AWS_ACCESS_KEY_ID="" \
    AWS_SECRET_ACCESS_KEY=""

# ── Service Portal Manager (consulta de workflows) ────────────────────────────
ENV MANAGER_URL=http://localhost:8082 \
    MANAGER_USERNAME=admin \
    MANAGER_PASSWORD=admin \
    MANAGER_TIMEOUT_MS=5000 \
    WORKFLOWS_CACHE_TTL_SECONDS=3600 \
    WORKFLOWS_WARM_UP_ENABLED=true

# ── Segurança ──────────────────────────────────────────────────────────────────
ENV JWT_SECRET=CHANGE_ME_PLEASE_THIS_IS_A_DEV_ONLY_SECRET_AT_LEAST_64_CHARS_LONG_FOR_HS512 \
    JWT_EXPIRATION=3600 \
    JWT_ISSUER=generic-orchestrator

# ── HTTP client ────────────────────────────────────────────────────────────────
ENV HTTP_CONNECT_TIMEOUT=5000 \
    HTTP_READ_TIMEOUT=30000

# ── Servidor ───────────────────────────────────────────────────────────────────
ENV SERVER_PORT=8080 \
    SPRING_PROFILES_ACTIVE="" \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
