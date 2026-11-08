# AGENTS.md — generic-orchestrator

Índice para agentes de IA. Leia antes de abrir código. Método: `../docs/plans/PLAN-repo-indexing.md` (monorepo).

<!-- index:meta
repo: generic-orchestrator
tipo: spring-boot-api (executor de fluxos YAML)
fonte-de-verdade: build.gradle.kts, src/main/resources/application.yml, src/main/java/com/orchestrator
atualizado-em: 2026-11-07
-->

## Resumo

Executa fluxos YAML: valida o contrato do payload, roda integrações (HTTP e filas), roda validações, devolve resultado consolidado. Não persiste nada e não faz CRUD de workflows (isso é do `service-portal-manager`): busca o YAML no Manager e cacheia no Redis (TTL 1h).

<!-- gen:size -->Tamanho: 67 arquivos .java em `src/main`, 22 em `src/test`.<!-- /gen:size -->

Stack: Java 21, Spring Boot 3.4.5 (não atualizar), Gradle Kotlin DSL, WebClient (nunca RestTemplate), Redis, RabbitMQ/Kafka/SQS, Resilience4j `Retry(CircuitBreaker(call))`, JWT HS512 stateless. Porta `SERVER_PORT` (8080).

## Endpoints

| Rota | Auth | Onde |
|---|---|---|
| `POST /api/auth/tokens` | público | `security/AuthController` |
| `POST /api/v1/flows/{flowId}/versions/{version}/executions` | JWT | `controller/OrchestrationController` → `service/OrchestrationService` (sequencial) |
| `POST /api/v2/flows/{flowId}/versions/{version}/executions` | JWT | mesmo controller → `service/OrchestrationV2Service` (paralelo por `order`, virtual threads) |
| `DELETE /api/admin/cache/workflows[/{flowId}/versions/{version}]` | JWT | `controller/CacheAdminController` |
| `/actuator/**`, `/swagger-ui.html`, `/v3/api-docs/**` | público | `security/SecurityConfig` |

O controller sempre responde 200 com `OrchestrationResponse`. O resultado vai em `status`: `SUCCESS`, `PARTIAL_SUCCESS` (algum `continueOnError` falhou) ou `FAILED`. Falhas viram `FAILED` dentro do serviço; o `GlobalExceptionHandler` não é alcançado nesse caminho.

## Fluxo de execução

1. `manager/WorkflowCacheService.load` lê o Redis; em miss, `ManagerWorkflowClient.fetchYaml` e `service/YamlParserService`.
2. `service/ContractValidationService.validate` (tipo `FieldType` e regra `ValidationType`).
3. Integrações em ordem de `order`: `integration/IntegrationExecutorFactory.get(type).execute`.
4. Validações do fluxo, mesmo mecanismo.
5. Passo com `continueOnError` grava `{error: msg}` e segue; sem a flag lança `IntegrationExecutionException` e o fluxo vira `FAILED`.

v2: passos com o mesmo `order` rodam em paralelo; os irmãos terminam antes de a falha ser relançada; grupos e validações seguintes não rodam. Templates `{{contract.x}}`, `{{integrations.id.campo}}`, `{{now()}}` ficam em `service/TemplateResolverService`; valor ausente vira `""` e nunca lança.

## Mapa

Base: `src/main/java/com/orchestrator/`.

| Caminho | Papel |
|---|---|
| `GenericOrchestratorApplication` | Entry point |
| `service/*` | Execução v1 e v2, contrato, template, parse YAML. `service/ResultMapper` (`applyMapping`, `build`) é compartilhado por v1 e v2 |
| `integration/IntegrationExecutor` e `integration/IntegrationExecutorFactory` | Interface e seleção por `IntegrationType` (`HTTP`, `QUEUE`) |
| `integration/http/HttpIntegrationExecutor` | HTTP com retry e circuit breaker; envia `X-Execution-Id` |
| `integration/queue/*` | `QueueIntegrationExecutor` escolhe publisher por `QueueProvider` (`RABBITMQ`, `KAFKA`, `SQS`) |
| `manager/*` | Cliente do Manager, login server-to-server (`ManagerAuthService`), cache e warm-up |
| `domain/model/*`, `domain/execution/*` | Modelo do fluxo YAML, contexto e resultado da execução |
| `security/*` | JWT stateless e rotas públicas |
| `config/*` | Redis, resiliência, WebClient, instâncias Kafka/Rabbit/SQS e `config/properties/*` |
| `exception/*`, `dto/*`, `filter/ExecutionMdcFilter` | Exceções e mapeamento HTTP, DTOs, `executionId` no MDC e no header |

Recursos: `src/main/resources/application.yml`, `application-docker.yml`, `docs/example-flow.yml` (5 tipos de integração), `docs/features/feature_{1,2,3}.md`.

## Quero mudar... onde

| Mudança | Onde |
|---|---|
| Regra de validação de campo | `domain/model/ValidationType` e `switch` em `service/ContractValidationService` (sem `default`: não compila sem o `case`) |
| Tipo de campo | `domain/model/FieldType` e `switch` de tipos no mesmo serviço |
| Provider de fila | `QueueProvider`, novo `XxxPublisher`, `case` em `QueueIntegrationExecutor`, config em `config/`, `application.yml`, `build.gradle.kts` |
| Tipo de integração | `IntegrationType`, config em `domain/model`, `IntegrationExecutor` com `@Component` |
| Retry e circuit breaker | `orch-integrations.retry-configuration` e `circuit-breaker-configuration` no `application.yml`; default em `config/properties/RetryConfigurationProperties` (500, 429, 408) |
| `responseMapping` do HTTP e montagem do `FlowExecutionResult` | `service/ResultMapper` (usado por v1 e v2) |
| Semântica de execução | `OrchestrationService` (v1) e `OrchestrationV2Service` (v2). Mude os dois se a regra for comum |
| Cache | TTL e warm-up em `orchestrator.cache.workflows.*`; invalidar por `CacheAdminController` ou `WorkflowCacheService.evict`/`evictAll` |
| Auth com o Manager | `manager/ManagerAuthService`, `orchestrator.manager.*` |
| Rota pública da API | `security/SecurityConfig` (chain JWT, `@Order(2)`) |
| Segurança do actuator | `security/ActuatorSecurityConfig` (chain Basic, `@Order(1)`, só `/actuator/**`) |
| Defaults inseguros | `security/SecurityDefaultsGuard` (WARN; falha em `prod`/`strict`) |
| Instância Kafka ou Rabbit | `orch-integrations.kafkas[]` ou `rabbitmqs[]` (o `id` casa com o passo no YAML) |
| Campo novo no YAML do fluxo | classe em `domain/model` e `docs/example-flow.yml` |

## Integrações e config

- Manager: `MANAGER_URL`, `MANAGER_USERNAME`, `MANAGER_PASSWORD` (`localhost:8082`, `admin`, `admin`). O Manager também chama de volta `DELETE /api/admin/cache/workflows/{flowId}/versions/{version}` (`OrchestratorCacheClient` do Manager) para invalidar o cache. Rotas usadas: `/api/auth/tokens`, `/manager/flows?status=active`, `/manager/flows/{id}/versions/{v}/yaml`.
- Redis: `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`. Cache: `WORKFLOWS_CACHE_TTL_SECONDS` (3600), `WORKFLOWS_WARM_UP_ENABLED` (true).
- JWT: `JWT_SECRET` (sem valor na imagem; o default de dev do `application.yml` é sinalizado pelo guard), `JWT_EXPIRATION`. Login da API (`/api/auth/tokens`): `ADMIN_USERNAME`/`ADMIN_PASSWORD` (default `admin`/`admin`, só dev; comparação em tempo constante).
- Actuator: públicos `/actuator/health/**`, `/actuator/info`, `/actuator/prometheus`. O resto (`env`, `loggers`, `threaddump`, `metrics`, `caches`, `beans`) exige HTTP Basic do usuário com role `ACTUATOR`: `ACTUATOR_USERNAME` (default `actuator`) e `ACTUATOR_PASSWORD` (SEM default: vazio = usuário inexistente = 401 sempre). `heapdump` não é exposto. `health` mostra detalhes só `when-authorized`. O SBA lê esses endpoints pelas metadata `spring.boot.admin.client.instance.metadata.user.name` e `.password`, preenchidas com as mesmas variáveis.
- Guard de segredos: `SECURITY_STRICT=true` ou profile `prod` faz o startup falhar se `JWT_SECRET` (`CHANGE_ME...`), `ADMIN_*` (`admin`/`admin`) ou `MANAGER_PASSWORD=admin` estiverem nos defaults; nos demais perfis só loga WARN com os nomes (nunca os valores). SBA: `SPRING_BOOT_ADMIN_URL`. Tracing: `OTEL_EXPORTER_OTLP_ENDPOINT`.
- Demais variáveis (Rabbit, Kafka, SQS, timeouts HTTP): `src/main/resources/application.yml`. Infra local: `docker-compose.yml`; stack completa: `../docker-compose-service-portal.yml`.

## Comandos

```bash
docker compose up -d && ./gradlew bootRun   # infra local e app
./gradlew test                              # só unitários (exclui o pacote it)
./gradlew integrationTest                   # *IT com Testcontainers (precisa de Docker)
./gradlew jacocoTestCoverageVerification    # roda os dois e verifica >= 95% sobre o merge dos .exec
./gradlew spotlessApply                     # obrigatório antes de commit
```

## Armadilhas

- Segredos nunca em `ENV` do Dockerfile: injete em runtime (compose/secret). Rota nova de actuator fora de health, info e prometheus fica protegida por padrão; não volte a liberar `/actuator/**` nem exponha `heapdump` (contém o `JWT_SECRET`).
- O `JwtAuthenticationFilter` fica só na chain da API (registro de servlet desligado em `SecurityConfig`); um Bearer não abre o actuator.
- Testes que leem `/actuator/prometheus` com MockMvc precisam de `@AutoConfigureObservability` (o `@SpringBootTest` desliga o export de métricas).
- Sem RestTemplate, sem banco, sem atualizar Java 21 ou Spring Boot 3.4.5. Retry e circuit breaker ficam externalizados.
- Virtual threads não herdam MDC nem contexto OTel: código paralelo novo precisa copiar os dois (`OrchestrationV2Service.executeGroupParallel`).
- Circuito aberto (`CallNotPermittedException`) não é retentado.
- Os registries de Retry e CircuitBreaker devolvem instâncias singleton por integração: listeners de eventos entram uma vez, em `config/HttpResilienceConfig` (`onEntryAdded`), nunca a cada execução em `HttpIntegrationExecutor` (acumulava consumers e duplicava logs).
- Testcontainers 1.20.x com Docker Engine 29 ou mais novo falha ao iniciar o cliente Docker. O `build.gradle.kts` fixa `api.version=1.44` nos testes; ao subir a versão do Testcontainers, reavalie.
- Todo `.java` precisa do header de licença, senão o Spotless falha.
- Só unitários dão 0,91 de cobertura: o gate precisa do `integrationTest` também. No CI são jobs paralelos (`.github/workflows/ci.yml`) e o job `coverage` junta os `.exec` por artifact, reusando as classes do job `build` (`-x compileJava ...`).
- O gate JaCoCo cobre `HttpIntegrationExecutor`, `manager/**`, config de Redis, resiliência e Manager, e `CacheAdminController`: mude essas classes com teste.
