package com.springbootai.incidentiq.controller;

import com.springbootai.incidentiq.dto.CreateIncidentRequest;
import com.springbootai.incidentiq.dto.IncidentResponse;
import com.springbootai.incidentiq.dto.PageResponse;
import com.springbootai.incidentiq.dto.UpdateIncidentRequest;
import com.springbootai.incidentiq.entity.Incident;
import com.springbootai.incidentiq.entity.IncidentSeverity;
import com.springbootai.incidentiq.entity.IncidentStatus;
import com.springbootai.incidentiq.exception.IncidentNotFoundException;
import com.springbootai.incidentiq.exception.InvalidIncidentException;
import com.springbootai.incidentiq.exception.InvalidIncidentStateException;
import com.springbootai.incidentiq.exception.ServiceNotFoundException;
import com.springbootai.incidentiq.service.IncidentService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IncidentController.class)
class IncidentControllerTest {

    private static final UUID ID = UUID.fromString("11111111-2222-4333-8444-555555555555");
    private static final UUID SERVICE_ID = UUID.fromString("7b0c1f2e-3a4b-4c5d-8e9f-0a1b2c3d4e5f");
    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");
    private static final String VALID_CREATE = """
            {"serviceId": "%s", "title": "DB timeouts", "description": "Checkout fails", "severity": "HIGH"}
            """.formatted(SERVICE_ID);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IncidentService incidentService;

    // --- create ---

    @Test
    void createReturns201WithLocationAndBody() throws Exception {
        when(incidentService.create(any())).thenReturn(response(IncidentStatus.OPEN, null));

        mockMvc.perform(post("/api/v1/incidents").contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/incidents/" + ID))
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.serviceId").value(SERVICE_ID.toString()))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.severity").value("HIGH"))
                .andExpect(jsonPath("$.startedAt").value("2026-10-10T12:00:00Z"))
                .andExpect(jsonPath("$.resolvedAt").isEmpty());

        ArgumentCaptor<CreateIncidentRequest> request = ArgumentCaptor.forClass(CreateIncidentRequest.class);
        verify(incidentService).create(request.capture());
        assertThat(request.getValue().serviceId()).isEqualTo(SERVICE_ID);
        assertThat(request.getValue().startedAt()).isNull();
        assertThat(request.getValue().unknownFields()).isEmpty();
    }

    @Test
    void createReturns400WithFieldErrorsForMissingOrBlankFields() throws Exception {
        mockMvc.perform(post("/api/v1/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.errors[?(@.field == 'serviceId')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'title')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'description')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'severity')]").exists());
        verifyNoInteractions(incidentService);
    }

    @Test
    void createReturns400WhenTitleTooLong() throws Exception {
        String body = """
                {"serviceId": "%s", "title": "%s", "description": "d", "severity": "LOW"}
                """.formatted(SERVICE_ID, "t".repeat(Incident.TITLE_MAX_LENGTH + 1));

        mockMvc.perform(post("/api/v1/incidents").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"));
        verifyNoInteractions(incidentService);
    }

    @Test
    void createReturns400WhenDescriptionTooLong() throws Exception {
        String body = """
                {"serviceId": "%s", "title": "t", "description": "%s", "severity": "LOW"}
                """.formatted(SERVICE_ID, "d".repeat(Incident.DESCRIPTION_MAX_LENGTH + 1));

        mockMvc.perform(post("/api/v1/incidents").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("description"));
        verifyNoInteractions(incidentService);
    }

    @Test
    void createCapturesReadOnlyFieldsForTheServiceToReject() throws Exception {
        when(incidentService.create(any())).thenThrow(new InvalidIncidentException(
                "Unknown or read-only fields are not allowed; "
                        + "accepted fields are serviceId, title, description, severity and startedAt"));

        mockMvc.perform(post("/api/v1/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CREATE.replace("}", ", \"status\": \"RESOLVED\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(startsWith(
                        "Unknown or read-only fields are not allowed")));

        ArgumentCaptor<CreateIncidentRequest> request = ArgumentCaptor.forClass(CreateIncidentRequest.class);
        verify(incidentService).create(request.capture());
        assertThat(request.getValue().unknownFields()).containsOnlyKeys("status");
    }

    @Test
    void createReturns400ForUnknownSeverity() throws Exception {
        mockMvc.perform(post("/api/v1/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CREATE.replace("HIGH", "URGENT")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(incidentService);
    }

    @Test
    void createReturns404WhenServiceDoesNotExist() throws Exception {
        when(incidentService.create(any())).thenThrow(new ServiceNotFoundException(SERVICE_ID));

        mockMvc.perform(post("/api/v1/incidents").contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Service " + SERVICE_ID + " was not found"));
    }

    @Test
    void createReturns400WhenDomainRuleIsViolated() throws Exception {
        when(incidentService.create(any()))
                .thenThrow(new InvalidIncidentException("Incident startedAt must not be in the future"));

        mockMvc.perform(post("/api/v1/incidents").contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Incident startedAt must not be in the future"));
    }

    // --- get ---

    @Test
    void getReturns200() throws Exception {
        when(incidentService.get(ID)).thenReturn(response(IncidentStatus.OPEN, null));

        mockMvc.perform(get("/api/v1/incidents/{id}", ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.title").value("DB timeouts"));
    }

    @Test
    void getReturns404WhenMissing() throws Exception {
        when(incidentService.get(ID)).thenThrow(new IncidentNotFoundException(ID));

        mockMvc.perform(get("/api/v1/incidents/{id}", ID))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Incident " + ID + " was not found"));
    }

    // --- list ---

    @Test
    void listUsesDefaultPagination() throws Exception {
        when(incidentService.list(null, null, null, 0, 20))
                .thenReturn(new PageResponse<>(List.of(response(IncidentStatus.OPEN, null)), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/incidents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(ID.toString()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void listPassesFiltersAndPagination() throws Exception {
        when(incidentService.list(any(), any(), any(), eq(2), eq(5)))
                .thenReturn(new PageResponse<>(List.of(), 2, 5, 0, 0));

        mockMvc.perform(get("/api/v1/incidents")
                        .param("serviceId", SERVICE_ID.toString())
                        .param("status", "RESOLVED")
                        .param("severity", "CRITICAL")
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        verify(incidentService).list(SERVICE_ID, IncidentStatus.RESOLVED, IncidentSeverity.CRITICAL, 2, 5);
    }

    @Test
    void listReturns400WhenPageSizeExceedsMaximum() throws Exception {
        mockMvc.perform(get("/api/v1/incidents").param("size", String.valueOf(IncidentService.MAX_PAGE_SIZE + 1)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.errors[0].field").value("size"));
        verifyNoInteractions(incidentService);
    }

    @Test
    void listReturns400WhenPageExceedsMaximum() throws Exception {
        mockMvc.perform(get("/api/v1/incidents").param("page", "2147483647").param("size", "2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("page"));
        verifyNoInteractions(incidentService);
    }

    @Test
    void listReturns400ForNegativePage() throws Exception {
        mockMvc.perform(get("/api/v1/incidents").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("page"));
        verifyNoInteractions(incidentService);
    }

    @Test
    void listReturns400ForUnknownStatusWithoutEchoingInput() throws Exception {
        mockMvc.perform(get("/api/v1/incidents").param("status", "CLOSED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Parameter 'status' has an invalid format"));
        verifyNoInteractions(incidentService);
    }

    // --- update ---

    @Test
    void patchUpdatesEditableFields() throws Exception {
        when(incidentService.update(eq(ID), any())).thenReturn(response(IncidentStatus.OPEN, null));

        mockMvc.perform(patch("/api/v1/incidents/{id}", ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "New title", "severity": "CRITICAL"}
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateIncidentRequest> request = ArgumentCaptor.forClass(UpdateIncidentRequest.class);
        verify(incidentService).update(eq(ID), request.capture());
        assertThat(request.getValue().title()).isEqualTo("New title");
        assertThat(request.getValue().description()).isNull();
        assertThat(request.getValue().severity()).isEqualTo(IncidentSeverity.CRITICAL);
    }

    @Test
    void patchCapturesReadOnlyFieldsAndReturns400WhenServiceRejectsThem() throws Exception {
        String message = "Unknown or read-only fields are not allowed; accepted fields are title, description "
                + "and severity (use POST /api/v1/incidents/{id}/resolve to resolve an incident)";
        when(incidentService.update(eq(ID), any())).thenThrow(new InvalidIncidentException(message));

        mockMvc.perform(patch("/api/v1/incidents/{id}", ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "New title", "status": "RESOLVED", "startedAt": "2026-01-01T00:00:00Z"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(message));

        ArgumentCaptor<UpdateIncidentRequest> request = ArgumentCaptor.forClass(UpdateIncidentRequest.class);
        verify(incidentService).update(eq(ID), request.capture());
        assertThat(request.getValue().unknownFields()).containsOnlyKeys("status", "startedAt");
    }

    @Test
    void patchCapturesNullAndNestedUnknownFieldsWithoutKeepingValues() throws Exception {
        when(incidentService.update(eq(ID), any())).thenThrow(new InvalidIncidentException("rejected"));

        mockMvc.perform(patch("/api/v1/incidents/{id}", ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": null, "meta": {"nested": ["secret-value"]}}
                                """))
                .andExpect(status().isBadRequest());

        ArgumentCaptor<UpdateIncidentRequest> request = ArgumentCaptor.forClass(UpdateIncidentRequest.class);
        verify(incidentService).update(eq(ID), request.capture());
        assertThat(request.getValue().unknownFields()).containsOnlyKeys("status", "meta");
        assertThat(request.getValue().toString()).doesNotContain("secret-value");
    }

    @Test
    void createReturns400ForNoBreakSpaceTitleLikePatchDoes() throws Exception {
        mockMvc.perform(post("/api/v1/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CREATE.replace("DB timeouts", "\\u00a0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"));
        mockMvc.perform(patch("/api/v1/incidents/{id}", ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"\\u00a0\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"));
        verifyNoInteractions(incidentService);
    }

    @Test
    void patchWithEmptyBodyReturns400FromDomainRule() throws Exception {
        when(incidentService.update(eq(ID), any())).thenThrow(new InvalidIncidentException(Incident.EMPTY_UPDATE_MESSAGE));

        mockMvc.perform(patch("/api/v1/incidents/{id}", ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(Incident.EMPTY_UPDATE_MESSAGE));

        ArgumentCaptor<UpdateIncidentRequest> request = ArgumentCaptor.forClass(UpdateIncidentRequest.class);
        verify(incidentService).update(eq(ID), request.capture());
        assertThat(request.getValue().unknownFields()).isEmpty();
    }

    @Test
    void patchRejectsBlankTitle() throws Exception {
        mockMvc.perform(patch("/api/v1/incidents/{id}", ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"));
        verifyNoInteractions(incidentService);
    }

    @Test
    void patchRejectsUnicodeWhitespaceTitleAtTheBoundary() throws Exception {
        mockMvc.perform(patch("/api/v1/incidents/{id}", ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"\\u3000\\u2003\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"));
        verifyNoInteractions(incidentService);
    }

    @Test
    void patchRejectsTooLongTitleAndDescription() throws Exception {
        String body = """
                {"title": "%s", "description": "%s"}
                """.formatted("t".repeat(Incident.TITLE_MAX_LENGTH + 1),
                "d".repeat(Incident.DESCRIPTION_MAX_LENGTH + 1));

        mockMvc.perform(patch("/api/v1/incidents/{id}", ID).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'title')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'description')]").exists());
        verifyNoInteractions(incidentService);
    }

    @Test
    void patchReturns409ForResolvedIncident() throws Exception {
        when(incidentService.update(eq(ID), any()))
                .thenThrow(new InvalidIncidentStateException("Resolved incident " + ID + " cannot be edited"));

        mockMvc.perform(patch("/api/v1/incidents/{id}", ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "New title"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid state transition"));
    }

    @Test
    void patchReturns404WhenMissing() throws Exception {
        when(incidentService.update(eq(ID), any())).thenThrow(new IncidentNotFoundException(ID));

        mockMvc.perform(patch("/api/v1/incidents/{id}", ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "New title"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void concurrentModificationReturns409WithoutInternals() throws Exception {
        when(incidentService.update(eq(ID), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Incident.class, ID));

        mockMvc.perform(patch("/api/v1/incidents/{id}", ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "New title"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Concurrent modification"))
                .andExpect(jsonPath("$.detail")
                        .value("The resource was modified by another request; reload it and try again"));
    }

    // --- resolve ---

    @Test
    void resolveReturns200WithResolvedIncident() throws Exception {
        Instant resolvedAt = NOW.plusSeconds(3600);
        when(incidentService.resolve(ID)).thenReturn(response(IncidentStatus.RESOLVED, resolvedAt));

        mockMvc.perform(post("/api/v1/incidents/{id}/resolve", ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").value("2026-10-10T13:00:00Z"));
    }

    @Test
    void resolveReturns409WhenAlreadyResolved() throws Exception {
        when(incidentService.resolve(ID))
                .thenThrow(new InvalidIncidentStateException("Incident " + ID + " is already resolved"));

        mockMvc.perform(post("/api/v1/incidents/{id}/resolve", ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Incident " + ID + " is already resolved"));
    }

    @Test
    void resolveReturns404WhenMissing() throws Exception {
        when(incidentService.resolve(ID)).thenThrow(new IncidentNotFoundException(ID));

        mockMvc.perform(post("/api/v1/incidents/{id}/resolve", ID))
                .andExpect(status().isNotFound());
    }

    private static IncidentResponse response(IncidentStatus status, Instant resolvedAt) {
        return new IncidentResponse(ID, SERVICE_ID, "DB timeouts", "Checkout fails", IncidentSeverity.HIGH,
                status, NOW, resolvedAt, NOW, resolvedAt != null ? resolvedAt : NOW);
    }
}
