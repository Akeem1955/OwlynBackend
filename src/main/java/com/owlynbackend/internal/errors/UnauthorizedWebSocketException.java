package com.owlynbackend.internal.errors;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class UnauthorizedWebSocketException extends RuntimeException {
    public UnauthorizedWebSocketException(String message) { super(message); }
}