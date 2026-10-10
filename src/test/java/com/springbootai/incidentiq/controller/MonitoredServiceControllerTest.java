package com.springbootai.incidentiq.controller;

import com.springbootai.incidentiq.dto.ServiceResponse;
import com.springbootai.incidentiq.entity.MonitoredService;
import com.springbootai.incidentiq.exception.DuplicateServiceNameException;
import com.springbootai.incidentiq.exception.InvalidServiceException;
import com.springbootai.incidentiq.exception.ServiceNotFoundException;
import com.springbootai.incidentiq.service.MonitoredServiceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MonitoredServiceController.class)
class MonitoredServiceControllerTest {

    private static final UUID ID = UUID.fromString("7b0c1f2e-3a4b-4c5d-8e9f-0a1b2c3d4e5f");
    private static final Instant CREATED_AT = Instant.parse("2026-10-10T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MonitoredServiceService monitoredServiceService;

    @Test
    void createReturns201WithLocationAndBody() throws Exception {
        when(monitoredServiceService.create(any()))
                .thenReturn(new ServiceResponse(ID, "payment-service", "Handles payments", CREATED_AT));

        mockMvc.perform(post("/api/v1/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "payment-service", "description": "Handles payments"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/services/" + ID))
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.name").value("payment-service"))
                .andExpect(jsonPath("$.description").value("Handles payments"))
                .andExpect(jsonPath("$.createdAt").value("2026-10-10T12:00:00Z"));
    }

    @Test
    void createReturns400WhenNameMissing() throws Exception {
        mockMvc.perform(post("/api/v1/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description": "no name"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
        verifyNoInteractions(monitoredServiceService);
    }

    @Test
    void createReturns201WhenFieldsAreAtMaximumLength() throws Exception {
        String name = "a".repeat(MonitoredService.NAME_MAX_LENGTH);
        String description = "d".repeat(MonitoredService.DESCRIPTION_MAX_LENGTH);
        when(monitoredServiceService.create(any()))
                .thenReturn(new ServiceResponse(ID, name, description, CREATED_AT));

        mockMvc.perform(post("/api/v1/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + name + "\", \"description\": \"" + description + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void createReturns400WhenNameIsWhitespace() throws Exception {
        mockMvc.perform(post("/api/v1/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[*].field", everyItem(equalTo("name"))));
        verifyNoInteractions(monitoredServiceService);
    }

    @Test
    void createReturns400WhenDomainRuleIsViolated() throws Exception {
        when(monitoredServiceService.create(any()))
                .thenThrow(new InvalidServiceException("Service name is required"));

        mockMvc.perform(post("/api/v1/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "payment-service"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.detail").value("Service name is required"));
    }

    @Test
    void createReturns415ForUnsupportedContentType() throws Exception {
        mockMvc.perform(post("/api/v1/services")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("payment-service"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(monitoredServiceService);
    }

    @Test
    void unsupportedMethodReturns405() throws Exception {
        mockMvc.perform(delete("/api/v1/services/{id}", ID))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(monitoredServiceService);
    }

    @Test
    void unknownPathReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void createReturns400WhenNameHasInvalidFormat() throws Exception {
        mockMvc.perform(post("/api/v1/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Payment Service"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
        verifyNoInteractions(monitoredServiceService);
    }

    @Test
    void createReturns400WhenDescriptionTooLong() throws Exception {
        String description = "a".repeat(MonitoredService.DESCRIPTION_MAX_LENGTH + 1);

        mockMvc.perform(post("/api/v1/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"payment-service\", \"description\": \"" + description + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("description"));
        verifyNoInteractions(monitoredServiceService);
    }

    @Test
    void createReturns400ForMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": "))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(monitoredServiceService);
    }

    @Test
    void createReturns409WhenNameAlreadyExists() throws Exception {
        when(monitoredServiceService.create(any()))
                .thenThrow(new DuplicateServiceNameException("payment-service"));

        mockMvc.perform(post("/api/v1/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "payment-service"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Duplicate resource"))
                .andExpect(jsonPath("$.detail").value("A service named 'payment-service' already exists"));
    }

    @Test
    void getReturns200WithService() throws Exception {
        when(monitoredServiceService.get(ID))
                .thenReturn(new ServiceResponse(ID, "payment-service", null, CREATED_AT));

        mockMvc.perform(get("/api/v1/services/{id}", ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.name").value("payment-service"));
    }

    @Test
    void getReturns404WhenServiceMissing() throws Exception {
        when(monitoredServiceService.get(ID)).thenThrow(new ServiceNotFoundException(ID));

        mockMvc.perform(get("/api/v1/services/{id}", ID))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Resource not found"))
                .andExpect(jsonPath("$.instance").value("/api/v1/services/" + ID));
    }

    @Test
    void getReturns400ForNonUuidId() throws Exception {
        mockMvc.perform(get("/api/v1/services/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.detail").value("Parameter 'id' has an invalid format"));
        verifyNoInteractions(monitoredServiceService);
    }

    @Test
    void unexpectedErrorReturns500WithoutInternalDetails() throws Exception {
        when(monitoredServiceService.get(ID)).thenThrow(new IllegalStateException("db password=secret"));

        mockMvc.perform(get("/api/v1/services/{id}", ID))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
    }
}
