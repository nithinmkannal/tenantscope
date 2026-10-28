package com.tenantscope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {"tenantscope.seed.enabled=true", "tenantscope.security.api-keys=test-key"})
@AutoConfigureMockMvc
@Testcontainers
class TenantScopeIntegrationTest {

    @Container
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", pg::getJdbcUrl);
        r.add("spring.datasource.username", pg::getUsername);
        r.add("spring.datasource.password", pg::getPassword);
    }

    @Autowired
    MockMvc mvc;

    private static final String KEY = "X-API-Key";

    @Test
    void apiRequiresKeyButHealthDoesNot() throws Exception {
        mvc.perform(get("/api/v1/dashboard/summary")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/dashboard/summary").header(KEY, "wrong")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void tenantDiagnosticsMasksSecretsAndFlagsProblems() throws Exception {
        mvc.perform(get("/api/v1/diagnostics/tenants/CONTOSO").header(KEY, "test-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findings[*].code", hasItems("CONFIG_MISSING", "CONFIG_EMPTY", "HIGH_FAILURE_RATE")))
                .andExpect(jsonPath("$.configuration.API_GATEWAY[?(@.key=='client.secret')].value", contains("********")))
                .andExpect(jsonPath("$.recentFailures", not(empty())));
        mvc.perform(get("/api/v1/diagnostics/tenants/LEGACYCO").header(KEY, "test-key"))
                .andExpect(jsonPath("$.findings[*].code", hasItem("TENANT_INACTIVE")));
        mvc.perform(get("/api/v1/diagnostics/tenants/NOPE").header(KEY, "test-key"))
                .andExpect(status().isNotFound());
    }

    @Test
    void incidentLifecycleWithDiagnosticsNote() throws Exception {
        String body = """
                {"tenantCode":"NORTHWIND","service":"EDI_PROCESSING","category":"PROCESSING_FAILURE",
                 "priority":"P3","title":"Orders stuck","description":"d"}""";
        String res = mvc.perform(post("/api/v1/incidents").header(KEY, "test-key")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                // Platinum tenant: P3 is raised to P2, routed to the EDI team
                .andExpect(jsonPath("$.priority").value("P2"))
                .andExpect(jsonPath("$.assignmentGroup").value("edi-ops-team"))
                .andReturn().getResponse().getContentAsString();
        String number = com.jayway.jsonpath.JsonPath.read(res, "$.number");
        assertThat(number).startsWith("TS");

        mvc.perform(post("/api/v1/diagnostics/incidents/" + number).header(KEY, "test-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantCode").value("NORTHWIND"));

        mvc.perform(post("/api/v1/incidents/" + number + "/transition").header(KEY, "test-key")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"state\":\"CLOSED\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/incidents/" + number + "/transition").header(KEY, "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"state\":\"ACKNOWLEDGED\",\"actor\":\"alice\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACKNOWLEDGED"));

        mvc.perform(get("/api/v1/incidents/" + number).header(KEY, "test-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes[*].kind", hasItems("WORK_NOTE", "DIAGNOSTIC", "STATE_CHANGE")));
    }

    @Test
    void validationAndFilters() throws Exception {
        mvc.perform(post("/api/v1/incidents").header(KEY, "test-key")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        mvc.perform(get("/api/v1/incidents?state=BOGUS").header(KEY, "test-key"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/incidents?tenant=CONTOSO&state=IN_PROGRESS").header(KEY, "test-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].tenantCode", everyItem(is("CONTOSO"))))
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void dashboardAggregatesSeededData() throws Exception {
        mvc.perform(get("/api/v1/dashboard/summary?days=7").header(KEY, "test-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openIncidents", greaterThanOrEqualTo(5)))
                .andExpect(jsonPath("$.urgentOpenIncidents", greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.trend", hasSize(7)))
                .andExpect(jsonPath("$.openByService.EDI_PROCESSING", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.meanTimeToResolveHours", notNullValue()))
                .andExpect(jsonPath("$.recentCritical[0].priority").value("P1"));
    }
}
