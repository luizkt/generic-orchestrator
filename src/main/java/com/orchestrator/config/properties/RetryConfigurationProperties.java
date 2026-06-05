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

import java.util.List;
import lombok.Data;

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

    /**
     * Timeout por tentativa individual, em ms. Usado quando a integração não define timeout
     * próprio.
     */
    private long timeout = 30000;
}
