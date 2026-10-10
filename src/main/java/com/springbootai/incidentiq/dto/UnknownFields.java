package com.springbootai.incidentiq.dto;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Helper for request records that capture unexpected JSON fields via {@code @JsonAnySetter}.
 * Only the field names are kept: client-supplied values (possibly large or nested) are discarded
 * and never appear in {@code toString()} or logs.
 */
final class UnknownFields {

    /** Placeholder value; the original value is intentionally not retained. */
    static final String OMITTED = "<omitted>";

    private UnknownFields() {
    }

    /** Returns an unmodifiable map with the same keys and {@link #OMITTED} values; {@code null} becomes empty. */
    static Map<String, Object> namesOnly(Map<String, Object> fields) {
        if (fields == null || fields.isEmpty()) {
            return Map.of();
        }
        return fields.keySet().stream()
                .collect(Collectors.toUnmodifiableMap(name -> name, name -> OMITTED));
    }
}
