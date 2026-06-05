package com.orchestrator.exception;

/** Sinaliza uma resposta HTTP com status retryable (ex.: 500, 429, 408). */
public class RetriableHttpException extends RuntimeException {

    private final int statusCode;

    public RetriableHttpException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
