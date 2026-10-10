package com.springbootai.incidentiq.exception;

/**
 * Thrown when a page number or page size is outside the supported range.
 */
public class InvalidPageRequestException extends RuntimeException {

    public InvalidPageRequestException(String message) {
        super(message);
    }
}
