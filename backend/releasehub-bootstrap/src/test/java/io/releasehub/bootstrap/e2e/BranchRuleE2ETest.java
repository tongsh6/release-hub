package io.releasehub.bootstrap.e2e;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BranchRuleE2ETest extends AbstractE2ETest {

    private String token;
    private String ruleId;

    @BeforeAll
    void setUp() throws Exception {
        token = loginAndGetToken();
    }

    @Test
    void listRules() throws Exception {
        mockMvc.perform(get("/api/v1/branch-rules")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void createAndGetById() throws Exception {
        // Create
        String body = """
                {"name":"E2E Rule","pattern":"e2e/**","type":"TEMPLATE","description":"E2E test rule","scopeLevel":"GLOBAL","scopeProjectId":null,"scopeSubProjectId":null}""";
        var result = mockMvc.perform(post("/api/v1/branch-rules")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.name").value("E2E Rule"))
                .andReturn();

        ruleId = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asText();

        // Verify getById works within same test
        mockMvc.perform(get("/api/v1/branch-rules/" + ruleId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("E2E Rule"));
    }

    @Test
    void listPaged() throws Exception {
        mockMvc.perform(get("/api/v1/branch-rules/paged?page=1&size=10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void updateRule() throws Exception {
        String body = """
                {"name":"E2E Rule Updated","pattern":"e2e-updated/**","type":"TEMPLATE","description":"Updated","scopeLevel":"GLOBAL","scopeProjectId":null,"scopeSubProjectId":null}""";
        mockMvc.perform(put("/api/v1/branch-rules/" + ruleId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("E2E Rule Updated"));
    }

    @Test
    void checkCompliance() throws Exception {
        mockMvc.perform(get("/api/v1/branch-rules/check?branchName=feature/test")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.compliant").exists());
    }

    @Test
    void z_deleteRule() throws Exception {
        mockMvc.perform(delete("/api/v1/branch-rules/" + ruleId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
