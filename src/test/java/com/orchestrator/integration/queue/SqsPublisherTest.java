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
package com.orchestrator.integration.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.orchestrator.domain.model.QueueIntegrationConfig;
import com.orchestrator.exception.IntegrationExecutionException;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

class SqsPublisherTest {

    private final SqsClient client = mock(SqsClient.class);
    private final SqsPublisher publisher = new SqsPublisher(client);

    @Test
    @DisplayName("Envia body para a queueUrl e retorna o messageId")
    void enviaERetornaMessageId() {
        when(client.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(SendMessageResponse.builder().messageId("m-1").build());
        QueueIntegrationConfig cfg = new QueueIntegrationConfig();
        cfg.setQueueUrl("http://sqs/q");

        Map<String, Object> out = publisher.publish(cfg, "corpo");

        ArgumentCaptor<SendMessageRequest> cap = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(client).sendMessage(cap.capture());
        assertThat(cap.getValue().queueUrl()).isEqualTo("http://sqs/q");
        assertThat(cap.getValue().messageBody()).isEqualTo("corpo");
        assertThat(out)
                .containsEntry("provider", "SQS")
                .containsEntry("queueUrl", "http://sqs/q")
                .containsEntry("messageId", "m-1")
                .containsEntry("published", true);
    }

    @Test
    @DisplayName("Falha quando queueUrl é nula ou em branco")
    void falhaSemQueueUrl() {
        QueueIntegrationConfig cfg = new QueueIntegrationConfig();
        assertThatThrownBy(() -> publisher.publish(cfg, "m"))
                .isInstanceOf(IntegrationExecutionException.class)
                .hasMessageContaining("queueUrl");
        cfg.setQueueUrl(" ");
        assertThatThrownBy(() -> publisher.publish(cfg, "m"))
                .isInstanceOf(IntegrationExecutionException.class);
        verifyNoInteractions(client);
    }
}
