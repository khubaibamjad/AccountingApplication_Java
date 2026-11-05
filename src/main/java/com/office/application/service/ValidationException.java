package com.office.application.service;

/** A business rule was broken. The message is safe to show to the user. */
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}
