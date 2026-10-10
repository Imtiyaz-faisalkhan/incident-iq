package com.springbootai.incidentiq.dto;

import com.springbootai.incidentiq.entity.MonitoredService;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateServiceRequest(

        @NotBlank
        @Size(max = MonitoredService.NAME_MAX_LENGTH)
        @Pattern(
                regexp = MonitoredService.NAME_PATTERN,
                message = "must be lowercase letters and digits separated by single hyphens, e.g. payment-service"
        )
        String name,

        @Size(max = MonitoredService.DESCRIPTION_MAX_LENGTH)
        String description
) {
}
