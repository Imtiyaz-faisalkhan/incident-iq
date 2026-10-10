package com.springbootai.incidentiq.entity;

import com.springbootai.incidentiq.exception.InvalidServiceException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An application being monitored by IncidentIQ, such as {@code payment-service}.
 */
@Entity
@Table(
        name = "monitored_service",
        uniqueConstraints = @UniqueConstraint(name = MonitoredService.NAME_UNIQUE_CONSTRAINT, columnNames = "name")
)
public class MonitoredService {

    public static final String NAME_UNIQUE_CONSTRAINT = "uk_monitored_service_name";

    /** Lowercase letters/digits separated by single hyphens, e.g. {@code payment-service}. */
    public static final String NAME_PATTERN = "^[a-z0-9]+(-[a-z0-9]+)*$";
    public static final int NAME_MAX_LENGTH = 100;
    public static final int DESCRIPTION_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Column(length = DESCRIPTION_MAX_LENGTH)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MonitoredService() {
        // required by JPA
    }

    public MonitoredService(String name, String description, Instant createdAt) {
        if (name == null || name.isBlank()) {
            throw new InvalidServiceException("Service name is required");
        }
        if (name.length() > NAME_MAX_LENGTH) {
            throw new InvalidServiceException("Service name must be at most " + NAME_MAX_LENGTH + " characters");
        }
        if (!name.matches(NAME_PATTERN)) {
            throw new InvalidServiceException(
                    "Service name must be lowercase letters and digits separated by single hyphens");
        }
        String normalizedDescription = normalizeDescription(description);
        if (normalizedDescription != null && normalizedDescription.length() > DESCRIPTION_MAX_LENGTH) {
            throw new InvalidServiceException(
                    "Service description must be at most " + DESCRIPTION_MAX_LENGTH + " characters");
        }
        this.name = name;
        this.description = normalizedDescription;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    /** Treats a blank description as absent so "no description" is always {@code null}. */
    private static String normalizeDescription(String description) {
        return description == null || description.isBlank() ? null : description.strip();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
