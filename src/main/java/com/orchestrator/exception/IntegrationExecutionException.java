package com.orchestrator.exception;
public class IntegrationExecutionException extends RuntimeException {
    public IntegrationExecutionException(String m) { super(m); }
    public IntegrationExecutionException(String m, Throwable t) { super(m, t); }
}
