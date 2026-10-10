package com.springbootai.incidentiq.service;

import com.springbootai.incidentiq.dto.CreateServiceRequest;
import com.springbootai.incidentiq.dto.ServiceResponse;
import com.springbootai.incidentiq.entity.MonitoredService;
import com.springbootai.incidentiq.exception.DuplicateServiceNameException;
import com.springbootai.incidentiq.exception.ServiceNotFoundException;
import com.springbootai.incidentiq.repository.MonitoredServiceRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonitoredServiceServiceTest {

    @Mock
    private MonitoredServiceRepository repository;

    @InjectMocks
    private MonitoredServiceService monitoredServiceService;

    @Test
    void createSavesNewServiceAndReturnsResponse() {
        when(repository.existsByName("payment-service")).thenReturn(false);
        when(repository.saveAndFlush(any(MonitoredService.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ServiceResponse response = monitoredServiceService.create(
                new CreateServiceRequest("payment-service", "Handles payments"));

        ArgumentCaptor<MonitoredService> saved = ArgumentCaptor.forClass(MonitoredService.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("payment-service");
        assertThat(saved.getValue().getDescription()).isEqualTo("Handles payments");
        assertThat(response.name()).isEqualTo("payment-service");
        assertThat(response.description()).isEqualTo("Handles payments");
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.createdAt().truncatedTo(ChronoUnit.MICROS)).isEqualTo(response.createdAt());
    }

    @Test
    void createRejectsExistingNameWithoutSaving() {
        when(repository.existsByName("payment-service")).thenReturn(true);

        assertThatThrownBy(() -> monitoredServiceService.create(new CreateServiceRequest("payment-service", null)))
                .isInstanceOf(DuplicateServiceNameException.class)
                .hasMessageContaining("payment-service");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void createMapsNameUniqueConstraintViolationToDuplicate() {
        DataIntegrityViolationException violation = integrityViolation(MonitoredService.NAME_UNIQUE_CONSTRAINT);
        when(repository.existsByName("payment-service")).thenReturn(false);
        when(repository.saveAndFlush(any(MonitoredService.class))).thenThrow(violation);

        assertThatThrownBy(() -> monitoredServiceService.create(new CreateServiceRequest("payment-service", null)))
                .isInstanceOf(DuplicateServiceNameException.class)
                .hasCause(violation);
    }

    @Test
    void createRethrowsOtherIntegrityViolations() {
        DataIntegrityViolationException violation = integrityViolation("some_other_constraint");
        when(repository.existsByName("payment-service")).thenReturn(false);
        when(repository.saveAndFlush(any(MonitoredService.class))).thenThrow(violation);

        assertThatThrownBy(() -> monitoredServiceService.create(new CreateServiceRequest("payment-service", null)))
                .isSameAs(violation);
    }

    @Test
    void createRethrowsIntegrityViolationWithoutConstraintDetails() {
        DataIntegrityViolationException violation = new DataIntegrityViolationException("value too long");
        when(repository.existsByName("payment-service")).thenReturn(false);
        when(repository.saveAndFlush(any(MonitoredService.class))).thenThrow(violation);

        assertThatThrownBy(() -> monitoredServiceService.create(new CreateServiceRequest("payment-service", null)))
                .isSameAs(violation);
    }

    @Test
    void getReturnsExistingService() {
        UUID id = UUID.randomUUID();
        MonitoredService service = new MonitoredService("payment-service", null, Instant.now());
        when(repository.findById(id)).thenReturn(Optional.of(service));

        ServiceResponse response = monitoredServiceService.get(id);

        assertThat(response.name()).isEqualTo("payment-service");
    }

    @Test
    void getThrowsNotFoundForMissingService() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> monitoredServiceService.get(id))
                .isInstanceOf(ServiceNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    /** Mirrors how Spring translates a Hibernate constraint violation. */
    private static DataIntegrityViolationException integrityViolation(String constraintName) {
        ConstraintViolationException cause = new ConstraintViolationException(
                "constraint violated", new SQLException("constraint violated", "23505"), constraintName);
        return new DataIntegrityViolationException("could not execute statement", cause);
    }
}
