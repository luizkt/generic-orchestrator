# Generic Orchestrator

Orquestrador genérico baseado em Java 21 LTS + Spring Boot 3.4 LTS + Gradle.

## Visão Geral

Define e executa fluxos de orquestração via YAML armazenado no MongoDB. Cada fluxo descreve:
- **Contrato de entrada**: validação tipo Swagger
- **Integrações**: HTTP, filas (RabbitMQ, Kafka, SQS) e banco MongoDB

## Stack

- Java 21 (LTS)
- Spring Boot 3.4.5 (LTS)
- Spring Security + JWT (HS512)
- MongoDB
- RabbitMQ + Kafka + AWS SQS
- WebClient (HTTP)
- Gradle Kotlin DSL

## Tipos de Fila Suportados

| Tipo            | Descrição                                  |
|-----------------|--------------------------------------------|
| `RABBITMQ`      | Publica via `RabbitTemplate`               |
| `KAFKA`         | Publica via `KafkaTemplate`                |
| `SQS`           | Publica via AWS SDK v2 (`SqsClient`)       |

## Endpoints

Todos os endpoints (exceto `/api/auth/**` e `/actuator/health`) exigem JWT no header `Authorization: Bearer <token>`.

| Método | Endpoint                          | Descrição                              |
|--------|-----------------------------------|----------------------------------------|
| POST   | `/api/auth/login`                 | Gera token JWT (user/pass: admin/admin)|
| POST   | `/api/flows`                      | Cadastra fluxo (corpo: YAML)           |
| GET    | `/api/flows/{flowId}`             | Busca fluxo ativo                      |
| PUT    | `/api/flows/{flowId}`             | Atualiza fluxo                         |
| DELETE | `/api/flows/{flowId}`             | Desativa fluxo                         |
| POST   | `/api/orchestrate/{flowId}`       | Executa fluxo com payload JSON         |

## Como executar

```bash
# Subir infra local (Mongo + Rabbit + Kafka + LocalStack/SQS)
docker compose up -d

# Build + testes
./gradlew build

# Apenas testes unitários
./gradlew test

# Apenas testes de integração
./gradlew test --tests "*IT"

# Rodar
./gradlew bootRun
```

## Login (para gerar JWT em dev)

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}'
```

## Exemplo de YAML

Veja `docs/example-flow.yml`.
