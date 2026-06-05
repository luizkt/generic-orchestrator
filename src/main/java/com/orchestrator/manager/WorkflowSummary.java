package com.orchestrator.manager;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO recebido em `GET /manager/flows?status=active` (Manager) — apenas os campos
 * que o orquestrador usa para warm-up do cache. Outros campos são ignorados.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkflowSummary {
    private String flowId;
    private String version;
    private boolean active;
}
