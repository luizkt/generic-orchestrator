package com.orchestrator.config.properties;

import lombok.Data;

import java.util.List;

@Data
public class RetryConfigurationProperties {

    /** Número total de tentativas (incluindo a primeira chamada). */
    private int maximumAttempts = 3;

    /** Tempo de espera inicial antes da primeira tentativa de retry, em ms. */
    private long delay = 100;

    /** Multiplicador para o backoff exponencial entre tentativas. */
    private double backoffMultiplier = 2.0;

    /** Tempo máximo de espera entre tentativas, em ms. */
    private long maximumDelay = 10000;

    /** Códigos HTTP que devem disparar um retry. */
    private List<Integer> retryableHttpCondition = List.of(500, 429, 408);

    /** Timeout por tentativa individual, em ms. Usado quando a integração não define timeout próprio. */
    private long timeout = 30000;
}
