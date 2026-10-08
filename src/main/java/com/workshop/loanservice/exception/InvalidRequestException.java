package com.workshop.loanservice.exception;

/**
 * Thrown when request parameters are syntactically or semantically invalid.
 * Mapped to HTTP 400 by {@link GlobalExceptionHandler}.
 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
