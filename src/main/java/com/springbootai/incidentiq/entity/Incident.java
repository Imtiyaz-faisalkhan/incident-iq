package com.springbootai.incidentiq.entity;

import com.springbootai.incidentiq.exception.InvalidIncidentException;
import com.springbootai.incidentiq.exception.InvalidIncidentStateException;
import com.springbootai.incidentiq.util.TextRules;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A problem affecting a {@link MonitoredService}. See {@link IncidentStatus} for the lifecycle policy.
 */
@Entity
@Table(
        name = "incident",
        indexes = {
                // Matches the main list query: filter by service, newest first (see IncidentService.list).
                @Index(name = "idx_incident_service_started_at", columnList = "service_id, started_at DESC, id DESC"),
                @Index(name = "idx_incident_started_at", columnList = "started_at")
        }
)
public class Incident {

    public static final int TITLE_MAX_LENGTH = 200;
    public static final int DESCRIPTION_MAX_LENGTH = 4000;

    /**
     * Tolerance for client clocks running slightly ahead when supplying {@code startedAt}.
     * Values inside the tolerance are stored as "now" so {@code startedAt} never exceeds {@code createdAt}.
     */
    public static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(1);

    public static final String EMPTY_UPDATE_MESSAGE = "At least one of title, description or severity must be provided";

    /** Earliest accepted {@code startedAt}; rejects meaningless or out-of-range dates. */
    public static final Instant MIN_STARTED_AT = Instant.parse("2000-01-01T00:00:00Z");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false, foreignKey = @ForeignKey(name = "fk_incident_service"))
    private MonitoredService service;

    @Column(nullable = false, length = TITLE_MAX_LENGTH)
    private String title;

    @Column(nullable = false, length = DESCRIPTION_MAX_LENGTH)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Optimistic locking: concurrent updates to the same incident fail instead of overwriting each other. */
    @Version
    private long version;

    protected Incident() {
        // required by JPA
    }

    /**
     * Creates a new {@link IncidentStatus#OPEN} incident.
     *
     * @param startedAt when the problem started; {@code null} means "now"
     * @param now       the current time, used for {@code createdAt}/{@code updatedAt}
     */
    public Incident(MonitoredService service, String title, String description, IncidentSeverity severity,
                    Instant startedAt, Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (service == null) {
            throw new InvalidIncidentException("Incident service is required");
        }
        if (severity == null) {
            throw new InvalidIncidentException("Incident severity is required");
        }
        this.service = service;
        this.title = requireTitle(title);
        this.description = requireDescription(description);
        this.severity = severity;
        this.status = IncidentStatus.OPEN;
        this.startedAt = effectiveStartedAt(startedAt, now);
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Updates the editable fields; {@code null} arguments leave a field unchanged, and at least one
     * must be provided. {@code updatedAt} only changes if a value actually changed, so a retried
     * request is not recorded as a new modification. Resolved incidents cannot be edited.
     */
    public void update(String newTitle, String newDescription, IncidentSeverity newSeverity, Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (newTitle == null && newDescription == null && newSeverity == null) {
            throw new InvalidIncidentException(EMPTY_UPDATE_MESSAGE);
        }
        if (status == IncidentStatus.RESOLVED) {
            throw new InvalidIncidentStateException("Resolved incident " + id + " cannot be edited");
        }
        // Validate everything before assigning anything, so a partly invalid request changes nothing.
        String updatedTitle = newTitle != null ? requireTitle(newTitle) : title;
        String updatedDescription = newDescription != null ? requireDescription(newDescription) : description;
        IncidentSeverity updatedSeverity = newSeverity != null ? newSeverity : severity;
        boolean changed = !updatedTitle.equals(title)
                || !updatedDescription.equals(description)
                || updatedSeverity != severity;
        if (changed) {
            this.title = updatedTitle;
            this.description = updatedDescription;
            this.severity = updatedSeverity;
            this.updatedAt = now;
        }
    }

    /** Resolves an OPEN or INVESTIGATING incident. Resolving an already resolved incident is rejected. */
    public void resolve(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (status == IncidentStatus.RESOLVED) {
            throw new InvalidIncidentStateException("Incident " + id + " is already resolved");
        }
        this.status = IncidentStatus.RESOLVED;
        this.resolvedAt = now;
        this.updatedAt = now;
    }

    private static Instant effectiveStartedAt(Instant startedAt, Instant now) {
        if (startedAt == null) {
            return now;
        }
        if (startedAt.isBefore(MIN_STARTED_AT)) {
            throw new InvalidIncidentException("Incident startedAt must not be before " + MIN_STARTED_AT);
        }
        if (startedAt.isAfter(now.plus(MAX_CLOCK_SKEW))) {
            throw new InvalidIncidentException("Incident startedAt must not be in the future");
        }
        // Slightly ahead (client clock skew): clamp so startedAt <= createdAt and later resolvedAt.
        return startedAt.isAfter(now) ? now : startedAt;
    }

    private static String requireTitle(String title) {
        return requireText(title, "title", TITLE_MAX_LENGTH);
    }

    private static String requireDescription(String description) {
        return requireText(description, "description", DESCRIPTION_MAX_LENGTH);
    }

    private static String requireText(String value, String field, int maxLength) {
        if (TextRules.isBlank(value)) {
            throw new InvalidIncidentException("Incident " + field + " is required");
        }
        if (TextRules.containsNul(value)) {
            throw new InvalidIncidentException("Incident " + field + " must not contain NUL characters");
        }
        String stripped = value.strip();
        if (stripped.length() > maxLength) {
            throw new InvalidIncidentException(
                    "Incident " + field + " must be at most " + maxLength + " characters");
        }
        return stripped;
    }

    public UUID getId() {
        return id;
    }

    public MonitoredService getService() {
        return service;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public IncidentSeverity getSeverity() {
        return severity;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
