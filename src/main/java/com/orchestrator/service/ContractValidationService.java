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
        if (contract != null && contract.getFields() != null)
            validateFields(contract.getFields(), payload, errors, "");
        if (!errors.isEmpty())
            throw new ContractValidationException("Contract validation errors", errors);
    }

    @SuppressWarnings("unchecked")
    private void validateFields(List<FieldDefinition> fields, Map<String, Object> payload,
                                List<String> errors, String parentPath) {
        for (FieldDefinition f : fields) {
            String path = parentPath.isEmpty() ? f.getName() : parentPath + "." + f.getName();
            Object value = payload != null ? payload.get(f.getName()) : null;

            if (f.isRequired() && (value == null || (value instanceof String s && s.isBlank()))) {
                errors.add(String.format("Field '%s' is required", path));
                continue;
            }
            if (value == null) continue;

            if (!matchesType(value, f.getType())) {
                errors.add(String.format("Field '%s' should be of type %s", path, f.getType()));
                continue;
            }

            if (f.getValidations() != null) {
                for (ValidationRule r : f.getValidations()) {
                    String err = applyRule(r, value, path);
                    if (err != null) errors.add(err);
                }
            }

            if (f.getType() == FieldType.OBJECT && f.getObject() != null && value instanceof Map<?, ?> m) {
                validateFields(f.getObject().getFields(), (Map<String, Object>) m, errors, path);
            } else if (f.getType() == FieldType.ARRAY && f.getObject() != null && value instanceof List<?> l) {
                for (int i = 0; i < l.size(); i++) {
                    if (l.get(i) instanceof Map<?, ?> m) {
                        validateFields(f.getObject().getFields(), (Map<String, Object>) m,
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
        String defaultMsg = String.format("Validation '%s' failed for field '%s'", r.getType(), path);
        String msg = r.getMessage() != null ? r.getMessage() : defaultMsg;
        return switch (r.getType()) {
            case NOT_BLANK -> (v instanceof String s && s.isBlank()) ? msg : null;
            case NOT_EMPTY -> (v instanceof Collection<?> c && c.isEmpty()) ? msg : null;
            case NOT_NULL -> v == null ? msg : null;
            case PATTERN -> (v instanceof String s && r.getValue() != null
                    && !Pattern.matches(r.getValue(), s)) ? msg : null;
            case SIZE -> validateSize(v, r, msg);
            case MIN -> (v instanceof Number n && r.getValue() != null
                    && n.doubleValue() < Double.parseDouble(r.getValue())) ? msg : null;
            case MAX -> (v instanceof Number n && r.getValue() != null
                    && n.doubleValue() > Double.parseDouble(r.getValue())) ? msg : null;
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
