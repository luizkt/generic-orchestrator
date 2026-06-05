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
package com.orchestrator.config.properties;

import lombok.Data;

@Data
public class CircuitBreakerConfigurationProperties {

    /**
     * Tamanho da janela deslizante (quantidade de chamadas ou segundos, conforme
     * slidingWindowSizeType).
     */
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

    /**
     * Duração a partir da qual uma chamada é considerada lenta e conta para a taxa de falha, em ms.
     */
    private long slowCallDurationThreshold = 800;
}
