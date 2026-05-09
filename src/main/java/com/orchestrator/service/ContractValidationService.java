package com.orchestrator.service;

import com.orchestrator.domain.model.*;
import com.orchestrator.exception.ContractValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ContractValidationService {

    public void validate(FlowContract contract, Map<String, Object> payload) {
        List<String> errors = new ArrayList<>();
        if (contract != null && contract.getCampos() != null)
            validateFields(contract.getCampos(), payload, errors, "");
        if (!errors.isEmpty())
            throw new ContractValidationException("Erros de validação do contrato", errors);
    }

    @SuppressWarnings("unchecked")
    private void validateFields(List<FieldDefinition> fields, Map<String, Object> payload,
                                List<String> errors, String parentPath) {
        for (FieldDefinition f : fields) {
            String path = parentPath.isEmpty() ? f.getNome() : parentPath + "." + f.getNome();
            Object value = payload != null ? payload.get(f.getNome()) : null;

            if (f.isObrigatorio() && (value == null || (value instanceof String s && s.isBlank()))) {
                errors.add(String.format("Campo '%s' é obrigatório", path));
                continue;
            }
            if (value == null) continue;

            if (!matchesType(value, f.getTipo())) {
                errors.add(String.format("Campo '%s' deveria ser do tipo %s", path, f.getTipo()));
                continue;
            }

            if (f.getValidacoes() != null) {
                for (ValidationRule r : f.getValidacoes()) {
                    String err = applyRule(r, value, path);
                    if (err != null) errors.add(err);
                }
            }

            if (f.getTipo() == FieldType.OBJECT && f.getObjeto() != null && value instanceof Map<?, ?> m) {
                validateFields(f.getObjeto().getCampos(), (Map<String, Object>) m, errors, path);
            } else if (f.getTipo() == FieldType.ARRAY && f.getObjeto() != null && value instanceof List<?> l) {
                for (int i = 0; i < l.size(); i++) {
                    if (l.get(i) instanceof Map<?, ?> m) {
                        validateFields(f.getObjeto().getCampos(), (Map<String, Object>) m,
                                errors, path + "[" + i + "]");
                    }
                }
            }
        }
    }

    private boolean matchesType(Object v, FieldType t) {
        return switch (t) {
            case STRING -> v instanceof String;
            case INTEGER -> v instanceof Integer || v instanceof Long;
            case DECIMAL -> v instanceof Number;
            case BOOLEAN -> v instanceof Boolean;
            case OBJECT -> v instanceof Map;
            case ARRAY -> v instanceof List;
        };
    }

    private String applyRule(ValidationRule r, Object v, String path) {
        String defaultMsg = String.format("Validação '%s' falhou para o campo '%s'", r.getTipo(), path);
        String msg = r.getMensagem() != null ? r.getMensagem() : defaultMsg;
        return switch (r.getTipo()) {
            case NOT_BLANK -> (v instanceof String s && s.isBlank()) ? msg : null;
            case NOT_EMPTY -> (v instanceof Collection<?> c && c.isEmpty()) ? msg : null;
            case NOT_NULL -> v == null ? msg : null;
            case PATTERN -> (v instanceof String s && r.getValor() != null
                    && !Pattern.matches(r.getValor(), s)) ? msg : null;
            case SIZE -> validateSize(v, r, msg);
            case MIN -> (v instanceof Number n && r.getValor() != null
                    && n.doubleValue() < Double.parseDouble(r.getValor())) ? msg : null;
            case MAX -> (v instanceof Number n && r.getValor() != null
                    && n.doubleValue() > Double.parseDouble(r.getValor())) ? msg : null;
            case POSITIVE -> (v instanceof Number n && n.doubleValue() <= 0) ? msg : null;
            case NEGATIVE -> (v instanceof Number n && n.doubleValue() >= 0) ? msg : null;
            case EMAIL -> (v instanceof String s
                    && !Pattern.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$", s)) ? msg : null;
        };
    }

    private String validateSize(Object v, ValidationRule r, String msg) {
        int size = -1;
        if (v instanceof String s) size = s.length();
        else if (v instanceof Collection<?> c) size = c.size();
        if (size < 0) return null;
        if (r.getMin() != null && size < r.getMin()) return msg;
        if (r.getMax() != null && size > r.getMax()) return msg;
        return null;
    }
}
