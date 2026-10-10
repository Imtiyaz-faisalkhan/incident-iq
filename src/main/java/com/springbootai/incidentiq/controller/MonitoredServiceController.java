package com.springbootai.incidentiq.controller;

import com.springbootai.incidentiq.dto.CreateServiceRequest;
import com.springbootai.incidentiq.dto.ServiceResponse;
import com.springbootai.incidentiq.service.MonitoredServiceService;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/services")
public class MonitoredServiceController {

    private final MonitoredServiceService monitoredServiceService;

    public MonitoredServiceController(MonitoredServiceService monitoredServiceService) {
        this.monitoredServiceService = monitoredServiceService;
    }

    @PostMapping
    public ResponseEntity<ServiceResponse> create(@Valid @RequestBody CreateServiceRequest request) {
        ServiceResponse created = monitoredServiceService.create(request);
        // Relative URI: avoids building an absolute URL from the client-supplied Host header.
        URI location = URI.create("/api/v1/services/" + created.id());
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    public ServiceResponse get(@PathVariable UUID id) {
        return monitoredServiceService.get(id);
    }
}
