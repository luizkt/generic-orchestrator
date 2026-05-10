# Generic Orchestrator

Orquestrador genérico baseado em Java 21 LTS + Spring Boot 3.4 LTS + Gradle.

## Visão Geral

Define e executa fluxos de orquestração declarados em YAML, armazenados no MongoDB. Cada fluxo descreve:

- **Contrato de entrada**: validação de campos com tipos e regras (similar a Bean Validation)
- **Integrações**: passos executados em ordem — chamadas HTTP, publicação em filas (RabbitMQ, Kafka, SQS) e operações no MongoDB

---

## Stack

| Componente | Versão |
|---|---|
| Java | 21 LTS |
| Spring Boot | 3.4.5 LTS |
| Gradle | Kotlin DSL |
| MongoDB | 7 |
| RabbitMQ | 3 (management) |
| Kafka | Confluent 7.6 (cp-kafka) |
| AWS SQS | LocalStack 3 (dev) / AWS SDK v2 (prod) |
| Segurança | Spring Security + JWT HS512 (jjwt 0.12.6) |
| HTTP client | Spring WebFlux WebClient (Netty) |
| Resiliência | Resilience4j 2.2 (retry + circuit breaker) |

---

## Estrutura do Projeto

```
src/main/java/com/orchestrator/
├── config/
│   ├── properties/
│   │   ├── OrchIntegrationsProperties.java        # @ConfigurationProperties("orch-integrations")
│   │   ├── KafkaIntegrationProperties.java        # Config de uma instância Kafka
│   │   ├── RabbitMqIntegrationProperties.java     # Config de uma instância RabbitMQ
│   │   ├── RetryConfigurationProperties.java      # Backoff, tentativas e status retryáveis
│   │   └── CircuitBreakerConfigurationProperties.java
│   ├── KafkaMultiInstanceConfig.java              # Cria Map<id, KafkaTemplate>
│   ├── RabbitMqMultiInstanceConfig.java           # Cria Map<id, RabbitTemplate>
│   ├── HttpResilienceConfig.java                  # RetryRegistry + CircuitBreakerRegistry
│   ├── SqsConfig.java
│   ├── WebClientConfig.java
│   └── JacksonConfig.java
├── exception/
│   └── RetriableHttpException.java                # Sinaliza status retryáveis (500, 429, 408)
├── domain/
│   ├── model/                                # FlowDefinition, IntegrationDefinition, etc.
│   └── execution/                            # FlowExecutionContext, FlowExecutionResult
├── integration/
│   ├── http/HttpIntegrationExecutor.java
│   ├── queue/
│   │   ├── QueueIntegrationExecutor.java
│   │   ├── KafkaPublisher.java
│   │   ├── RabbitMqPublisher.java
│   │   └── SqsPublisher.java
│   └── database/DatabaseIntegrationExecutor.java
├── service/                                  # Orquestração, validação, template resolver
├── controller/                               # FlowDefinitionController, OrchestrationController
├── security/                                 # JWT filter, AuthController
└── GenericOrchestratorApplication.java
mongodb-workflows/
└── init-mongo.js                             # Script de init da collection no MongoDB
docs/
└── example-flow.yml
```

---

## Configuração

### `application.yml` — configurações principais

```yaml
spring:
  data:
    mongodb:
      uri: ${MONGODB_URI:mongodb://localhost:27017/generic-orchestrator}
      database: ${MONGODB_DATABASE:generic-orchestrator}
  rabbitmq:                          # Usado pelo Spring AMQP (autoconfigure)
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
  kafka:                             # Usado pelo Spring Kafka (autoconfigure)
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}

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
- MongoDB em `localhost:27017/generic-orchestrator`
- RabbitMQ em `localhost:5672`
- Kafka em `localhost:9092`
- LocalStack SQS em `http://localhost:4566` (credenciais `test/test`)

---

## Infraestrutura local (`docker-compose.yml`)

```bash
docker compose up -d
```

Sobe os serviços:

| Serviço | Porta | Descrição |
|---|---|---|
| MongoDB 7 | 27017 | Armazenamento dos workflows |
| RabbitMQ 3 | 5672 / 15672 | Broker + Management UI |
| Kafka (Confluent 7.6) | 9092 | Broker |
| Zookeeper | 2181 | Dependência do Kafka |
| LocalStack 3 | 4566 | Emulador AWS SQS |

### Inicialização do MongoDB

O diretório `mongodb-workflows/` é montado em `/docker-entrypoint-initdb.d` do container MongoDB. Na primeira inicialização (volume vazio), o script `init-mongo.js` é executado automaticamente e:

1. Cria o database `generic-orchestrator`
2. Cria a collection `workflows`
3. Cria índice único em `flowId`

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

| Método | Endpoint | Descrição |
|---|---|---|
| POST | `/api/auth/login` | Gera token JWT |
| GET | `/api/flows` | Lista fluxos ativos |
| POST | `/api/flows` | Cadastra fluxo (corpo: YAML) |
| GET | `/api/flows/{flowId}` | Busca fluxo ativo |
| PUT | `/api/flows/{flowId}` | Atualiza fluxo |
| DELETE | `/api/flows/{flowId}` | Desativa fluxo |
| POST | `/api/orchestrate/{version}/{flowId}` | Executa fluxo com payload JSON (`version` no path — match em `id` + `versao` no Mongo) |
| GET | `/actuator/health` | Health check |

---

## Formato do Workflow (YAML)

```yaml
fluxo:
  id: "meu-fluxo"           # Obrigatório, único no MongoDB
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
      tipo: HTTP             # HTTP | QUEUE | DATABASE
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

### Integração DATABASE (MongoDB)

```yaml
- id: "salvar-registro"
  ordem: 2
  tipo: DATABASE
  continuarEmErro: false
  database:
    operacao: INSERT         # INSERT | FIND_ONE | FIND_MANY | UPDATE | DELETE
    colecao: "minha-colecao"
    documentoTemplate: |
      {"campo":"{{contrato.campo}}","ts":"{{now()}}"}
    filtroTemplate: |        # Para FIND, UPDATE, DELETE
      {"_id":"{{contrato.id}}"}
    mapeamentoResposta:
      campoOrigem: "_id"
      campoDestino: "registroId"
```

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

## MongoDB

- **Database:** `generic-orchestrator`
- **Collection:** `workflows`
- **Índice:** composto único em `id` + `versao` (criado pelo `init-mongo.js`)

A collection é gerenciada via `FlowDefinitionRepository` (Spring Data MongoDB). Os documentos armazenam a definição completa do fluxo, incluindo contrato e integrações. O índice composto permite múltiplas versões de um mesmo `id` coexistirem; o segmento `version` na URL `/api/orchestrate/{version}/{flowId}` direciona qual delas será executada.
