package com.orchestrator.it;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7"));
    static final RabbitMQContainer RABBIT = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3-management"));
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));
    static final LocalStackContainer LOCALSTACK = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3"))
            .withServices(LocalStackContainer.Service.SQS);

    static {
        MONGO.start();
        RABBIT.start();
        KAFKA.start();
        LOCALSTACK.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry r) {
        r.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
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
    }
}
