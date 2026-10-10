package com.springbootai.incidentiq.entity;

import com.springbootai.incidentiq.exception.InvalidIncidentException;
import com.springbootai.incidentiq.exception.InvalidIncidentStateException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IncidentTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");
    private static final MonitoredService SERVICE = new MonitoredService("payment-service", null, NOW);

    @Test
    void newIncidentIsOpenWithConsistentTimestamps() {
        Instant startedAt = NOW.minusSeconds(600);

        Incident incident = new Incident(SERVICE, "DB timeouts", "Checkout fails", IncidentSeverity.HIGH, startedAt, NOW);

        assertThat(incident.getService()).isSameAs(SERVICE);
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(incident.getSeverity()).isEqualTo(IncidentSeverity.HIGH);
        assertThat(incident.getStartedAt()).isEqualTo(startedAt);
        assertThat(incident.getResolvedAt()).isNull();
        assertThat(incident.getCreatedAt()).isEqualTo(NOW);
        assertThat(incident.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void startedAtDefaultsToNow() {
        Incident incident = new Incident(SERVICE, "DB timeouts", "Checkout fails", IncidentSeverity.LOW, null, NOW);

        assertThat(incident.getStartedAt()).isEqualTo(NOW);
    }

    @Test
    void startedAtWithinClockSkewToleranceIsClampedToNow() {
        Instant slightlyAhead = NOW.plus(Incident.MAX_CLOCK_SKEW);

        Incident incident = new Incident(SERVICE, "t", "d", IncidentSeverity.LOW, slightlyAhead, NOW);

        assertThat(incident.getStartedAt()).isEqualTo(NOW);
        assertThat(incident.getStartedAt()).isBeforeOrEqualTo(incident.getCreatedAt());
    }

    @Test
    void startedAtAtMinimumIsAcceptedAndEarlierIsRejected() {
        Incident incident = new Incident(SERVICE, "t", "d", IncidentSeverity.LOW, Incident.MIN_STARTED_AT, NOW);

        assertThat(incident.getStartedAt()).isEqualTo(Incident.MIN_STARTED_AT);
        assertThatThrownBy(() -> new Incident(
                SERVICE, "t", "d", IncidentSeverity.LOW, Incident.MIN_STARTED_AT.minusSeconds(1), NOW))
                .isInstanceOf(InvalidIncidentException.class)
                .hasMessageContaining("must not be before");
    }

    @Test
    void startedAtInTheFutureIsRejected() {
        Instant future = NOW.plus(Incident.MAX_CLOCK_SKEW).plusSeconds(1);

        assertThatThrownBy(() -> new Incident(SERVICE, "t", "d", IncidentSeverity.LOW, future, NOW))
                .isInstanceOf(InvalidIncidentException.class)
                .hasMessageContaining("future");
    }

    @Test
    void titleAndDescriptionAreStripped() {
        Incident incident = new Incident(SERVICE, "  DB timeouts ", "\n Checkout fails \n", IncidentSeverity.LOW, null, NOW);

        assertThat(incident.getTitle()).isEqualTo("DB timeouts");
        assertThat(incident.getDescription()).isEqualTo("Checkout fails");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", " ", "　", "  \t"})
    void blankTitleIsRejected(String title) {
        assertThatThrownBy(() -> new Incident(SERVICE, title, "d", IncidentSeverity.LOW, null, NOW))
                .isInstanceOf(InvalidIncidentException.class)
                .hasMessageContaining("title");
    }

    @Test
    void nulCharactersAreRejectedBecausePostgresCannotStoreThem() {
        assertThatThrownBy(() -> new Incident(SERVICE, "db\u0000down", "d", IncidentSeverity.LOW, null, NOW))
                .isInstanceOf(InvalidIncidentException.class)
                .hasMessage("Incident title must not contain NUL characters");
        assertThatThrownBy(() -> new Incident(SERVICE, "t", "d\u0000", IncidentSeverity.LOW, null, NOW))
                .isInstanceOf(InvalidIncidentException.class)
                .hasMessage("Incident description must not contain NUL characters");
        Incident incident = openIncident();
        assertThatThrownBy(() -> incident.update(null, "x\u0000", null, NOW.plusSeconds(1)))
                .isInstanceOf(InvalidIncidentException.class);
        assertThat(incident.getDescription()).isEqualTo("Checkout fails");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", " ", "　"})
    void blankDescriptionIsRejected(String description) {
        assertThatThrownBy(() -> new Incident(SERVICE, "t", description, IncidentSeverity.LOW, null, NOW))
                .isInstanceOf(InvalidIncidentException.class)
                .hasMessageContaining("description");
    }

    @Test
    void fieldsAtMaximumLengthAreAcceptedAndLongerAreRejected() {
        String maxTitle = "t".repeat(Incident.TITLE_MAX_LENGTH);
        String maxDescription = "d".repeat(Incident.DESCRIPTION_MAX_LENGTH);

        Incident incident = new Incident(SERVICE, maxTitle, maxDescription, IncidentSeverity.LOW, null, NOW);

        assertThat(incident.getTitle()).hasSize(Incident.TITLE_MAX_LENGTH);
        assertThat(incident.getDescription()).hasSize(Incident.DESCRIPTION_MAX_LENGTH);
        assertThatThrownBy(() -> new Incident(SERVICE, maxTitle + "t", "d", IncidentSeverity.LOW, null, NOW))
                .isInstanceOf(InvalidIncidentException.class);
        assertThatThrownBy(() -> new Incident(SERVICE, "t", maxDescription + "d", IncidentSeverity.LOW, null, NOW))
                .isInstanceOf(InvalidIncidentException.class);
    }

    @Test
    void serviceAndSeverityAreRequired() {
        assertThatThrownBy(() -> new Incident(null, "t", "d", IncidentSeverity.LOW, null, NOW))
                .isInstanceOf(InvalidIncidentException.class);
        assertThatThrownBy(() -> new Incident(SERVICE, "t", "d", null, null, NOW))
                .isInstanceOf(InvalidIncidentException.class);
    }

    @Test
    void updateChangesOnlyProvidedFieldsAndTouchesUpdatedAt() {
        Incident incident = openIncident();
        Instant later = NOW.plusSeconds(60);

        incident.update("New title", null, IncidentSeverity.CRITICAL, later);

        assertThat(incident.getTitle()).isEqualTo("New title");
        assertThat(incident.getDescription()).isEqualTo("Checkout fails");
        assertThat(incident.getSeverity()).isEqualTo(IncidentSeverity.CRITICAL);
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(incident.getCreatedAt()).isEqualTo(NOW);
        assertThat(incident.getUpdatedAt()).isEqualTo(later);
    }

    @Test
    void updateRejectsBlankTitleAndChangesNothing() {
        Incident incident = openIncident();

        assertThatThrownBy(() -> incident.update("  ", null, IncidentSeverity.CRITICAL, NOW.plusSeconds(1)))
                .isInstanceOf(InvalidIncidentException.class);
        assertThat(incident.getTitle()).isEqualTo("DB timeouts");
        assertThat(incident.getSeverity()).isEqualTo(IncidentSeverity.HIGH);
        assertThat(incident.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void updateRequiresAtLeastOneField() {
        Incident incident = openIncident();

        assertThatThrownBy(() -> incident.update(null, null, null, NOW.plusSeconds(1)))
                .isInstanceOf(InvalidIncidentException.class)
                .hasMessage(Incident.EMPTY_UPDATE_MESSAGE);
    }

    @Test
    void updateWithUnchangedValuesDoesNotTouchUpdatedAt() {
        Incident incident = openIncident();

        incident.update(" DB timeouts ", "Checkout fails", IncidentSeverity.HIGH, NOW.plusSeconds(60));

        assertThat(incident.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void resolveSetsStatusAndTimestamps() {
        Incident incident = openIncident();
        Instant later = NOW.plusSeconds(3600);

        incident.resolve(later);

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(incident.getResolvedAt()).isEqualTo(later);
        assertThat(incident.getUpdatedAt()).isEqualTo(later);
    }

    @Test
    void resolvingTwiceIsRejectedAndKeepsOriginalResolvedAt() {
        Incident incident = openIncident();
        Instant firstResolution = NOW.plusSeconds(60);
        incident.resolve(firstResolution);

        assertThatThrownBy(() -> incident.resolve(NOW.plusSeconds(120)))
                .isInstanceOf(InvalidIncidentStateException.class)
                .hasMessageContaining("already resolved");
        assertThat(incident.getResolvedAt()).isEqualTo(firstResolution);
    }

    @Test
    void resolvedIncidentCannotBeEdited() {
        Incident incident = openIncident();
        incident.resolve(NOW.plusSeconds(60));

        assertThatThrownBy(() -> incident.update("New title", null, null, NOW.plusSeconds(120)))
                .isInstanceOf(InvalidIncidentStateException.class);
        assertThat(incident.getTitle()).isEqualTo("DB timeouts");
    }

    private static Incident openIncident() {
        return new Incident(SERVICE, "DB timeouts", "Checkout fails", IncidentSeverity.HIGH, null, NOW);
    }
}
