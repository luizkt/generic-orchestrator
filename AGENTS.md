# AGENTS.md — generic-orchestrator

Guia de contexto para agentes de IA trabalhando neste componente.

---

## Stack

| Item | Versão |
|---|---|
| Java | 21 (LTS) |
| Spring Boot | 3.4.5 (LTS) |
| Build | Gradle Kotlin DSL |
| HTTP client | Spring WebFlux WebClient (Netty) |
| Cache | Redis 7 via `spring-boot-starter-data-redis` + `@Cacheable` |
| Mensageria | RabbitMQ 3, Kafka (Bitnami KRaft), AWS SQS (LocalStack em dev) |
| Resiliência | Resilience4j 2.2.0 (Retry + CircuitBreaker) |
| Segurança | JWT HS512 via jjwt 0.12.6 — autenticação interna server-to-server |
| Porta | 8080 |

---

## Responsabilidade

O orquestrador **executa fluxos** definidos em YAML. Ele não persiste dados de domínio e não faz CRUD de workflows — essas responsabilidades pertencem ao `service-portal-manager`.

Fluxo de uma requisição:
```
POST /api/flows/{flowId}/versions/{version}/executions
  └─> valida contrato (campos obrigatórios, tipos, regex)
  └─> carrega FlowDefinition do Redis (ou busca no Manager em cache miss)
  └─> executa integrações em ordem: HTTP → QUEUE (Rabbit/Kafka/SQS)
  └─> retorna resultado consolidado
```

---

## Endpoints

| Método | Path | Auth | Descrição |
|---|---|---|---|
| `POST` | `/api/auth/tokens` | Público | Gera JWT (admin/admin) — retorna 201 |
| `POST` | `/api/flows/{flowId}/versions/{version}/executions` | Bearer JWT | Executa um fluxo |
| `GET` | `/actuator/health` | Público | Health check |
| `GET` | `/actuator/info` | Público | Info da aplicação |
| `GET` | `/actuator/metrics` | Público | Métricas |

O orquestrador **não expõe** endpoints de CRUD de workflows — esses ficam no Manager.

---

## Como rodar localmente

### Pré-requisitos

- Java 21 instalado
- Docker + Docker Compose

### Opção 1 — Só infraestrutura via Docker, app no host

```bash
cd generic-orchestrator
docker compose up -d          # sobe Redis, RabbitMQ, Kafka, MongoDB, LocalStack, WireMock, Manager
./gradlew bootRun             # conecta em localhost:* via portas mapeadas
```

### Opção 2 — Stack completa

```bash
# Na raiz do repositório
docker compose -f docker-compose-service-portal.yml up -d
```

---

## Como testar

```bash
cd generic-orchestrator

# Testes unitários
./gradlew test

# Testes unitários + relatório de cobertura
./gradlew test jacocoTestReport
# Relatório em: build/reports/jacoco/jacocoTestReport/html/index.html

# Verificação de cobertura (gate ≥ 95% INSTRUCTION nas classes da feature)
./gradlew jacocoTestCoverageVerification

# Build sem testes
./gradlew bootJar -x test

# Build da imagem Docker
docker build -t generic-orchestrator:local .
```

### Gate de cobertura JaCoCo

O gate cobre especificamente:
- `integration/http/HttpIntegrationExecutor` — retry + circuit breaker
- `config/HttpResilienceConfig`, `RetryConfigurationProperties`, `CircuitBreakerConfigurationProperties`
- `exception/RetriableHttpException`
- Pacote `manager/**` — cliente, cache e warm-up do Manager
- `config/RedisCacheConfig`, `ManagerWebClientConfig`, `ManagerProperties`

Cobertura atual: **99% INSTRUCTION** (842/843).

---

## Estrutura de pacotes relevante

```
src/main/java/com/orchestrator/
├── controller/
│   └── OrchestrationController        # POST /api/flows/{id}/versions/{v}/executions
├── security/
│   ├── AuthController                 # POST /api/auth/tokens
│   ├── JwtService                     # geração e validação de JWT
│   └── JwtAuthenticationFilter        # filtro de Bearer token
├── service/
│   ├── OrchestrationService           # coordena validação + execução de integrações
│   ├── ContractValidationService      # valida campos do contrato do fluxo
│   ├── TemplateResolverService        # resolve {{contract.x}} e {{integrations.step.field}}
│   └── YamlParserService              # deserializa YAML → FlowDefinition
├── integration/
│   ├── IntegrationExecutorFactory     # seleciona executor por IntegrationType
│   ├── http/HttpIntegrationExecutor   # HTTP com Retry + CircuitBreaker (Resilience4j)
│   └── queue/
│       ├── QueueIntegrationExecutor   # delega para Rabbit/Kafka/SQS por provider
│       ├── RabbitMqPublisher
│       ├── KafkaPublisher
│       └── SqsPublisher
├── manager/
│   ├── ManagerWorkflowClient          # GET /manager/flows?status=active e .../yaml
│   ├── ManagerAuthService             # login server-to-server no Manager, JWT em memória
│   ├── WorkflowCacheService           # @Cacheable("workflows") — carrega do Redis ou Manager
│   └── WorkflowCacheWarmer            # popula Redis no startup via ApplicationReadyEvent
├── domain/
│   ├── model/FlowDefinition           # modelo em memória (não persistido aqui)
│   └── execution/FlowExecutionContext # contexto acumulado durante a execução
└── config/
    ├── RedisCacheConfig               # TTL 1h, Jackson serializer com type info
    ├── HttpResilienceConfig           # Retry + CircuitBreaker compartilhados
    ├── KafkaMultiInstanceConfig       # múltiplas instâncias Kafka via orch-integrations
    └── RabbitMqMultiInstanceConfig    # múltiplas instâncias RabbitMQ via orch-integrations
```

---

## Decisões de design

**Sem persistência própria de workflows.** O orquestrador não tem `spring-boot-starter-data-mongodb`. Workflows são carregados do Manager via HTTP e cacheados no Redis por 1h. `IntegrationType.DATABASE` foi removido.

**Cache de workflows (Redis).** `WorkflowCacheService` é o ponto único de carregamento: tenta o Redis primeiro; em miss, busca o YAML do Manager e deserializa. `WorkflowCacheWarmer` pré-popula na subida. Invalidação é somente por TTL — para forçar, chame `evict(flowId, version)` ou `evictAll()` programaticamente.

**Auth server-to-server com Manager.** `ManagerAuthService` faz login em `POST /api/auth/tokens` do Manager e guarda o JWT em campo volátil. Token é renovado automaticamente quando expirado ou inválido. Credenciais via `orchestrator.manager.username/password`.

**Multi-instância de Kafka e RabbitMQ.** Configuradas via `orch-integrations.kafkas[]` e `orch-integrations.rabbitmqs[]` no `application.yml`, cada uma com um `id`. O campo `id` do passo de integração no YAML do fluxo faz o match.

**Retry + Circuit Breaker em HTTP.** Ordem de aplicação: `Retry(CircuitBreaker(httpCall))`. Status retryable: 500, 429, 408 (configurável). `CallNotPermittedException` não é retentada. Todos os parâmetros ficam em `orch-integrations.retry-configuration` e `orch-integrations.circuit-breaker-configuration`.

**Sem trocar WebClient.** WebFlux WebClient é o único cliente HTTP permitido — não usar RestTemplate.

---

## Variáveis de ambiente

| Variável | Padrão | Descrição |
|---|---|---|
| `SERVER_PORT` | `8080` | Porta do servidor |
| `SPRING_PROFILES_ACTIVE` | `` | Use `docker` para rodar no compose |
| `REDIS_HOST` | `localhost` | Host do Redis |
| `REDIS_PORT` | `6379` | Porta do Redis |
| `REDIS_PASSWORD` | `` | Senha do Redis (vazio em dev) |
| `RABBITMQ_HOST` | `localhost` | Host do RabbitMQ |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Broker Kafka padrão |
| `KAFKA_BOOTSTRAP_SERVERS_1` | `localhost:9092` | Broker Kafka da instância `kafka-user-tracking` |
| `MANAGER_URL` | `http://localhost:8082` | Base URL do service-portal-manager |
| `MANAGER_USERNAME` | `admin` | Credencial para o Manager |
| `MANAGER_PASSWORD` | `admin` | Credencial para o Manager |
| `WORKFLOWS_CACHE_TTL_SECONDS` | `3600` | TTL do cache de workflows no Redis |
| `WORKFLOWS_WARM_UP_ENABLED` | `true` | Pré-carrega Redis na subida |
| `JWT_SECRET` | (dev secret) | Segredo HS512 — **trocar em produção** |
| `JWT_EXPIRATION` | `3600` | Expiração do JWT em segundos |
| `AWS_REGION` | `us-east-1` | Região AWS para SQS |
| `AWS_SQS_ENDPOINT` | `` | Endpoint customizado (LocalStack: `http://localhost:4566`) |

---

## Arquivo de fluxo de exemplo

[docs/example-flow.yml](docs/example-flow.yml) — fluxo `create-order-v1` com os 5 tipos de integração suportados: HTTP GET, HTTP POST com `responseMapping`, QUEUE RabbitMQ, QUEUE Kafka, QUEUE SQS.

Em dev, `api.exemplo.com` resolve para o container WireMock via alias de rede Docker.

---

## Restrições

- Java 21 LTS, Spring Boot 3.4.5 LTS — não atualizar versões
- Gradle com Kotlin DSL
- Sem trocar WebClient por RestTemplate ou similares
- Parâmetros de retry e circuit breaker devem permanecer externalizados no `application.yml`
- O orquestrador não deve adquirir dependência direta com banco de dados
