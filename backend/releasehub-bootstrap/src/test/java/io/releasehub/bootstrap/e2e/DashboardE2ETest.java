package io.releasehub.bootstrap.e2e;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DashboardE2ETest extends AbstractE2ETest {

    private String token;

    @BeforeAll
    void setUp() throws Exception {
        token = loginAndGetToken();
    }

    @Test
    void statsEndpointReturnsData() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRepositories").exists())
                .andExpect(jsonPath("$.data.totalIterations").exists())
                .andExpect(jsonPath("$.data.activeWindows").exists())
                .andExpect(jsonPath("$.data.totalRuns").exists());
    }
}
