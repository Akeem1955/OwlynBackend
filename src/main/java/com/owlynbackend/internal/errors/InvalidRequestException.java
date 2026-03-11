package com.owlynbackend.internal.errors;

public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) { super(message); }
}