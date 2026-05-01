package io.releasehub.bootstrap.e2e;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SettingsE2eTest extends AbstractE2ETest {

    private String token;

    @BeforeAll
    void setUp() throws Exception {
        token = loginAndGetToken();
    }

    @Test
    void saveAndGetGitlab() throws Exception {
        String body = """
                {"baseUrl":"https://gitlab.example.com","token":"glpat-xxx"}""";
        mockMvc.perform(post("/api/v1/settings/gitlab")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/settings/gitlab")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void saveAndGetNaming() throws Exception {
        String body = """
                {"featureTemplate":"feature/{iterationKey}","releaseTemplate":"release/{windowKey}"}""";
        mockMvc.perform(post("/api/v1/settings/naming")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/settings/naming")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void getRef() throws Exception {
        mockMvc.perform(get("/api/v1/settings/ref")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void saveAndGetBlocking() throws Exception {
        String body = "{\"defaultPolicy\":\"WARN\"}";
        mockMvc.perform(post("/api/v1/settings/blocking")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/settings/blocking")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
