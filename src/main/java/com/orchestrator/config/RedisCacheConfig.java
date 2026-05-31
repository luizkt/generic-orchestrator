package com.orchestrator.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orchestrator.domain.model.FlowDefinition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * Cache Redis dos workflows ativos. Chave: `workflows::{flowId}_{versao}`.
 *
 * TTL padrão de 1 hora (configurável via `orchestrator.cache.workflows.ttl-seconds`).
 *
 * Usa Jackson2JsonRedisSerializer<FlowDefinition> com tipo concreto fixo —
 * sem necessidade de @class no JSON. GenericJackson2JsonRedisSerializer com
 * activateDefaultTyping(NON_FINAL) + BasicPolymorphicTypeValidator não embute
 * @class no JSON em Jackson 2.12+ quando o tipo raiz é conhecido, causando
 * ClassCastException ao desserializar (LinkedHashMap → FlowDefinition).
 */
@Configuration
@EnableCaching
public class RedisCacheConfig {

    public static final String WORKFLOWS_CACHE = "workflows";

    @Value("${orchestrator.cache.workflows.ttl-seconds:3600}")
    private long ttlSeconds;

    @Bean
    public RedisCacheManager workflowsCacheManager(RedisConnectionFactory connectionFactory) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        Jackson2JsonRedisSerializer<FlowDefinition> serializer =
                new Jackson2JsonRedisSerializer<>(mapper, FlowDefinition.class);

        RedisCacheConfiguration cfg = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(ttlSeconds))
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(cfg)
                .withCacheConfiguration(WORKFLOWS_CACHE, cfg)
                .build();
    }
}
