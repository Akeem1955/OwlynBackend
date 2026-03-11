package com.owlynbackend.internal.errors;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String message) { super(message); }
}