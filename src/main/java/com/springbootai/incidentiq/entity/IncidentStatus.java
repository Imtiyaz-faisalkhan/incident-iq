package com.springbootai.incidentiq.entity;

/**
 * Incident lifecycle. New incidents start as {@link #OPEN}. {@link #INVESTIGATING} is reserved for
 * when an investigation is started (a later phase). {@link #RESOLVED} is terminal: resolved incidents
 * cannot be edited or resolved again (reopening is not supported yet).
 */
public enum IncidentStatus {
    OPEN,
    INVESTIGATING,
    RESOLVED
}
