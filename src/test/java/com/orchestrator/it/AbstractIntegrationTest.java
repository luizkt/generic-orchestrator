package com.orchestrator.it;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    static final RabbitMQContainer RABBIT = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3-management"));
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));
    static final LocalStackContainer LOCALSTACK = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3"))
            .withServices(LocalStackContainer.Service.SQS);
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static {
        RABBIT.start();
        KAFKA.start();
        LOCALSTACK.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry r) {
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        r.add("spring.rabbitmq.host", RABBIT::getHost);
        r.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        r.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        r.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
        r.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        r.add("orchestrator.aws.region", () -> LOCALSTACK.getRegion());
        r.add("orchestrator.aws.sqs.endpoint", () ->
                LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.SQS).toString());
        r.add("orchestrator.aws.sqs.access-key", LOCALSTACK::getAccessKey);
        r.add("orchestrator.aws.sqs.secret-key", LOCALSTACK::getSecretKey);

        r.add("orch-integrations.kafkas[0].id", () -> "kafka-user-tracking");
        r.add("orch-integrations.kafkas[0].bootstrap-servers", KAFKA::getBootstrapServers);
        r.add("orch-integrations.rabbitmqs[0].id", () -> "rabbitmq-notifier");
        r.add("orch-integrations.rabbitmqs[0].host", RABBIT::getHost);
        r.add("orch-integrations.rabbitmqs[0].port", () -> String.valueOf(RABBIT.getAmqpPort()));
        r.add("orch-integrations.rabbitmqs[0].username", RABBIT::getAdminUsername);
        r.add("orch-integrations.rabbitmqs[0].password", RABBIT::getAdminPassword);

        // Manager: URL placeholder — testes não chamam o Manager. Warm-up
        // desabilitado e WorkflowCacheService é mockado quando preciso (vide
        // OrchestrationServiceTest). SecurityIT não toca em fluxos.
        r.add("orchestrator.manager.base-url", () -> "http://localhost:1");
        r.add("orchestrator.manager.username", () -> "admin");
        r.add("orchestrator.manager.password", () -> "admin");
        r.add("orchestrator.cache.workflows.warm-up-enabled", () -> "false");
    }
}
