package com.springbootai.incidentiq.dto;

import com.springbootai.incidentiq.entity.IncidentSeverity;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IncidentRequestTest {

    @Test
    void unknownFieldValuesAreDiscardedAndOnlyNamesKept() {
        Map<String, Object> captured = new HashMap<>();
        captured.put("status", null);
        captured.put("nested", Map.of("deep", List.of("secret-value")));

        UpdateIncidentRequest request = new UpdateIncidentRequest("t", null, null, captured);

        assertThat(request.unknownFields()).containsOnlyKeys("status", "nested");
        assertThat(request.unknownFields().values()).containsOnly(UnknownFields.OMITTED);
        assertThat(request.toString()).doesNotContain("secret-value");
        assertThatThrownBy(() -> request.unknownFields().put("x", 1))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void missingUnknownFieldsBecomeEmpty() {
        CreateIncidentRequest create = new CreateIncidentRequest(
                UUID.randomUUID(), "t", "d", IncidentSeverity.LOW, null, null);
        UpdateIncidentRequest update = new UpdateIncidentRequest("t", null, null);

        assertThat(create.unknownFields()).isEmpty();
        assertThat(update.unknownFields()).isEmpty();
    }

    @Test
    void updateIsEmptyOnlyWhenNoEditableFieldIsProvided() {
        assertThat(new UpdateIncidentRequest(null, null, null).isEmpty()).isTrue();
        assertThat(new UpdateIncidentRequest(null, null, IncidentSeverity.LOW).isEmpty()).isFalse();
        assertThat(new UpdateIncidentRequest(null, null, null, Map.of("status", "x")).isEmpty()).isTrue();
    }
}
