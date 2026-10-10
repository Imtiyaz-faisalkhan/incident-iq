package com.springbootai.incidentiq.integration;

import com.jayway.jsonpath.JsonPath;
import com.springbootai.incidentiq.TestcontainersConfiguration;
import com.springbootai.incidentiq.entity.Incident;
import com.springbootai.incidentiq.entity.IncidentStatus;
import com.springbootai.incidentiq.entity.MonitoredService;
import com.springbootai.incidentiq.repository.IncidentRepository;
import com.springbootai.incidentiq.repository.MonitoredServiceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end incident tests against a real PostgreSQL (Testcontainers). Each test creates its own
 * service and filters by it, so tests don't depend on each other or on cleanup.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class IncidentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MonitoredServiceRepository serviceRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void createdIncidentCanBeRetrieved() throws Exception {
        UUID serviceId = createService();

        String body = mockMvc.perform(post("/api/v1/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(serviceId, "DB timeouts", "HIGH", null)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.resolvedAt").isEmpty())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        String createdAt = JsonPath.read(body, "$.createdAt");

        mockMvc.perform(get("/api/v1/incidents/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.serviceId").value(serviceId.toString()))
                .andExpect(jsonPath("$.title").value("DB timeouts"))
                .andExpect(jsonPath("$.severity").value("HIGH"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.createdAt").value(createdAt))
                .andExpect(jsonPath("$.updatedAt").value(createdAt))
                .andExpect(jsonPath("$.startedAt").value(createdAt));
    }

    @Test
    void createWithUnknownServiceReturns404AndPersistsNothing() throws Exception {
        UUID unknownServiceId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(unknownServiceId, "t", "LOW", null)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Service " + unknownServiceId + " was not found"));
        assertThat(incidentCount(unknownServiceId)).isZero();
    }

    @Test
    void createWithReadOnlyFieldReturns400AndPersistsNothing() throws Exception {
        UUID serviceId = createService();

        mockMvc.perform(post("/api/v1/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(serviceId, "t", "LOW", null).replace("}", ", \"status\": \"RESOLVED\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(startsWith(
                        "Unknown or read-only fields are not allowed")));
        assertThat(incidentCount(serviceId)).isZero();
    }

    @Test
    void nulCharacterReturns400InsteadOf500OnCreateAndPatch() throws Exception {
        UUID serviceId = createService();

        mockMvc.perform(post("/api/v1/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(serviceId, "db\\u0000down", "LOW", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Incident title must not contain NUL characters"));
        assertThat(incidentCount(serviceId)).isZero();

        String id = createIncident(serviceId, "DB timeouts", "LOW", null);
        mockMvc.perform(patch("/api/v1/incidents/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\": \"bad\\u0000value\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Incident description must not contain NUL characters"));
    }

    @Test
    void noBreakSpaceOnlyTitleIsRejectedOnCreateLikeOnPatch() throws Exception {
        UUID serviceId = createService();

        mockMvc.perform(post("/api/v1/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(serviceId, "\\u00a0", "LOW", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"));
        assertThat(incidentCount(serviceId)).isZero();
    }

    @Test
    void invalidPatchBodyReturns400EvenWhenIncidentDoesNotExist() throws Exception {
        UUID missingId = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/incidents/{id}", missingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(Incident.EMPTY_UPDATE_MESSAGE));
        mockMvc.perform(patch("/api/v1/incidents/{id}", missingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titel\": \"typo\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(startsWith("Unknown or read-only fields are not allowed")));
    }

    @Test
    void explicitStartedAtIsStoredTruncatedToMicroseconds() throws Exception {
        UUID serviceId = createService();
        Instant startedAt = Instant.now().minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS).plusNanos(123_456_789);

        String id = createIncident(serviceId, "t", "LOW", startedAt);

        mockMvc.perform(get("/api/v1/incidents/{id}", id))
                .andExpect(jsonPath("$.startedAt").value(startedAt.truncatedTo(ChronoUnit.MICROS).toString()));
    }

    @Test
    void startedAtSlightlyInTheFutureIsClampedToCreationTime() throws Exception {
        UUID serviceId = createService();

        String id = createIncident(serviceId, "t", "LOW", Instant.now().plusSeconds(30));

        String body = mockMvc.perform(get("/api/v1/incidents/{id}", id))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(body, "$.startedAt")).isEqualTo(JsonPath.read(body, "$.createdAt"));
    }

    @Test
    void startedAtBeforeMinimumReturns400() throws Exception {
        UUID serviceId = createService();

        mockMvc.perform(post("/api/v1/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(serviceId, "t", "LOW", Instant.parse("1999-12-31T23:59:59Z"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Incident startedAt must not be before " + Incident.MIN_STARTED_AT));
        assertThat(incidentCount(serviceId)).isZero();
    }

    @Test
    void createWithInvalidFieldsReturns400() throws Exception {
        UUID serviceId = createService();

        mockMvc.perform(post("/api/v1/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(serviceId, "   ", "HIGH", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"));
    }

    @Test
    void createWithFutureStartedAtReturns400() throws Exception {
        UUID serviceId = createService();
        Instant future = Instant.now().plus(1, ChronoUnit.HOURS);

        mockMvc.perform(post("/api/v1/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(serviceId, "t", "HIGH", future)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Incident startedAt must not be in the future"));
    }

    @Test
    void missingIncidentReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/incidents/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void listFiltersByServiceStatusAndSeverityAndPaginatesNewestFirst() throws Exception {
        UUID serviceId = createService();
        UUID otherServiceId = createService();
        Instant base = Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        String oldest = createIncident(serviceId, "oldest", "LOW", base);
        String middle = createIncident(serviceId, "middle", "CRITICAL", base.plusSeconds(60));
        String newest = createIncident(serviceId, "newest", "HIGH", base.plusSeconds(120));
        createIncident(otherServiceId, "other service", "CRITICAL", base.plusSeconds(180));
        mockMvc.perform(post("/api/v1/incidents/{id}/resolve", middle)).andExpect(status().isOk());

        // Filter by service, newest first, first page of two.
        mockMvc.perform(get("/api/v1/incidents").param("serviceId", serviceId.toString()).param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", contains(newest, middle)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        // Second page.
        mockMvc.perform(get("/api/v1/incidents")
                        .param("serviceId", serviceId.toString()).param("size", "2").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", contains(oldest)));

        // Status filter.
        mockMvc.perform(get("/api/v1/incidents")
                        .param("serviceId", serviceId.toString()).param("status", "RESOLVED"))
                .andExpect(jsonPath("$.content[*].id", contains(middle)));
        mockMvc.perform(get("/api/v1/incidents")
                        .param("serviceId", serviceId.toString()).param("status", "OPEN"))
                .andExpect(jsonPath("$.content[*].id", contains(newest, oldest)));

        // Severity filter, combined with service (excludes the other service's CRITICAL incident).
        mockMvc.perform(get("/api/v1/incidents")
                        .param("serviceId", serviceId.toString()).param("severity", "CRITICAL"))
                .andExpect(jsonPath("$.content[*].id", contains(middle)));

        // All three filters combined with no match.
        mockMvc.perform(get("/api/v1/incidents")
                        .param("serviceId", serviceId.toString())
                        .param("severity", "CRITICAL")
                        .param("status", "OPEN"))
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void listRejectsPageSizeAboveMaximum() throws Exception {
        mockMvc.perform(get("/api/v1/incidents").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("size"));
    }

    @Test
    void listRejectsHugePageNumberInsteadOfFailingWith500() throws Exception {
        mockMvc.perform(get("/api/v1/incidents").param("page", "2147483647").param("size", "2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("page"));
    }

    @Test
    void listPageBeyondLastPageIsEmptyWithCorrectTotals() throws Exception {
        UUID serviceId = createService();
        createIncident(serviceId, "only", "LOW", null);

        mockMvc.perform(get("/api/v1/incidents")
                        .param("serviceId", serviceId.toString()).param("page", "5").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page").value(5))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void patchUpdatesEditableFieldsAndPersists() throws Exception {
        UUID serviceId = createService();
        String id = createIncident(serviceId, "DB timeouts", "LOW", null);

        mockMvc.perform(patch("/api/v1/incidents/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Connection pool exhausted", "description": "Root cause narrowed", "severity": "CRITICAL"}
                                """))
                .andExpect(status().isOk());

        String body = mockMvc.perform(get("/api/v1/incidents/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Connection pool exhausted"))
                .andExpect(jsonPath("$.description").value("Root cause narrowed"))
                .andExpect(jsonPath("$.severity").value("CRITICAL"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn().getResponse().getContentAsString();
        Instant createdAt = Instant.parse(JsonPath.read(body, "$.createdAt"));
        Instant updatedAt = Instant.parse(JsonPath.read(body, "$.updatedAt"));
        // Exact timestamps are asserted in IncidentServiceTest with a fixed Clock.
        assertThat(updatedAt).isAfterOrEqualTo(createdAt);
    }

    @Test
    void patchWithUnchangedValuesDoesNotBumpUpdatedAt() throws Exception {
        UUID serviceId = createService();
        String id = createIncident(serviceId, "DB timeouts", "LOW", null);
        String before = mockMvc.perform(get("/api/v1/incidents/{id}", id))
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(patch("/api/v1/incidents/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "DB timeouts", "severity": "LOW"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedAt").value(JsonPath.<String>read(before, "$.updatedAt")));
    }

    @Test
    void staleConcurrentUpdateIsRejectedByOptimisticLocking() throws Exception {
        UUID serviceId = createService();
        UUID id = UUID.fromString(createIncident(serviceId, "DB timeouts", "LOW", null));
        Incident staleCopy = incidentRepository.findById(id).orElseThrow();

        // Another request changes the incident after the stale copy was read.
        mockMvc.perform(post("/api/v1/incidents/{id}/resolve", id)).andExpect(status().isOk());
        staleCopy.update("Overwrite attempt", null, null, Instant.now());

        assertThatThrownBy(() -> incidentRepository.saveAndFlush(staleCopy))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        mockMvc.perform(get("/api/v1/incidents/{id}", id))
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.title").value("DB timeouts"));
    }

    @Test
    void patchCannotChangeStatus() throws Exception {
        UUID serviceId = createService();
        String id = createIncident(serviceId, "DB timeouts", "LOW", null);

        mockMvc.perform(patch("/api/v1/incidents/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "RESOLVED"}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/incidents/{id}", id))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.resolvedAt").isEmpty());
    }

    @Test
    void resolveSetsResolvedAtAndRejectsRepeatedResolutionAndEdits() throws Exception {
        UUID serviceId = createService();
        String id = createIncident(serviceId, "DB timeouts", "HIGH", null);

        String body = mockMvc.perform(post("/api/v1/incidents/{id}/resolve", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andReturn().getResponse().getContentAsString();
        String resolvedAt = JsonPath.read(body, "$.resolvedAt");
        assertThat(resolvedAt).isNotNull();

        mockMvc.perform(post("/api/v1/incidents/{id}/resolve", id))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid state transition"));
        mockMvc.perform(patch("/api/v1/incidents/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Too late"}
                                """))
                .andExpect(status().isConflict());

        // The failed attempts changed nothing.
        mockMvc.perform(get("/api/v1/incidents/{id}", id))
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").value(resolvedAt))
                .andExpect(jsonPath("$.updatedAt").value(resolvedAt))
                .andExpect(jsonPath("$.title").value("DB timeouts"));
    }

    @Test
    void investigatingIncidentCanBeResolved() throws Exception {
        UUID serviceId = createService();
        String id = createIncident(serviceId, "DB timeouts", "HIGH", null);
        // No API sets INVESTIGATING yet (Phase 4), so set it directly in the database.
        jdbcTemplate.update("update incident set status = 'INVESTIGATING' where id = ?", UUID.fromString(id));

        mockMvc.perform(post("/api/v1/incidents/{id}/resolve", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty());
    }

    @Test
    void resolveMissingIncidentReturns404() throws Exception {
        mockMvc.perform(post("/api/v1/incidents/{id}/resolve", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void incidentIsPersistedWithServiceRelationshipAndEnumNames() throws Exception {
        UUID serviceId = createService();
        UUID id = UUID.fromString(createIncident(serviceId, "DB timeouts", "CRITICAL", null));

        transactionTemplate.executeWithoutResult(tx -> {
            Incident incident = incidentRepository.findById(id).orElseThrow();
            assertThat(incident.getService().getId()).isEqualTo(serviceId);
            assertThat(incident.getService().getName()).startsWith("svc-");
            assertThat(incident.getStatus()).isEqualTo(IncidentStatus.OPEN);
        });

        // Enums are stored by name, not ordinal; the foreign key column holds the service id.
        List<String> row = jdbcTemplate.queryForObject(
                "select status, severity, service_id::text from incident where id = ?",
                (rs, n) -> List.of(rs.getString(1), rs.getString(2), rs.getString(3)), id);
        assertThat(row).containsExactly("OPEN", "CRITICAL", serviceId.toString());
    }

    @Test
    void databaseRejectsIncidentForNonexistentService() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into incident (id, service_id, title, description, severity, status,
                                      started_at, created_at, updated_at, version)
                values (gen_random_uuid(), ?, 't', 'd', 'LOW', 'OPEN', now(), now(), now(), 0)
                """, UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** Counts incidents for one service only, so the check is unaffected by other tests' data. */
    private int incidentCount(UUID serviceId) {
        return jdbcTemplate.queryForObject("select count(*) from incident where service_id = ?", Integer.class, serviceId);
    }

    private UUID createService() {
        String name = "svc-" + UUID.randomUUID().toString().substring(0, 8);
        return serviceRepository.saveAndFlush(new MonitoredService(name, null, Instant.now())).getId();
    }

    private String createIncident(UUID serviceId, String title, String severity, Instant startedAt) throws Exception {
        String body = mockMvc.perform(post("/api/v1/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(serviceId, title, severity, startedAt)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private static String createBody(UUID serviceId, String title, String severity, Instant startedAt) {
        String startedAtField = startedAt != null ? ", \"startedAt\": \"" + startedAt + "\"" : "";
        return """
                {"serviceId": "%s", "title": "%s", "description": "Checkout requests fail", "severity": "%s"%s}
                """.formatted(serviceId, title, severity, startedAtField);
    }
}
