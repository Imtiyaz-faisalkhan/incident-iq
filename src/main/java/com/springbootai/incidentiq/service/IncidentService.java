package com.springbootai.incidentiq.service;

import com.springbootai.incidentiq.dto.CreateIncidentRequest;
import com.springbootai.incidentiq.dto.IncidentResponse;
import com.springbootai.incidentiq.dto.PageResponse;
import com.springbootai.incidentiq.dto.UpdateIncidentRequest;
import com.springbootai.incidentiq.entity.Incident;
import com.springbootai.incidentiq.entity.IncidentSeverity;
import com.springbootai.incidentiq.entity.IncidentStatus;
import com.springbootai.incidentiq.entity.MonitoredService;
import com.springbootai.incidentiq.exception.IncidentNotFoundException;
import com.springbootai.incidentiq.exception.InvalidIncidentException;
import com.springbootai.incidentiq.exception.InvalidPageRequestException;
import com.springbootai.incidentiq.exception.ServiceNotFoundException;
import com.springbootai.incidentiq.repository.IncidentRepository;
import com.springbootai.incidentiq.repository.IncidentSpecifications;
import com.springbootai.incidentiq.repository.MonitoredServiceRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class IncidentService {

    public static final int MAX_PAGE_SIZE = 100;

    /** Caps the page number, which also bounds the SQL OFFSET (at most MAX_PAGE * MAX_PAGE_SIZE rows). */
    public static final int MAX_PAGE = 10_000;

    /** Newest incidents first; id as a tie-breaker keeps page boundaries stable. */
    private static final Sort LIST_ORDER = Sort.by(Sort.Order.desc("startedAt"), Sort.Order.desc("id"));

    private final IncidentRepository incidentRepository;
    private final MonitoredServiceRepository serviceRepository;
    private final Clock clock;

    public IncidentService(IncidentRepository incidentRepository, MonitoredServiceRepository serviceRepository,
                           Clock clock) {
        this.incidentRepository = incidentRepository;
        this.serviceRepository = serviceRepository;
        this.clock = clock;
    }

    @Transactional
    public IncidentResponse create(CreateIncidentRequest request) {
        if (!request.unknownFields().isEmpty()) {
            throw new InvalidIncidentException("Unknown or read-only fields are not allowed; "
                    + "accepted fields are serviceId, title, description, severity and startedAt");
        }
        MonitoredService service = serviceRepository.findById(request.serviceId())
                .orElseThrow(() -> new ServiceNotFoundException(request.serviceId()));
        Instant startedAt = request.startedAt() != null ? truncate(request.startedAt()) : null;
        Incident incident = new Incident(
                service, request.title(), request.description(), request.severity(), startedAt, now());
        // Flush so database errors surface here rather than at commit.
        return IncidentResponse.from(incidentRepository.saveAndFlush(incident));
    }

    @Transactional(readOnly = true)
    public IncidentResponse get(UUID id) {
        return IncidentResponse.from(findIncident(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<IncidentResponse> list(UUID serviceId, IncidentStatus status, IncidentSeverity severity,
                                               int page, int size) {
        if (page < 0 || page > MAX_PAGE) {
            throw new InvalidPageRequestException("page must be between 0 and " + MAX_PAGE);
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidPageRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        return PageResponse.from(
                incidentRepository.findAll(
                        IncidentSpecifications.matching(serviceId, status, severity),
                        PageRequest.of(page, size, LIST_ORDER)),
                IncidentResponse::from);
    }

    @Transactional
    public IncidentResponse update(UUID id, UpdateIncidentRequest request) {
        // Validate the request before looking up the incident, so a bad body is 400 whether or not it exists.
        if (!request.unknownFields().isEmpty()) {
            throw new InvalidIncidentException("Unknown or read-only fields are not allowed; "
                    + "accepted fields are title, description and severity "
                    + "(use POST /api/v1/incidents/{id}/resolve to resolve an incident)");
        }
        if (request.isEmpty()) {
            throw new InvalidIncidentException(Incident.EMPTY_UPDATE_MESSAGE);
        }
        Incident incident = findIncident(id);
        incident.update(request.title(), request.description(), request.severity(), now());
        return IncidentResponse.from(incidentRepository.saveAndFlush(incident));
    }

    @Transactional
    public IncidentResponse resolve(UUID id) {
        Incident incident = findIncident(id);
        incident.resolve(now());
        return IncidentResponse.from(incidentRepository.saveAndFlush(incident));
    }

    private Incident findIncident(UUID id) {
        return incidentRepository.findById(id).orElseThrow(() -> new IncidentNotFoundException(id));
    }

    // PostgreSQL stores microseconds; truncate so write and read responses match.
    private Instant now() {
        return truncate(clock.instant());
    }

    private static Instant truncate(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MICROS);
    }
}
