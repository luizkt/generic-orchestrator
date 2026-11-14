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
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Pool de threads físicas usado pela v2 ({@code orchestrator.platform-pool.*}). */
@Data
@ConfigurationProperties(prefix = "orchestrator.platform-pool")
public class OrchestratorThreadPoolProperties {

    /** Threads mantidas no pool. */
    private int coreSize = 200;

    /** Máximo de threads. Com fila limitada, só passa de core se a fila encher. */
    private int maxSize = 200;

    /** Capacidade da fila limitada. Cheia e com o pool no máximo, vale CallerRunsPolicy. */
    private int queueCapacity = 200;

    /** Keep-alive (s) das threads acima do core. */
    private long keepAliveSeconds = 60;

    /** Prefixo do nome das threads. */
    private String threadNamePrefix = "orch-v2-";
}
