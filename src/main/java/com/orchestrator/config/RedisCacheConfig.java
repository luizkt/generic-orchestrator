package com.orchestrator.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * Cache Redis dos workflows ativos. Chave: `workflows::{flowId}_{versao}`.
 *
 * TTL padrão de 1 hora (configurável via `orchestrator.cache.workflows.ttl-seconds`).
 *
 * Por que parsed FlowDefinition em vez do YAML cru:
 *   - Economia de CPU em execuções concorrentes (parse YAML acontece 1x por entrada).
 *   - Spring Cache + Jackson serializa/deserializa transparentemente para o Redis.
 *
 * Invalidação: somente por TTL. Workflows atualizados via Manager ficarão
 * "stale" no cache do orquestrador por até 1 hora — aceitável dado o uso
 * (workflows mudam raramente vs. execuções, que são muito frequentes).
 */
@Configuration
@EnableCaching
public class RedisCacheConfig {

    public static final String WORKFLOWS_CACHE = "workflows";

    @Value("${orchestrator.cache.workflows.ttl-seconds:3600}")
    private long ttlSeconds;

    @Bean
    public ObjectMapper cacheObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        // activateDefaultTyping para preservar tipos polimórficos (enums, sub-objetos)
        mapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("com.orchestrator.")
                        .allowIfSubType("java.util.")
                        .allowIfSubType("java.time.")
                        .allowIfSubType("java.lang.")
                        .build(),
                ObjectMapper.DefaultTyping.NON_FINAL);
        return mapper;
    }

    @Bean
    public RedisCacheManager workflowsCacheManager(
            RedisConnectionFactory connectionFactory, ObjectMapper cacheObjectMapper) {
        GenericJackson2JsonRedisSerializer serializer =
                new GenericJackson2JsonRedisSerializer(cacheObjectMapper);

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
