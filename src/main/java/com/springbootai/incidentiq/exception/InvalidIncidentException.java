package com.springbootai.incidentiq.exception;

/**
 * Thrown when an incident would violate a domain rule (required fields, lengths, start time).
 */
public class InvalidIncidentException extends RuntimeException {

    public InvalidIncidentException(String message) {
        super(message);
    }
}
