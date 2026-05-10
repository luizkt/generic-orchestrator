package com.orchestrator.domain.model;

/**
 * Tipos de integração suportados pelo orquestrador na execução de workflows.
 *
 * <p>O tipo {@code DATABASE} foi removido — workflows não fazem mais operações
 * em bancos arbitrários. Persistência de dados de domínio acontece nos
 * serviços downstream (chamados via integrações HTTP/QUEUE).
 */
public enum IntegrationType { HTTP, QUEUE }
