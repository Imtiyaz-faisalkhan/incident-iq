package com.springbootai.incidentiq.exception;

/**
 * Thrown when an action is not allowed in the incident's current status,
 * e.g. resolving or editing an already resolved incident.
 */
public class InvalidIncidentStateException extends RuntimeException {

    public InvalidIncidentStateException(String message) {
        super(message);
    }
}
