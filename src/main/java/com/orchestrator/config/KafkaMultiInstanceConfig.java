package com.orchestrator.config;

import com.orchestrator.config.properties.KafkaIntegrationProperties;
import com.orchestrator.config.properties.OrchIntegrationsProperties;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.HashMap;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class KafkaMultiInstanceConfig {

    private final OrchIntegrationsProperties properties;

    @Bean
    public Map<String, KafkaTemplate<String, String>> kafkaTemplates() {
        Map<String, KafkaTemplate<String, String>> templates = new HashMap<>();
        for (KafkaIntegrationProperties cfg : properties.getKafkas()) {
            Map<String, Object> props = new HashMap<>();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, cfg.getBootstrapServers());
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, cfg.getProducer().getKeySerializer());
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, cfg.getProducer().getValueSerializer());
            templates.put(cfg.getId(), new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props)));
        }
        return templates;
    }
}
