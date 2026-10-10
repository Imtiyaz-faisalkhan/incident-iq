package com.springbootai.incidentiq.exception;

/**
 * Thrown when a monitored service would violate a domain rule (name format, field lengths).
 */
public class InvalidServiceException extends RuntimeException {

    public InvalidServiceException(String message) {
        super(message);
    }
}
