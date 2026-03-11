package com.owlynbackend.internal.errors;

public class WorkspaceAccessDeniedException extends RuntimeException {
    public WorkspaceAccessDeniedException(String message) { super(message); }
}