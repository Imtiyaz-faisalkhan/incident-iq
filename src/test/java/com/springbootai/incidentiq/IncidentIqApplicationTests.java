package com.springbootai.incidentiq;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

// Same configuration as the integration tests, so the Spring context and PostgreSQL container are shared.
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class IncidentIqApplicationTests {

    @Test
    void contextLoads() {
    }

}
