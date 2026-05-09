package com.orchestrator.config.properties;

import lombok.Data;

@Data
public class KafkaIntegrationProperties {

    private String id;
    private String bootstrapServers = "localhost:9092";
    private Producer producer = new Producer();

    @Data
    public static class Producer {
        private String keySerializer = "org.apache.kafka.common.serialization.StringSerializer";
        private String valueSerializer = "org.apache.kafka.common.serialization.StringSerializer";
    }
}
