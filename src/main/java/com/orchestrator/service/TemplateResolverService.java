package com.orchestrator.service;

import com.orchestrator.domain.execution.FlowExecutionContext;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        Object current = switch (parts[0]) {
            case "contract" -> ctx.getContract();
            case "integrations" -> ctx.getIntegrations();
            default -> null;
        };
        if (current == null) return "";
        for (int i = 1; i < parts.length && current != null; i++) current = navigate(current, parts[i]);
        return current != null ? current : "";
    }

    private Object navigate(Object current, String key) {
        if (current instanceof Map<?, ?> m) return m.get(key);
        if (current instanceof List<?> l) {
            try { return l.get(Integer.parseInt(key)); }
            catch (NumberFormatException e) { return null; }
        }
        return null;
    }
}
