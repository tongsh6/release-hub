package io.releasehub.bootstrap.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IterationRepoApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String loginAndGetToken() throws Exception {
        String body = "{\"username\":\"admin\",\"password\":\"admin\"}";
        var result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.token").exists())
            .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("data").get("token").asText();
    }

    @Test
    void shouldAddRemoveAndListReposForIteration() throws Exception {
        String token = loginAndGetToken();
        String groupCode = createGroupAndGetCode(token);
        var createResult = mockMvc.perform(post("/api/v1/iterations")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"d\",\"groupCode\":\"" + groupCode + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.key").exists())
            .andReturn();
        String iterationKey = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("key").asText();
        String repo1 = createRepo(token, groupCode, "repo-add-1");
        String repo2 = createRepo(token, groupCode, "repo-add-2");

        mockMvc.perform(post("/api/v1/iterations/" + iterationKey + "/repos/add")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("repoIds", java.util.List.of(repo1, repo2)))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.repoIds.length()").value(2));

        mockMvc.perform(get("/api/v1/iterations/" + iterationKey + "/repos")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(2));

        mockMvc.perform(post("/api/v1/iterations/" + iterationKey + "/repos/remove")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("repoIds", java.util.List.of(repo1)))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.repoIds.length()").value(1));

        mockMvc.perform(get("/api/v1/iterations/" + iterationKey + "/repos")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void shouldPageRepoDetailsWithVersionMetadataForLargeIteration() throws Exception {
        String token = loginAndGetToken();
        String groupCode = createGroupAndGetCode(token);
        var createResult = mockMvc.perform(post("/api/v1/iterations")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"IT-Large-Repo\",\"description\":\"d\",\"groupCode\":\"" + groupCode + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.key").exists())
            .andReturn();
        String iterationKey = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("key").asText();
        java.util.List<String> repoIds = new java.util.ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            repoIds.add(createRepo(token, groupCode, String.format("large-%02d", i)));
        }

        mockMvc.perform(post("/api/v1/iterations/" + iterationKey + "/repos/add")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("repoIds", repoIds))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.repoIds.length()").value(12));

        mockMvc.perform(get("/api/v1/iterations/" + iterationKey + "/repos/paged?page=2&size=5")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.length()").value(5))
            .andExpect(jsonPath("$.page.total").value(12))
            .andExpect(jsonPath("$.page.page").value(2))
            .andExpect(jsonPath("$.page.size").value(5))
            .andExpect(jsonPath("$.data[0].repoId").exists())
            .andExpect(jsonPath("$.data[0].repoName").exists())
            .andExpect(jsonPath("$.data[0].branchCreationMode").value("AUTO"))
            .andExpect(jsonPath("$.data[0].featureBranch").value("feature/" + iterationKey))
            .andExpect(jsonPath("$.data[0].baseVersion").exists())
            .andExpect(jsonPath("$.data[0].devVersion").exists())
            .andExpect(jsonPath("$.data[0].targetVersion").exists())
            .andExpect(jsonPath("$.data[0].versionSource").value("SYSTEM"));
    }

    private String createGroupAndGetCode(String token) throws Exception {
        String code = "G" + System.nanoTime();
        String req = "{\"name\":\"UT-Group\",\"code\":\"" + code + "\",\"parentCode\":null}";
        mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists());
        return code;
    }

    private String createRepo(String token, String groupCode, String suffix) throws Exception {
        String name = "UT-" + suffix + "-" + System.nanoTime();
        String cloneUrl = "https://git.example.com/" + name + ".git";
        String req = "{" +
                "\"name\":\"" + name + "\"," +
                "\"cloneUrl\":\"" + cloneUrl + "\"," +
                "\"groupCode\":\"" + groupCode + "\"," +
                "\"defaultBranch\":\"main\"," +
                "\"gitProvider\":\"GITLAB\"," +
                "\"gitAccessToken\":\"test-token\"" +
                "}";
        var result = mockMvc.perform(post("/api/v1/repositories")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(req))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").exists())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data")
                .get("id")
                .asText();
    }
}
