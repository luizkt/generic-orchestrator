package com.orchestrator.config;

import com.orchestrator.config.properties.OrchIntegrationsProperties;
import com.orchestrator.config.properties.RabbitMqIntegrationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class RabbitMqMultiInstanceConfig {

    private final OrchIntegrationsProperties properties;

    @Bean
    public Map<String, RabbitTemplate> rabbitTemplates() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        Map<String, RabbitTemplate> templates = new HashMap<>();
        for (RabbitMqIntegrationProperties cfg : properties.getRabbitmqs()) {
            CachingConnectionFactory cf = new CachingConnectionFactory();
            cf.setHost(cfg.getHost());
            cf.setPort(cfg.getPort());
            cf.setUsername(cfg.getUsername());
            cf.setPassword(cfg.getPassword());
            RabbitTemplate template = new RabbitTemplate(cf);
            template.setMessageConverter(converter);
            templates.put(cfg.getId(), template);
        }
        return templates;
    }
}
