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
import com.springbootai.incidentiq.exception.InvalidIncidentStateException;
import com.springbootai.incidentiq.exception.InvalidPageRequestException;
import com.springbootai.incidentiq.exception.ServiceNotFoundException;
import com.springbootai.incidentiq.repository.IncidentRepository;
import com.springbootai.incidentiq.repository.MonitoredServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

    private static final UUID SERVICE_ID = UUID.randomUUID();
    private static final UUID INCIDENT_ID = UUID.randomUUID();
    /** Includes nanoseconds to prove timestamps are truncated to microseconds. */
    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00.123456789Z");
    private static final Instant NOW_MICROS = NOW.truncatedTo(ChronoUnit.MICROS);
    private static final Instant CREATED_AT = Instant.parse("2026-10-10T11:00:00Z");

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private MonitoredServiceRepository serviceRepository;

    private IncidentService incidentService;

    @BeforeEach
    void setUp() {
        incidentService = new IncidentService(
                incidentRepository, serviceRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // --- create ---

    @Test
    void createOpensIncidentForExistingServiceWithClockTimestamps() {
        MonitoredService service = service();
        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(service));
        when(incidentRepository.saveAndFlush(any(Incident.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IncidentResponse response = incidentService.create(new CreateIncidentRequest(
                SERVICE_ID, "DB timeouts", "Checkout fails", IncidentSeverity.HIGH, null));

        ArgumentCaptor<Incident> saved = ArgumentCaptor.forClass(Incident.class);
        verify(incidentRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getService()).isSameAs(service);
        assertThat(response.status()).isEqualTo(IncidentStatus.OPEN);
        assertThat(response.severity()).isEqualTo(IncidentSeverity.HIGH);
        assertThat(response.resolvedAt()).isNull();
        assertThat(response.createdAt()).isEqualTo(NOW_MICROS);
        assertThat(response.updatedAt()).isEqualTo(NOW_MICROS);
        assertThat(response.startedAt()).isEqualTo(NOW_MICROS);
    }

    @Test
    void createTruncatesSuppliedStartedAtToMicroseconds() {
        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(service()));
        when(incidentRepository.saveAndFlush(any(Incident.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Instant startedAt = Instant.parse("2026-10-10T10:00:00.123456789Z");

        IncidentResponse response = incidentService.create(new CreateIncidentRequest(
                SERVICE_ID, "t", "d", IncidentSeverity.LOW, startedAt));

        assertThat(response.startedAt()).isEqualTo(Instant.parse("2026-10-10T10:00:00.123456Z"));
    }

    @Test
    void createRejectsUnknownServiceWithoutSaving() {
        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> incidentService.create(new CreateIncidentRequest(
                SERVICE_ID, "t", "d", IncidentSeverity.LOW, null)))
                .isInstanceOf(ServiceNotFoundException.class)
                .hasMessageContaining(SERVICE_ID.toString());
        verify(incidentRepository, never()).saveAndFlush(any());
    }

    @Test
    void createRejectsUnknownOrReadOnlyFieldsWithoutTouchingRepositories() {
        CreateIncidentRequest request = new CreateIncidentRequest(
                SERVICE_ID, "t", "d", IncidentSeverity.LOW, null, Map.of("status", "RESOLVED"));

        assertThatThrownBy(() -> incidentService.create(request))
                .isInstanceOf(InvalidIncidentException.class)
                .hasMessageStartingWith("Unknown or read-only fields are not allowed")
                .hasMessageNotContaining("RESOLVED");
        verifyNoInteractions(serviceRepository, incidentRepository);
    }

    // --- get ---

    @Test
    void getReturnsExistingIncident() {
        when(incidentRepository.findById(INCIDENT_ID)).thenReturn(Optional.of(openIncident()));

        assertThat(incidentService.get(INCIDENT_ID).title()).isEqualTo("DB timeouts");
    }

    @Test
    void getThrowsNotFoundForMissingIncident() {
        when(incidentRepository.findById(INCIDENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> incidentService.get(INCIDENT_ID))
                .isInstanceOf(IncidentNotFoundException.class)
                .hasMessageContaining(INCIDENT_ID.toString());
    }

    // --- list ---

    @Test
    @SuppressWarnings("unchecked")
    void listRequestsPageNewestFirstAndMapsResults() {
        when(incidentRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(
                        List.of(openIncident()), invocation.getArgument(1), 21));

        PageResponse<IncidentResponse> page =
                incidentService.list(SERVICE_ID, IncidentStatus.OPEN, IncidentSeverity.HIGH, 1, 10);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(incidentRepository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Order.desc("startedAt"), Sort.Order.desc("id")));
        assertThat(page.content()).hasSize(1);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(10);
        assertThat(page.totalElements()).isEqualTo(21);
        assertThat(page.totalPages()).isEqualTo(3);
    }

    @Test
    void listRejectsOutOfRangePageRequestWithDeliberateException() {
        assertThatThrownBy(() -> incidentService.list(null, null, null, -1, 20))
                .isInstanceOf(InvalidPageRequestException.class);
        assertThatThrownBy(() -> incidentService.list(null, null, null, IncidentService.MAX_PAGE + 1, 20))
                .isInstanceOf(InvalidPageRequestException.class);
        assertThatThrownBy(() -> incidentService.list(null, null, null, 0, 0))
                .isInstanceOf(InvalidPageRequestException.class);
        assertThatThrownBy(() -> incidentService.list(null, null, null, 0, IncidentService.MAX_PAGE_SIZE + 1))
                .isInstanceOf(InvalidPageRequestException.class);
        verifyNoInteractions(incidentRepository);
    }

    // --- update ---

    @Test
    void updateAppliesEditableFieldsWithClockTimestamp() {
        Incident incident = openIncident();
        when(incidentRepository.findById(INCIDENT_ID)).thenReturn(Optional.of(incident));
        when(incidentRepository.saveAndFlush(incident)).thenReturn(incident);

        IncidentResponse response = incidentService.update(INCIDENT_ID,
                new UpdateIncidentRequest("New title", "New description", IncidentSeverity.CRITICAL));

        assertThat(response.title()).isEqualTo("New title");
        assertThat(response.description()).isEqualTo("New description");
        assertThat(response.severity()).isEqualTo(IncidentSeverity.CRITICAL);
        assertThat(response.status()).isEqualTo(IncidentStatus.OPEN);
        assertThat(response.createdAt()).isEqualTo(CREATED_AT);
        assertThat(response.updatedAt()).isEqualTo(NOW_MICROS);
    }

    @Test
    void updateRejectsUnknownOrReadOnlyFieldsWithoutLoading() {
        UpdateIncidentRequest request = new UpdateIncidentRequest(
                "New title", null, null, Map.of("startedAt", "2026-01-01T00:00:00Z"));

        assertThatThrownBy(() -> incidentService.update(INCIDENT_ID, request))
                .isInstanceOf(InvalidIncidentException.class)
                .hasMessageStartingWith("Unknown or read-only fields are not allowed; "
                        + "accepted fields are title, description and severity");
        verifyNoInteractions(incidentRepository);
    }

    @Test
    void emptyUpdateIsRejectedBeforeLookupSoMissingIncidentStillGets400() {
        assertThatThrownBy(() -> incidentService.update(INCIDENT_ID, new UpdateIncidentRequest(null, null, null)))
                .isInstanceOf(InvalidIncidentException.class)
                .hasMessage(Incident.EMPTY_UPDATE_MESSAGE);
        verifyNoInteractions(incidentRepository);
    }

    @Test
    void updateThrowsNotFoundForMissingIncident() {
        when(incidentRepository.findById(INCIDENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> incidentService.update(INCIDENT_ID, new UpdateIncidentRequest("t", null, null)))
                .isInstanceOf(IncidentNotFoundException.class);
    }

    @Test
    void updateOfResolvedIncidentIsRejectedWithoutSaving() {
        Incident incident = openIncident();
        incident.resolve(CREATED_AT.plusSeconds(60));
        when(incidentRepository.findById(INCIDENT_ID)).thenReturn(Optional.of(incident));

        assertThatThrownBy(() -> incidentService.update(INCIDENT_ID, new UpdateIncidentRequest("t", null, null)))
                .isInstanceOf(InvalidIncidentStateException.class);
        verify(incidentRepository, never()).saveAndFlush(any());
    }

    // --- resolve ---

    @Test
    void resolveSetsResolvedStatusAndClockTimestamp() {
        Incident incident = openIncident();
        when(incidentRepository.findById(INCIDENT_ID)).thenReturn(Optional.of(incident));
        when(incidentRepository.saveAndFlush(incident)).thenReturn(incident);

        IncidentResponse response = incidentService.resolve(INCIDENT_ID);

        assertThat(response.status()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(response.resolvedAt()).isEqualTo(NOW_MICROS);
        assertThat(response.updatedAt()).isEqualTo(NOW_MICROS);
    }

    @Test
    void resolvingAlreadyResolvedIncidentIsRejectedWithoutSaving() {
        Incident incident = openIncident();
        incident.resolve(CREATED_AT.plusSeconds(60));
        when(incidentRepository.findById(INCIDENT_ID)).thenReturn(Optional.of(incident));

        assertThatThrownBy(() -> incidentService.resolve(INCIDENT_ID))
                .isInstanceOf(InvalidIncidentStateException.class);
        verify(incidentRepository, never()).saveAndFlush(any());
    }

    @Test
    void resolveThrowsNotFoundForMissingIncident() {
        when(incidentRepository.findById(INCIDENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> incidentService.resolve(INCIDENT_ID))
                .isInstanceOf(IncidentNotFoundException.class);
    }

    private static MonitoredService service() {
        return new MonitoredService("payment-service", null, CREATED_AT);
    }

    private static Incident openIncident() {
        return new Incident(service(), "DB timeouts", "Checkout fails", IncidentSeverity.HIGH, null, CREATED_AT);
    }
}
