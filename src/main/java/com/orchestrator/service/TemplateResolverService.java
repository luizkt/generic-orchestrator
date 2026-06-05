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
package com.orchestrator.service;

import com.orchestrator.domain.execution.FlowExecutionContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class TemplateResolverService {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([^}]+)\\s*}}");

    public String resolve(String template, FlowExecutionContext ctx) {
        if (template == null) return null;
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String expr = m.group(1).trim();
            String value = String.valueOf(resolveExpression(expr, ctx));
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private Object resolveExpression(String expr, FlowExecutionContext ctx) {
        if ("now()".equals(expr)) return LocalDateTime.now().toString();
        String[] parts = expr.split("\\.");
        Object current =
                switch (parts[0]) {
                    case "contract" -> ctx.getContract();
                    case "integrations" -> ctx.getIntegrations();
                    default -> null;
                };
        if (current == null) return "";
        for (int i = 1; i < parts.length && current != null; i++)
            current = navigate(current, parts[i]);
        return current != null ? current : "";
    }

    private Object navigate(Object current, String key) {
        if (current instanceof Map<?, ?> m) return m.get(key);
        if (current instanceof List<?> l) {
            try {
                return l.get(Integer.parseInt(key));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
