package com.springbootai.incidentiq.repository;

import com.springbootai.incidentiq.entity.Incident;
import com.springbootai.incidentiq.entity.IncidentSeverity;
import com.springbootai.incidentiq.entity.IncidentStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds the optional filters for incident search. Each {@code null} argument means "no filter".
 */
public final class IncidentSpecifications {

    private IncidentSpecifications() {
    }

    public static Specification<Incident> matching(UUID serviceId, IncidentStatus status, IncidentSeverity severity) {
        List<Specification<Incident>> filters = new ArrayList<>();
        if (serviceId != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("service").get("id"), serviceId));
        }
        if (status != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (severity != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("severity"), severity));
        }
        return filters.isEmpty() ? Specification.unrestricted() : Specification.allOf(filters);
    }
}
