package com.springbootai.incidentiq.dto;

import com.springbootai.incidentiq.entity.Incident;
import com.springbootai.incidentiq.entity.IncidentSeverity;
import com.springbootai.incidentiq.entity.IncidentStatus;

import java.time.Instant;
import java.util.UUID;

public record IncidentResponse(
        UUID id,
        UUID serviceId,
        String title,
        String description,
        IncidentSeverity severity,
        IncidentStatus status,
        Instant startedAt,
        Instant resolvedAt,
        Instant createdAt,
        Instant updatedAt
) {

    public static IncidentResponse from(Incident incident) {
        return new IncidentResponse(
                incident.getId(),
                incident.getService().getId(),
                incident.getTitle(),
                incident.getDescription(),
                incident.getSeverity(),
                incident.getStatus(),
                incident.getStartedAt(),
                incident.getResolvedAt(),
                incident.getCreatedAt(),
                incident.getUpdatedAt()
        );
    }
}
