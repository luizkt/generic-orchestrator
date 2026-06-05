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
package com.orchestrator.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class ExecutionMdcFilterTest {

    private final ExecutionMdcFilter filter = new ExecutionMdcFilter();

    @AfterEach
    void cleanMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("Gera executionId quando header ausente e ecoa no response")
    void geraExecutionIdQuandoHeaderAusente() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, res, chain);

        verify(chain).doFilter(req, res);
        assertThat(res.getHeader("X-Execution-Id")).isNotBlank();
    }

    @Test
    @DisplayName("Reutiliza executionId presente no header da requisição")
    void reutilizaExecutionIdDoHeader() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Execution-Id", "eid-from-caller");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, res, chain);

        assertThat(res.getHeader("X-Execution-Id")).isEqualTo("eid-from-caller");
    }

    @Test
    @DisplayName("MDC contém executionId durante a execução da cadeia")
    void mdcContemExecutionIdDuranteChain() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Execution-Id", "eid-123");
        MockHttpServletResponse res = new MockHttpServletResponse();
        AtomicReference<String> mdcValue = new AtomicReference<>();
        FilterChain chain = (r, s) -> mdcValue.set(MDC.get(ExecutionMdcFilter.MDC_EXECUTION_ID));

        filter.doFilterInternal(req, res, chain);

        assertThat(mdcValue.get()).isEqualTo("eid-123");
    }

    @Test
    @DisplayName("MDC é limpo após a chain completar normalmente")
    void limpaMdcAposChainNormal() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, res, chain);

        assertThat(MDC.get(ExecutionMdcFilter.MDC_EXECUTION_ID)).isNull();
    }

    @Test
    @DisplayName("MDC é limpo mesmo quando a chain lança exceção")
    void limpaMdcAposExcecaoNaChain() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        doThrow(new RuntimeException("boom")).when(chain).doFilter(req, res);

        try {
            filter.doFilterInternal(req, res, chain);
        } catch (RuntimeException ignored) {
        }

        assertThat(MDC.get(ExecutionMdcFilter.MDC_EXECUTION_ID)).isNull();
    }

    @Test
    @DisplayName("Header em branco é tratado como ausente — gera novo executionId")
    void headerEmBrancoGeraNovoExecutionId() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Execution-Id", "   ");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, res, chain);

        assertThat(res.getHeader("X-Execution-Id")).isNotBlank();
        assertThat(res.getHeader("X-Execution-Id")).doesNotContain("   ");
    }
}
