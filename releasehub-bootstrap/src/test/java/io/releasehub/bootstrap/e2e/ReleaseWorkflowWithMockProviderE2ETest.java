package io.releasehub.bootstrap.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReleaseWorkflowWithMockProviderE2ETest extends AbstractE2ETest {

    @Test
    void shouldPublishWindowWithMockProviderAndExposeBranchStatus() throws Exception {
        String token = loginAndGetToken();
        String groupCode = createGroup(token);
        String repoId = createMockRepo(token, groupCode);
        String iterationKey = createIterationWithRepo(token, groupCode, repoId);
        String windowId = createReleaseWindow(token, groupCode);

        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/attach")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("iterationKeys", List.of(iterationKey)))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/freeze")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.frozen").value(true));

        MvcResult publishResult = mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/publish")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andReturn();

        JsonNode published = objectMapper.readTree(publishResult.getResponse().getContentAsString()).get("data");
        assertThat(published.get("publishedAt").asText()).isNotBlank();

        mockMvc.perform(get("/api/v1/release-windows/" + windowId + "/branch-status")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.windowId").value(windowId))
                .andExpect(jsonPath("$.data.repos").isArray())
                .andExpect(jsonPath("$.data.repos.length()").value(1))
                .andExpect(jsonPath("$.data.repos[0].repoId").value(repoId))
                .andExpect(jsonPath("$.data.repos[0].releaseBranch.mergeStatus").value("PENDING"));
    }

    private String createMockRepo(String token, String groupCode) throws Exception {
        String name = "TC-MockRepo-" + System.currentTimeMillis();
        String req = "{" +
                "\"name\":\"" + name + "\"," +
                "\"cloneUrl\":\"https://github.com/acme/" + name + ".git\"," +
                "\"groupCode\":\"" + groupCode + "\"," +
                "\"defaultBranch\":\"main\"," +
                "\"gitProvider\":\"MOCK\"," +
                "\"gitToken\":\"mock-token\"" +
                "}";

        MvcResult result = mockMvc.perform(post("/api/v1/repositories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asText();
    }

    private String createIterationWithRepo(String token, String groupCode, String repoId) throws Exception {
        String name = "TC-Iter-" + System.currentTimeMillis();
        String req = String.format(
                "{\"name\":\"%s\",\"description\":\"E2E iter\",\"groupCode\":\"%s\",\"repoIds\":[\"%s\"]}",
                name, groupCode, repoId
        );
        MvcResult result = mockMvc.perform(post("/api/v1/iterations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.key").exists())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("key").asText();
    }
}
