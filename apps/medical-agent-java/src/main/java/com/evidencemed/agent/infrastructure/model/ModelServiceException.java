package com.evidencemed.agent.infrastructure.model;

public class ModelServiceException extends RuntimeException {
    private final String service;

    public ModelServiceException(String service, String message, Throwable cause) {
        super(message, cause);
        this.service = service;
    }

    public String getService() { return service; }
}
