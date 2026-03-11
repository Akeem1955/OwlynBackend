package com.owlynbackend.internal.errors;

public class WorkspaceMemberNotFoundException extends RuntimeException {
    public WorkspaceMemberNotFoundException(String message) { super(message); }
}