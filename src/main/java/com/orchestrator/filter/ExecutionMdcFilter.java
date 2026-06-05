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

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Captura ou gera um {@code executionId} por requisição e o injeta no MDC do SLF4J.
 *
 * <p>Se o header {@value #HEADER_EXECUTION_ID} estiver presente na requisição, ele é reutilizado;
 * caso contrário, um UUID é gerado. O valor é devolvido na resposta no mesmo header para
 * rastreabilidade end-to-end. O MDC é limpo ao final da requisição (bloco {@code finally}).
 */
@Component
public class ExecutionMdcFilter extends OncePerRequestFilter {

    public static final String MDC_EXECUTION_ID = "executionId";
    public static final String HEADER_EXECUTION_ID = "X-Execution-Id";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String executionId = request.getHeader(HEADER_EXECUTION_ID);
        if (executionId == null || executionId.isBlank()) {
            executionId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_EXECUTION_ID, executionId);
        response.setHeader(HEADER_EXECUTION_ID, executionId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
