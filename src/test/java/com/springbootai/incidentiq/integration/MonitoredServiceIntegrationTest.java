package com.springbootai.incidentiq.integration;

import com.jayway.jsonpath.JsonPath;
import com.springbootai.incidentiq.TestcontainersConfiguration;
import com.springbootai.incidentiq.dto.CreateServiceRequest;
import com.springbootai.incidentiq.entity.MonitoredService;
import com.springbootai.incidentiq.exception.DuplicateServiceNameException;
import com.springbootai.incidentiq.repository.MonitoredServiceRepository;
import com.springbootai.incidentiq.service.MonitoredServiceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests against a real PostgreSQL (Testcontainers). Each test uses a unique
 * service name, so tests don't depend on each other or on cleanup.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MonitoredServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MonitoredServiceRepository repository;

    @Test
    void createdServiceCanBeRetrieved() throws Exception {
        String name = uniqueName();

        String body = mockMvc.perform(post("/api/v1/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + name + "\", \"description\": \"Handles payments\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        String createdAt = JsonPath.read(body, "$.createdAt");

        mockMvc.perform(get("/api/v1/services/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.description").value("Handles payments"))
                .andExpect(jsonPath("$.createdAt").value(createdAt));
    }

    @Test
    void locationHeaderPointsToCreatedService() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post("/api/v1/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + uniqueName() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse();
        String id = JsonPath.read(response.getContentAsString(), "$.id");

        assertThat(response.getHeader("Location")).isEqualTo("/api/v1/services/" + id);
        mockMvc.perform(get(response.getHeader("Location")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void duplicateNameReturns409() throws Exception {
        String request = "{\"name\": \"" + uniqueName() + "\"}";

        mockMvc.perform(post("/api/v1/services").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/services").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void missingServiceReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/services/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void databaseEnforcesUniqueServiceName() {
        String name = uniqueName();
        repository.saveAndFlush(new MonitoredService(name, null, Instant.now()));

        assertThatThrownBy(() -> repository.saveAndFlush(new MonitoredService(name, null, Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Simulates the race where another request inserts the same name after the existence
     * check: the real repository runs against PostgreSQL, but {@code existsByName} says "no".
     * Verifies that the real constraint violation is recognised and mapped to a duplicate.
     */
    @Test
    void concurrentDuplicateDetectedByDatabaseIsReportedAsDuplicate() {
        String name = uniqueName();
        repository.saveAndFlush(new MonitoredService(name, null, Instant.now()));
        MonitoredServiceRepository racingRepository =
                mock(MonitoredServiceRepository.class, delegatesTo(repository));
        doReturn(false).when(racingRepository).existsByName(name);
        MonitoredServiceService service = new MonitoredServiceService(racingRepository);

        assertThatThrownBy(() -> service.create(new CreateServiceRequest(name, null)))
                .isInstanceOf(DuplicateServiceNameException.class)
                .hasCauseInstanceOf(DataIntegrityViolationException.class);
    }

    private static String uniqueName() {
        return "svc-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
