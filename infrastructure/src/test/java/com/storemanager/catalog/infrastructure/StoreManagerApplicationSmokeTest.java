package com.storemanager.catalog.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

/**
 * Runtime-harness evidence for tasks.md Phase 1 (PR1): the Spring context
 * boots against the H2 test datasource, Flyway applies V1__init_catalog.sql
 * cleanly, and the actuator health endpoint responds. This is the smallest
 * proof that the multi-module skeleton actually runs end-to-end.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class StoreManagerApplicationSmokeTest {

    @Test
    void healthEndpointReturnsOk(@org.springframework.beans.factory.annotation.Autowired TestRestTemplate restTemplate) {
        var response = restTemplate.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
