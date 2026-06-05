package com.orchestrator.config;

import com.orchestrator.config.properties.CircuitBreakerConfigurationProperties;
import com.orchestrator.config.properties.OrchIntegrationsProperties;
import com.orchestrator.config.properties.RetryConfigurationProperties;
import com.orchestrator.exception.RetriableHttpException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;

@Configuration
@RequiredArgsConstructor
public class HttpResilienceConfig {

    private final OrchIntegrationsProperties properties;

    @Bean
    public RetryRegistry httpRetryRegistry() {
        RetryConfigurationProperties rp = properties.getRetryConfiguration();

        RetryConfig config = RetryConfig.custom()
                .maxAttempts(rp.getMaximumAttempts())
                .intervalFunction(attempt -> {
                    long waitMs = (long) (rp.getDelay() * Math.pow(rp.getBackoffMultiplier(), attempt - 1));
                    return Math.min(waitMs, rp.getMaximumDelay());
                })
                .retryOnException(e -> {
                    if (e instanceof CallNotPermittedException) return false;
                    if (e instanceof RetriableHttpException) return true;
                    // Não retenta erros HTTP não-retryáveis (4xx sem estar na lista)
                    if (e instanceof WebClientResponseException) return false;
                    // Retenta erros de rede/timeout (não são WebClientResponseException)
                    return true;
                })
                .build();

        return RetryRegistry.of(config);
    }

    @Bean
    public CircuitBreakerRegistry httpCircuitBreakerRegistry() {
        CircuitBreakerConfigurationProperties cb = properties.getCircuitBreakerConfiguration();

        SlidingWindowType windowType = "TIME".equalsIgnoreCase(cb.getSlidingWindowSizeType())
                ? SlidingWindowType.TIME_BASED
                : SlidingWindowType.COUNT_BASED;

        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(windowType)
                .slidingWindowSize(cb.getSlidingWindowSize())
                .minimumNumberOfCalls(cb.getMinimumNumberCalls())
                .failureRateThreshold(cb.getFailureRateThreshold())
                .waitDurationInOpenState(Duration.ofSeconds(cb.getWaitDurationOpenState()))
                .permittedNumberOfCallsInHalfOpenState(cb.getPermittedCallsOpenState())
                .automaticTransitionFromOpenToHalfOpenEnabled(cb.isAutomaticTransitionHalfOpenEnabled())
                .slowCallDurationThreshold(Duration.ofMillis(cb.getSlowCallDurationThreshold()))
                .slowCallRateThreshold(100)
                .build();

        return CircuitBreakerRegistry.of(config);
    }
}
