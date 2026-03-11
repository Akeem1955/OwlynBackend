package com.owlynbackend.internal.errors;

public class WorkspaceNotFoundException extends RuntimeException {
    public WorkspaceNotFoundException(String message) { super(message); }
}