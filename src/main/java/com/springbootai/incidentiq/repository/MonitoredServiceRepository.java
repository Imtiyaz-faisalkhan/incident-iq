package com.springbootai.incidentiq.repository;

import com.springbootai.incidentiq.entity.MonitoredService;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MonitoredServiceRepository extends JpaRepository<MonitoredService, UUID> {

    boolean existsByName(String name);
}
