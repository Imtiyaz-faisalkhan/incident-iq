package com.springbootai.incidentiq.entity;

import com.springbootai.incidentiq.exception.InvalidServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MonitoredServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");

    @Test
    void createsServiceWithValidName() {
        MonitoredService service = new MonitoredService("payment-service", "Handles payments", NOW);

        assertThat(service.getName()).isEqualTo("payment-service");
        assertThat(service.getDescription()).isEqualTo("Handles payments");
        assertThat(service.getCreatedAt()).isEqualTo(NOW);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "Payment-Service", "payment service", "-payment", "payment--service", "payment-"})
    void rejectsInvalidName(String name) {
        assertThatThrownBy(() -> new MonitoredService(name, null, NOW))
                .isInstanceOf(InvalidServiceException.class);
    }

    @Test
    void acceptsNameAtMaximumLength() {
        String name = "a".repeat(MonitoredService.NAME_MAX_LENGTH);

        assertThat(new MonitoredService(name, null, NOW).getName()).isEqualTo(name);
    }

    @Test
    void rejectsNameLongerThanMaximum() {
        String tooLong = "a".repeat(MonitoredService.NAME_MAX_LENGTH + 1);

        assertThatThrownBy(() -> new MonitoredService(tooLong, null, NOW))
                .isInstanceOf(InvalidServiceException.class)
                .hasMessageContaining("at most " + MonitoredService.NAME_MAX_LENGTH);
    }

    @Test
    void acceptsDescriptionAtMaximumLength() {
        String description = "a".repeat(MonitoredService.DESCRIPTION_MAX_LENGTH);

        assertThat(new MonitoredService("payment-service", description, NOW).getDescription()).isEqualTo(description);
    }

    @Test
    void rejectsDescriptionLongerThanMaximum() {
        String tooLong = "a".repeat(MonitoredService.DESCRIPTION_MAX_LENGTH + 1);

        assertThatThrownBy(() -> new MonitoredService("payment-service", tooLong, NOW))
                .isInstanceOf(InvalidServiceException.class)
                .hasMessageContaining("at most " + MonitoredService.DESCRIPTION_MAX_LENGTH);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t\n"})
    void storesBlankDescriptionAsNull(String description) {
        assertThat(new MonitoredService("payment-service", description, NOW).getDescription()).isNull();
    }

    @Test
    void stripsSurroundingWhitespaceFromDescription() {
        MonitoredService service = new MonitoredService("payment-service", "  Handles payments  ", NOW);

        assertThat(service.getDescription()).isEqualTo("Handles payments");
    }
}
