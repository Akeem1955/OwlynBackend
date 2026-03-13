package com.owlynbackend.internal.errors;

public class ReportNotReadyException extends RuntimeException {
    public ReportNotReadyException(String message) {
        super(message);
    }
}
