package com.springbootai.incidentiq.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.springbootai.incidentiq.entity.Incident;
import com.springbootai.incidentiq.entity.IncidentSeverity;
import com.springbootai.incidentiq.util.TextRules;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Request to open an incident. The status is always {@code OPEN} on creation and cannot be supplied:
 * any field other than those below is recorded in {@code unknownFields} and rejected by the service.
 *
 * @param startedAt     optional; defaults to the creation time. Must not be before
 *                      {@link Incident#MIN_STARTED_AT} or more than {@link Incident#MAX_CLOCK_SKEW} in the
 *                      future; a value slightly in the future (client clock skew) is stored as the creation
 *                      time. The stored value is returned in the response.
 * @param unknownFields names of unexpected JSON fields; values are discarded (see {@link UnknownFields})
 */
public record CreateIncidentRequest(

        @NotNull
        UUID serviceId,

        @NotNull
        @Pattern(regexp = TextRules.NOT_BLANK_PATTERN, message = "must not be blank")
        @Size(max = Incident.TITLE_MAX_LENGTH)
        String title,

        @NotNull
        @Pattern(regexp = TextRules.NOT_BLANK_PATTERN, message = "must not be blank")
        @Size(max = Incident.DESCRIPTION_MAX_LENGTH)
        String description,

        @NotNull
        IncidentSeverity severity,

        Instant startedAt,

        @JsonAnySetter
        Map<String, Object> unknownFields
) {

    public CreateIncidentRequest {
        unknownFields = UnknownFields.namesOnly(unknownFields);
    }

    public CreateIncidentRequest(UUID serviceId, String title, String description, IncidentSeverity severity,
                                 Instant startedAt) {
        this(serviceId, title, description, severity, startedAt, Map.of());
    }
}
