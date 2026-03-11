package com.owlynbackend.internal.errors;

public class InterviewAlreadyActiveException extends RuntimeException {
    public InterviewAlreadyActiveException(String message) { super(message); }
}