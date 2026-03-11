package com.owlynbackend.internal.errors;

public class AgentConnectionException extends RuntimeException {
    public AgentConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}