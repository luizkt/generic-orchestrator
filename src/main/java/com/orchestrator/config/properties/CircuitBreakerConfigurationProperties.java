package com.orchestrator.config.properties;

import lombok.Data;

@Data
public class CircuitBreakerConfigurationProperties {

    /** Tamanho da janela deslizante (quantidade de chamadas ou segundos, conforme slidingWindowSizeType). */
    private int slidingWindowSize = 10;

    /** Tipo da janela: COUNT (quantidade de chamadas) ou TIME (janela de tempo em segundos). */
    private String slidingWindowSizeType = "COUNT";

    /** Número mínimo de chamadas antes de o circuit breaker começar a calcular a taxa de falha. */
    private int minimumNumberCalls = 5;

    /** Percentual de falhas que fecha o circuito (0-100). */
    private float failureRateThreshold = 50;

    /** Tempo em que o circuito permanece aberto antes de transitar para half-open, em segundos. */
    private long waitDurationOpenState = 30;

    /** Número de chamadas de teste permitidas no estado half-open. */
    private int permittedCallsOpenState = 3;

    /** Transita automaticamente para half-open após waitDurationOpenState expirar. */
    private boolean automaticTransitionHalfOpenEnabled = true;

    /** Duração a partir da qual uma chamada é considerada lenta e conta para a taxa de falha, em ms. */
    private long slowCallDurationThreshold = 800;
}
