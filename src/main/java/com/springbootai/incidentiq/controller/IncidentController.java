package com.springbootai.incidentiq.controller;

import com.springbootai.incidentiq.dto.CreateIncidentRequest;
import com.springbootai.incidentiq.dto.IncidentResponse;
import com.springbootai.incidentiq.dto.PageResponse;
import com.springbootai.incidentiq.dto.UpdateIncidentRequest;
import com.springbootai.incidentiq.entity.IncidentSeverity;
import com.springbootai.incidentiq.entity.IncidentStatus;
import com.springbootai.incidentiq.service.IncidentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @PostMapping
    public ResponseEntity<IncidentResponse> create(@Valid @RequestBody CreateIncidentRequest request) {
        IncidentResponse created = incidentService.create(request);
        // Relative URI: avoids building an absolute URL from the client-supplied Host header.
        return ResponseEntity.created(URI.create("/api/v1/incidents/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public IncidentResponse get(@PathVariable UUID id) {
        return incidentService.get(id);
    }

    @GetMapping
    public PageResponse<IncidentResponse> list(
            @RequestParam(required = false) UUID serviceId,
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) IncidentSeverity severity,
            @RequestParam(defaultValue = "0") @Min(0) @Max(IncidentService.MAX_PAGE) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(IncidentService.MAX_PAGE_SIZE) int size) {
        return incidentService.list(serviceId, status, severity, page, size);
    }

    @PatchMapping("/{id}")
    public IncidentResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateIncidentRequest request) {
        return incidentService.update(id, request);
    }

    @PostMapping("/{id}/resolve")
    public IncidentResponse resolve(@PathVariable UUID id) {
        return incidentService.resolve(id);
    }
}
