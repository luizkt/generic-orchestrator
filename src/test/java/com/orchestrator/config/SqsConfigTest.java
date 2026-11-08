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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.sqs.SqsClient;

class SqsConfigTest {

    private SqsConfig config(String endpoint, String access, String secret) {
        SqsConfig c = new SqsConfig();
        ReflectionTestUtils.setField(c, "region", "sa-east-1");
        ReflectionTestUtils.setField(c, "endpoint", endpoint);
        ReflectionTestUtils.setField(c, "accessKey", access);
        ReflectionTestUtils.setField(c, "secretKey", secret);
        return c;
    }

    @Test
    @DisplayName("Com endpoint e credenciais estáticas, cria cliente na região configurada")
    void comEndpointECredenciais() {
        try (SqsClient client = config("http://localhost:4566", "ak", "sk").sqsClient()) {
            assertThat(client.serviceClientConfiguration().region().id()).isEqualTo("sa-east-1");
            assertThat(client.serviceClientConfiguration().endpointOverride())
                    .hasValueSatisfying(
                            u -> assertThat(u.toString()).isEqualTo("http://localhost:4566"));
        }
    }

    @Test
    @DisplayName("Sem credenciais nem endpoint, usa a cadeia padrão e o endpoint da AWS")
    void semCredenciaisNemEndpoint() {
        try (SqsClient client = config("", "", "").sqsClient()) {
            assertThat(client.serviceClientConfiguration().endpointOverride()).isEmpty();
        }
    }
}
