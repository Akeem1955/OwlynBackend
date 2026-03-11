package com.owlynbackend.internal.errors;

public class InvalidAccessCodeException extends RuntimeException {
    public InvalidAccessCodeException(String message) { super(message); }
}