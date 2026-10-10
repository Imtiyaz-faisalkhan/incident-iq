package com.springbootai.incidentiq.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.springbootai.incidentiq.entity.Incident;
import com.springbootai.incidentiq.entity.IncidentSeverity;
import com.springbootai.incidentiq.util.TextRules;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * Partial update of an incident's editable fields. Omitted ({@code null}) fields are left unchanged;
 * at least one must be provided.
 * <p>
 * Any other JSON field (e.g. {@code status}, {@code startedAt}, {@code serviceId}) is recorded in
 * {@code unknownFields} and rejected by the service, instead of being silently ignored. Status changes
 * go through dedicated actions such as {@code POST /api/v1/incidents/{id}/resolve}.
 *
 * @param unknownFields names of unexpected JSON fields; values are discarded (see {@link UnknownFields})
 */
public record UpdateIncidentRequest(

        @Size(max = Incident.TITLE_MAX_LENGTH)
        @Pattern(regexp = TextRules.NOT_BLANK_PATTERN, message = "must not be blank")
        String title,

        @Size(max = Incident.DESCRIPTION_MAX_LENGTH)
        @Pattern(regexp = TextRules.NOT_BLANK_PATTERN, message = "must not be blank")
        String description,

        IncidentSeverity severity,

        @JsonAnySetter
        Map<String, Object> unknownFields
) {

    public UpdateIncidentRequest {
        unknownFields = UnknownFields.namesOnly(unknownFields);
    }

    public UpdateIncidentRequest(String title, String description, IncidentSeverity severity) {
        this(title, description, severity, Map.of());
    }

    /** {@code true} if none of the editable fields was provided. */
    public boolean isEmpty() {
        return title == null && description == null && severity == null;
    }
}
