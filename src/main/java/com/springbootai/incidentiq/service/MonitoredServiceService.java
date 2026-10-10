package com.springbootai.incidentiq.service;

import com.springbootai.incidentiq.dto.CreateServiceRequest;
import com.springbootai.incidentiq.dto.ServiceResponse;
import com.springbootai.incidentiq.entity.MonitoredService;
import com.springbootai.incidentiq.exception.DuplicateServiceNameException;
import com.springbootai.incidentiq.exception.ServiceNotFoundException;
import com.springbootai.incidentiq.repository.MonitoredServiceRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class MonitoredServiceService {

    private final MonitoredServiceRepository repository;

    public MonitoredServiceService(MonitoredServiceRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ServiceResponse create(CreateServiceRequest request) {
        if (repository.existsByName(request.name())) {
            throw new DuplicateServiceNameException(request.name());
        }
        // PostgreSQL stores microseconds; truncate so the create and read responses match.
        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        MonitoredService service = new MonitoredService(request.name(), request.description(), createdAt);
        try {
            return ServiceResponse.from(repository.saveAndFlush(service));
        } catch (DataIntegrityViolationException e) {
            // A concurrent request created the same name between the check and the insert.
            // Any other integrity violation is unexpected and must not be reported as a duplicate.
            if (violatesConstraint(e, MonitoredService.NAME_UNIQUE_CONSTRAINT)) {
                throw new DuplicateServiceNameException(request.name(), e);
            }
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public ServiceResponse get(UUID id) {
        return repository.findById(id)
                .map(ServiceResponse::from)
                .orElseThrow(() -> new ServiceNotFoundException(id));
    }

    private static boolean violatesConstraint(DataIntegrityViolationException e, String constraintName) {
        for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return constraintName.equalsIgnoreCase(violation.getConstraintName());
            }
        }
        return false;
    }
}
