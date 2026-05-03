package io.releasehub.bootstrap.e2e;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReleaseWindowE2ETest extends AbstractE2ETest {

    private String token;
    private String groupCode;
    private String iterKey;
    private String windowId;

    @BeforeAll
    void setUp() throws Exception {
        token = loginAndGetToken();
        groupCode = createGroup(token);
        iterKey = createIteration(token, groupCode);

        String name = "e2e-rw-" + System.currentTimeMillis();
        windowId = createReleaseWindow(token, groupCode);
    }

    @Test
    void getById() throws Exception {
        mockMvc.perform(get("/api/v1/release-windows/" + windowId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(windowId))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void listAll() throws Exception {
        mockMvc.perform(get("/api/v1/release-windows")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void listPaged() throws Exception {
        mockMvc.perform(get("/api/v1/release-windows/paged?page=1&size=10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void pagedFilterByStatus() throws Exception {
        mockMvc.perform(get("/api/v1/release-windows/paged?page=1&size=10&status=DRAFT")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void freeze() throws Exception {
        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/freeze")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.frozen").value(true));
    }

    @Test
    void unfreeze() throws Exception {
        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/unfreeze")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.frozen").value(false));
    }

    @Test
    void attachAndPublish() throws Exception {
        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/attach")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("iterationKeys", List.of(iterKey)))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/publish")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
    }

    @Test
    void close() throws Exception {
        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/close")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));
    }

    @Test
    void publishFailsWithoutIteration() throws Exception {
        // B2 regression: publish without attached iteration → should fail
        String freshWinId = createReleaseWindow(token, groupCode);
        mockMvc.perform(post("/api/v1/release-windows/" + freshWinId + "/publish")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void freezeClosedWindowFails() throws Exception {
        // B4 regression: freeze on CLOSED window → RW_009
        String freshWinId = createReleaseWindow(token, groupCode);
        String freshIterKey = createIteration(token, groupCode);

        mockMvc.perform(post("/api/v1/release-windows/" + freshWinId + "/attach")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("iterationKeys", List.of(freshIterKey)))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/release-windows/" + freshWinId + "/publish")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/release-windows/" + freshWinId + "/close")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Now freeze the CLOSED window → should fail
        mockMvc.perform(post("/api/v1/release-windows/" + freshWinId + "/freeze")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is4xxClientError());
    }
}
