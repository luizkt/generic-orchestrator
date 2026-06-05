/*
 * MIT License
 *
 * Copyright (c) 2026 Luiz
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.orchestrator.config;

import com.orchestrator.config.properties.OrchIntegrationsProperties;
import com.orchestrator.config.properties.RabbitMqIntegrationProperties;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
