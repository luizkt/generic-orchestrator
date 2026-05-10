# Generic Orchestrator

Orquestrador genérico baseado em Java 21 LTS + Spring Boot 3.4 LTS + Gradle.

## Visão Geral

Define e executa fluxos de orquestração declarados em YAML. Cada fluxo descreve:

- **Contrato de entrada**: validação de campos com tipos e regras (similar a Bean Validation)
- **Integrações**: passos executados em ordem — chamadas HTTP e publicação em filas (RabbitMQ, Kafka, SQS)

Os workflows em si **não são armazenados pelo orquestrador** — vivem no [`service-portal-manager`](../service-portal-manager/), que serve o YAML cru via API. O orquestrador consome via Redis (cache 1h) com fallback no Manager.

> ⚠️ **Mudança de escopo (refactor):** o orquestrador **não tem mais integração de banco de dados**. Workflows não fazem mais operações genéricas em MongoDB durante a execução — persistência de domínio é responsabilidade dos serviços downstream chamados via integrações HTTP/QUEUE.

---

## Stack

| Componente | Versão |
|---|---|
| Java | 21 LTS |
| Spring Boot | 3.4.5 LTS |
| Gradle | Kotlin DSL |
| RabbitMQ | 3 (management) |
| Kafka | Bitnami 3.7 (KRaft) |
| AWS SQS | LocalStack 3 (dev) / AWS SDK v2 (prod) |
| Segurança | Spring Security + JWT HS512 (jjwt 0.12.6) |
| HTTP client | Spring WebFlux WebClient (Netty) |
| Resiliência | Resilience4j 2.2 (retry + circuit breaker) |
| Cache | Spring Cache + Redis (Lettuce) — workflows ativos com TTL 1h |

---

## Estrutura do Projeto

```
src/main/java/com/orchestrator/
├── config/
│   ├── properties/
│   │   ├── OrchIntegrationsProperties.java        # @ConfigurationProperties("orch-integrations")
│   │   ├── KafkaIntegrationProperties.java        # Config de uma instância Kafka
│   │   ├── RabbitMqIntegrationProperties.java     # Config de uma instância RabbitMQ
│   │   ├── ManagerProperties.java                 # @ConfigurationProperties("orchestrator.manager")
│   │   ├── RetryConfigurationProperties.java      # Backoff, tentativas e status retryáveis
│   │   └── CircuitBreakerConfigurationProperties.java
│   ├── KafkaMultiInstanceConfig.java              # Cria Map<id, KafkaTemplate>
│   ├── RabbitMqMultiInstanceConfig.java           # Cria Map<id, RabbitTemplate>
│   ├── HttpResilienceConfig.java                  # RetryRegistry + CircuitBreakerRegistry
│   ├── ManagerWebClientConfig.java                # WebClient dedicado para o Manager
│   ├── RedisCacheConfig.java                      # @EnableCaching + RedisCacheManager TTL 1h
│   ├── SqsConfig.java
│   ├── WebClientConfig.java
│   └── JacksonConfig.java
├── manager/                                       # Integração com service-portal-manager
│   ├── ManagerAuthService.java                    # Login server-to-server + cache de token
│   ├── ManagerWorkflowClient.java                 # GET /manager/workflows/active e .../yaml
│   ├── WorkflowSummary.java                       # DTO da lista de ativos
│   ├── WorkflowCacheService.java                  # @Cacheable: cache miss → Manager → parse
│   └── WorkflowCacheWarmer.java                   # @ApplicationReadyEvent: popula o Redis
├── exception/
│   └── RetriableHttpException.java                # Sinaliza status retryáveis (500, 429, 408)
├── domain/
│   ├── model/                                # FlowDefinition, IntegrationDefinition, etc.
│   └── execution/                            # FlowExecutionContext, FlowExecutionResult
├── integration/
│   ├── http/HttpIntegrationExecutor.java
│   └── queue/
│       ├── QueueIntegrationExecutor.java
│       ├── KafkaPublisher.java
│       ├── RabbitMqPublisher.java
│       └── SqsPublisher.java
├── service/                                  # Orquestração, validação, template resolver
├── controller/                               # OrchestrationController (CRUD migrou para o Manager)
├── security/                                 # JWT filter, AuthController
└── GenericOrchestratorApplication.java
docs/
└── example-flow.yml
```

---

## Configuração

### `application.yml` — configurações principais

```yaml
spring:
  rabbitmq:                          # Usado pelo Spring AMQP (autoconfigure)
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
  kafka:                             # Usado pelo Spring Kafka (autoconfigure)
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
  data:
    redis:                             # Cache de workflows
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      timeout: 2000ms
  cache:
    type: redis

orchestrator:
  manager:                             # Service Portal Manager — fonte dos workflows
    base-url: ${MANAGER_URL:http://localhost:8082}
    username: ${MANAGER_USERNAME:admin}
    password: ${MANAGER_PASSWORD:admin}
    timeout-ms: ${MANAGER_TIMEOUT_MS:5000}
  cache:
    workflows:
      ttl-seconds: ${WORKFLOWS_CACHE_TTL_SECONDS:3600}
      warm-up-enabled: ${WORKFLOWS_WARM_UP_ENABLED:true}

orch-integrations:                   # Configuração multi-instância dos brokers
  rabbitmqs:
    - id: rabbitmq-notifier
      host: ${RABBITMQ_HOST:localhost}
      port: ${RABBITMQ_PORT:5672}
      username: ${RABBITMQ_USERNAME:guest}
      password: ${RABBITMQ_PASSWORD:guest}
  kafkas:
    - id: kafka-user-tracking
      bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS_1:localhost:9092}
      producer:
        key-serializer: org.apache.kafka.common.serialization.StringSerializer
        value-serializer: org.apache.kafka.common.serialization.StringSerializer
    - id: kafka-notifier
      bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS_2:localhost:9093}
      producer:
        key-serializer: org.apache.kafka.common.serialization.StringSerializer
        value-serializer: org.apache.kafka.common.serialization.StringSerializer

  # Resiliência das integrações HTTP — Retry com backoff exponencial
  retry-configuration:
    maximum-attempts: 3                # Total de tentativas (inclui a primeira)
    delay: 100                         # Delay inicial em ms
    backoff-multiplier: 2.0            # Multiplicador entre tentativas
    maximum-delay: 10000               # Teto do delay (ms)
    retryable-http-condition: [500, 429, 408]  # Status que disparam retry
    timeout: 1000                      # Timeout default por tentativa quando o YAML não define

  # Circuit breaker — protege o downstream de chamadas em falha
  circuit-breaker-configuration:
    sliding-window-size: 10            # Janela de N chamadas (ou segundos, se TIME)
    sliding-window-size-type: COUNT    # COUNT | TIME
    minimum-number-calls: 100          # Mínimo antes de calcular taxa de falha
    failure-rate-threshold: 50         # % de falhas para abrir o circuito
    wait-duration-open-state: 30       # Segundos no estado OPEN antes de HALF_OPEN
    permitted-calls-open-state: 10     # Chamadas-teste no estado HALF_OPEN
    automatic-transition-half-open-enabled: true
    slow-call-duration-threshold: 800  # ms — chamadas mais lentas contam como falha
```

### Multi-instância de brokers (`orch-integrations`)

O orquestrador suporta múltiplas instâncias de Kafka e RabbitMQ configuradas simultaneamente. O **match** entre o passo do workflow e o broker é feito pelo campo `id`:

- O `id` da integração no YAML do workflow deve corresponder ao `id` configurado em `orch-integrations.kafkas` ou `orch-integrations.rabbitmqs`.
- Se nenhuma configuração for encontrada para o `id`, a execução falha com mensagem clara indicando qual `id` está ausente.

### Cache de workflows (Redis) + integração com Manager

Workflows não são mais armazenados localmente — o orquestrador **consome** do `service-portal-manager` (porta 8082):

```
[startup]                                 [execução]
  ┌──────────────────────┐                  ┌─────────────────────────┐
  │ WorkflowCacheWarmer  │                  │ POST /api/orchestrate.. │
  │ ApplicationReadyEvt  │                  │       │                 │
  └─────────┬────────────┘                  │       ▼                 │
            │                               │ WorkflowCacheService    │
            ▼                               │   .load(flowId, versao) │
  ┌──────────────────────┐                  │       │                 │
  │ Manager              │                  │       ▼                 │
  │ /workflows/active    │                  │   Redis lookup          │
  │ → para cada ativo:   │                  │       │                 │
  │   .load(id, ver)     │                  │ HIT? ─yes→ FlowDefinition│
  └─────────┬────────────┘                  │   no                    │
            │                               │       ▼                 │
            ▼                               │ Manager /yaml + parse   │
  ┌──────────────────────┐                  │       │                 │
  │ Redis populado       │                  │       ▼                 │
  └──────────────────────┘                  │ Cacheia + executa       │
                                            └─────────────────────────┘
```

- **Cache key**: `workflows::{flowId}_{versao}` (Spring Cache + Redis serialização Jackson)
- **TTL**: 1h por padrão (configurável via `orchestrator.cache.workflows.ttl-seconds`)
- **Conteúdo cacheado**: `FlowDefinition` parseado (não o YAML cru) — economiza CPU em execuções repetidas
- **Invalidação**: somente por TTL. Se um workflow for atualizado no Manager, o orquestrador pode usar versão obsoleta por até 1h. Para invalidar manualmente, usar `WorkflowCacheService.evict(flowId, versao)` ou `evictAll()`
- **Warm-up**: opcional via `orchestrator.cache.workflows.warm-up-enabled` (default `true`). Se desabilitado ou se o Manager estiver indisponível na inicialização, o orquestrador faz lazy-load no primeiro request — não bloqueia o startup
- **Falhas**: cache miss + Manager 404 → `FlowNotFoundException` no fluxo de execução; cache miss + Manager 5xx → `WebClientResponseException` propagado

### Resiliência HTTP — Retry + Circuit Breaker (Resilience4j)

Toda integração HTTP passa por uma cadeia `Retry( CircuitBreaker( httpCall ) )` em [HttpIntegrationExecutor](src/main/java/com/orchestrator/integration/http/HttpIntegrationExecutor.java):

- **Retry** com backoff exponencial — `delay × backoffMultiplier^(n-1)` limitado por `maximumDelay`. Status retryáveis (`retryable-http-condition`) são propagados como `RetriableHttpException` e disparam retry; demais respostas HTTP (`WebClientResponseException`) propagam sem retry. Erros de rede/timeout que não são `WebClientResponseException` também são retentados.
- **Circuit Breaker** — uma instância por `integration.id`. O estado transita entre `CLOSED → OPEN → HALF_OPEN → CLOSED` conforme `failure-rate-threshold` e `slow-call-duration-threshold`. Quando aberto, o `CallNotPermittedException` é capturado e devolvido como `IntegrationExecutionException("Circuit breaker aberto para: …")` — sem retry.
- Configuração padrão **única** para todas as integrações HTTP, externalizada em `orch-integrations.retry-configuration` e `orch-integrations.circuit-breaker-configuration`.

Cobertura de testes da feature: **100%** (435 instruções) — gate `jacocoTestCoverageVerification` exige ≥ 95%. Rode com:

```bash
./gradlew test jacocoTestReport jacocoTestCoverageVerification
# Relatório HTML: build/reports/jacoco/test/html/index.html
```

---

## Profiles Spring

| Profile | Arquivo | Uso |
|---|---|---|
| default | `application.yml` | Desenvolvimento local |
| `docker` | `application-docker.yml` | Infraestrutura via `docker compose up` |

**Ativar o profile docker:**

```bash
./gradlew bootRun --args='--spring.profiles.active=docker'
# ou
java -jar generic-orchestrator.jar --spring.profiles.active=docker
```

O profile `docker` configura:
- Redis em `redis:6379`
- RabbitMQ em `rabbitmq:5672`
- Kafka em `kafka:9092`
- LocalStack SQS em `http://localstack:4566` (credenciais `test/test`)
- service-portal-manager em `http://manager:8082`

---

## Infraestrutura local (`docker-compose-service-portal.yml`)

```bash
docker compose -f docker-compose-service-portal.yml up -d
```

Sobe os serviços:

| Serviço | Porta | Descrição |
|---|---|---|
| **Redis 7** | 6379 | Cache de workflows ativos do orquestrador (TTL 1h) |
| RabbitMQ 3 | 5672 / 15672 | Broker + Management UI |
| Kafka (Bitnami 3.7, KRaft) | 9092 | Broker |
| LocalStack 3 (opcional) | 4566 | Emulador AWS SQS |
| **WireMock** | **18080** (admin/host) | Simulador de APIs HTTP externas — alias `api.exemplo.com` na rede `portal` |
| **service-portal-manager** | 8082 | Dono da collection `workflows` — fonte de YAML para o orquestrador |
| MongoDB 7 | 27017 | (Não consumido pelo orquestrador) usado apenas pelo `service-portal-manager` |

### WireMock — APIs externas simuladas

Os fluxos com integrações HTTP que apontam para `http://api.exemplo.com/...` (ex.: [docs/example-flow.yml](docs/example-flow.yml)) caem no container WireMock automaticamente: o `docker-compose-service-portal.yml` registra `api.exemplo.com` como **alias de rede** do container, então o DNS interno do Docker resolve a chamada — sem alterar URLs nos workflows.

Mappings em [`wiremock/mappings/`](../wiremock/mappings/) (raiz do repo). Para inspecionar via host:

```bash
curl http://localhost:18080/clientes/ABC123      # 200 com cliente fictício
curl http://localhost:18080/clientes/foo         # 404 (fallback)
curl http://localhost:18080/__admin/mappings     # lista de stubs
curl http://localhost:18080/__admin/requests     # últimas chamadas recebidas
```

Detalhes e como adicionar novos stubs: [`wiremock/README.md`](../wiremock/README.md).

---

## Como Executar

```bash
# 1. Subir a infraestrutura
docker compose up -d

# 2. Build e testes
./gradlew build

# 3. Apenas testes unitários
./gradlew test

# 4. Apenas testes de integração
./gradlew test --tests "*IT"

# 5. Rodar com profile docker
./gradlew bootRun --args='--spring.profiles.active=docker'
```

---

## API

Todos os endpoints (exceto `/api/auth/**` e `/actuator/health`) exigem `Authorization: Bearer <token>`.

### Autenticação

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}'
```

### Endpoints

> **Atenção:** após o refactor para o `service-portal-manager`, **CRUD de fluxos saiu do orquestrador.** O orquestrador agora só executa — gerenciamento (POST/GET/PUT/DELETE) acontece no Manager (porta 8082).

| Método | Endpoint | Descrição |
|---|---|---|
| POST | `/api/auth/login` | Gera token JWT (server-to-server) |
| POST | `/api/orchestrate/{version}/{flowId}` | Executa fluxo com payload JSON. Carrega o `FlowDefinition` via Redis cache (cache miss → consulta Manager) |
| GET | `/actuator/health` | Health check (público) |

Para criar/atualizar/listar fluxos, ver [`service-portal-manager/README.md`](../service-portal-manager/README.md).

---

## Formato do Workflow (YAML)

```yaml
fluxo:
  id: "meu-fluxo"           # Obrigatório, único por (id, versao) no Manager
  descricao: "Descrição"
  versao: "1.0.0"
  ativo: true

  contrato:                  # Validação do payload de entrada
    campos:
      - nome: "campo"
        tipo: STRING         # STRING | INTEGER | DECIMAL | BOOLEAN | OBJECT | ARRAY
        obrigatorio: true
        validacoes:
          - tipo: NOT_BLANK
          - tipo: PATTERN
            valor: "^[0-9]{11}$"
            mensagem: "Formato inválido"

  integracoes:
    - id: "buscar-dados"     # ID identifica o passo e faz match com orch-integrations
      ordem: 1
      tipo: HTTP             # HTTP | QUEUE
      continuarEmErro: false
      http: ...

    - id: "kafka-user-tracking"   # ID deve existir em orch-integrations.kafkas
      ordem: 2
      tipo: QUEUE
      provider: KAFKA             # Nível principal: KAFKA | RABBITMQ | SQS
      continuarEmErro: true
      queue:
        topic: "meu-topico"
        mensagemTemplate: |
          {"documento":"{{contrato.documento}}"}
```

### Tipos de campo (`FieldType`)

`STRING` | `INTEGER` | `DECIMAL` | `BOOLEAN` | `OBJECT` | `ARRAY`

### Regras de validação (`ValidationType`)

| Tipo | Descrição | Parâmetros extras |
|---|---|---|
| `NOT_BLANK` | String não vazia | — |
| `NOT_EMPTY` | Coleção não vazia | — |
| `NOT_NULL` | Valor não nulo | — |
| `PATTERN` | Regex | `valor` (regex), `mensagem` |
| `SIZE` | Tamanho mínimo/máximo | `min`, `max` |
| `MIN` | Valor numérico mínimo | `valor` |
| `MAX` | Valor numérico máximo | `valor` |
| `POSITIVE` | Número positivo | — |
| `NEGATIVE` | Número negativo | — |
| `EMAIL` | Formato de e-mail | — |

### Integração HTTP

```yaml
- id: "buscar-cliente"
  ordem: 1
  tipo: HTTP
  continuarEmErro: false
  http:
    url: "http://localhost:8080/v1/clientes/{{contrato.documento}}/cursos"
    metodo: GET              # GET | POST | PUT | DELETE | PATCH
    headers:
      Accept: "application/json"
    bodyTemplate: |          # Opcional, para POST/PUT
      {"nome":"{{contrato.nome}}"}
    timeout: 5000            # ms, padrão 30000
    mapeamentoResposta:
      campoOrigem: "cursos"  # Extrai do response JSON
      campoDestino: "cursos" # Armazena no contexto de execução
```

### Integração QUEUE

```yaml
- id: "kafka-user-tracking"        # Deve corresponder ao id em orch-integrations.kafkas
  ordem: 2
  tipo: QUEUE
  provider: KAFKA                  # Obrigatório no nível principal
  continuarEmErro: true
  queue:
    topic: "meu-topico"
    key: "chave-opcional"
    mensagemTemplate: |
      {"documento":"{{contrato.documento}}"}

- id: "rabbitmq-notifier"          # Deve corresponder ao id em orch-integrations.rabbitmqs
  ordem: 3
  tipo: QUEUE
  provider: RABBITMQ
  continuarEmErro: true
  queue:
    exchange: "minha.exchange"
    routingKey: "evento.criado"
    persistente: true
    mensagemTemplate: |
      {"evento":"CRIADO"}

- id: "notificar-sqs"
  ordem: 4
  tipo: QUEUE
  provider: SQS
  continuarEmErro: true
  queue:
    queueUrl: "https://sqs.us-east-1.amazonaws.com/123/minha-fila"
    mensagemTemplate: |
      {"evento":"CRIADO"}
```

> **Persistência de dados de domínio**: o orquestrador **não tem mais integração de banco de dados**. Para gravar registros (pedidos, eventos, etc.), os workflows chamam APIs HTTP de serviços downstream (passo `tipo: HTTP`, método `POST`/`PUT`). Veja [docs/example-flow.yml](docs/example-flow.yml) para o exemplo `salvar-pedido` via `POST /pedidos`.

### Resolução de templates

| Expressão | Descrição |
|---|---|
| `{{contrato.campo}}` | Campo do payload de entrada |
| `{{contrato.objeto.campo}}` | Campo aninhado (dot-notation) |
| `{{integracoes.stepId.campo}}` | Resultado de um passo anterior |
| `{{now()}}` | Timestamp atual (ISO-8601) |

---

## Exemplo completo

Veja [docs/example-flow.yml](docs/example-flow.yml).

**Caso de uso — consulta de cursos de um aluno:**

```yaml
fluxo:
  id: "consulta-cursos-aluno"
  descricao: "Consulta cursos e notifica via Kafka"
  versao: "1.0.0"
  ativo: true

  contrato:
    campos:
      - nome: "aluno"
        tipo: OBJECT
        obrigatorio: true
        objeto:
          campos:
            - nome: "nome"
              tipo: STRING
              obrigatorio: true
              validacoes:
                - tipo: NOT_BLANK
            - nome: "documento"
              tipo: STRING
              obrigatorio: true
              validacoes:
                - tipo: NOT_BLANK
                - tipo: PATTERN
                  valor: "^[0-9]{11}$"
                  mensagem: "Documento deve conter 11 dígitos numéricos"
            - nome: "tipo_documento"
              tipo: STRING
              obrigatorio: true
              validacoes:
                - tipo: NOT_BLANK

  integracoes:
    - id: "buscar-cursos"
      ordem: 1
      tipo: HTTP
      continuarEmErro: false
      http:
        url: "http://localhost:8080/v1/clientes/{{contrato.aluno.documento}}/cursos"
        metodo: GET
        headers:
          Accept: "application/json"
        timeout: 5000
        mapeamentoResposta:
          campoOrigem: "cursos"
          campoDestino: "cursos"

    - id: "kafka-user-tracking"     # match com orch-integrations.kafkas[id=kafka-user-tracking]
      ordem: 2
      tipo: QUEUE
      provider: KAFKA
      continuarEmErro: true
      queue:
        topic: "consultas-cursos-clientes"
        mensagemTemplate: |
          {"documento":"{{contrato.aluno.documento}}"}
```

**Payload de execução (note `version` no path):**

```bash
curl -X POST http://localhost:8080/api/orchestrate/v1/consulta-cursos-aluno \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "aluno": {
      "nome": "John Joe",
      "documento": "12345678910",
      "tipo_documento": "CPF"
    }
  }'
```

**Resposta:**

```json
{
  "executionId": "uuid-aqui",
  "flowId": "consulta-cursos-aluno",
  "status": "SUCCESS",
  "resultado": {
    "buscar-cursos": {
      "cursos": [...]
    },
    "kafka-user-tracking": {
      "provider": "KAFKA",
      "integrationId": "kafka-user-tracking",
      "topic": "consultas-cursos-clientes",
      "partition": 0,
      "offset": 42,
      "published": true
    }
  },
  "iniciadoEm": "2026-05-09T10:00:00",
  "finalizadoEm": "2026-05-09T10:00:00.123"
}
```

---

## Persistência

O orquestrador **não tem dependência direta de banco de dados**. Workflows são fornecidos pelo [`service-portal-manager`](../service-portal-manager/) (que possui a collection `workflows` no MongoDB) e ficam em cache no Redis com TTL 1h.

Persistência de dados de domínio (pedidos, eventos, registros) acontece nos serviços downstream chamados pelos passos `HTTP`/`QUEUE` do workflow.
