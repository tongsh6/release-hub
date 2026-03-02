package io.releasehub.bootstrap.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.releasehub.interfaces.auth.AuthController.LoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BranchStatusApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldGetBranchStatusForWindow() throws Exception {
        String token = loginAndGetToken();
        String groupCode = createGroupAndGetCode(token);
        String repoId = createMockRepoAndGetId(token, groupCode);
        String iterationKey = createIterationWithRepo(token, groupCode, repoId);
        String windowId = createWindowAndGetId(token, groupCode);

        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/attach")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("iterationKeys", List.of(iterationKey)))))
                .andExpect(status().isOk());

        MvcResult statusResult = mockMvc.perform(get("/api/v1/release-windows/" + windowId + "/branch-status")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.windowId").value(windowId))
                .andExpect(jsonPath("$.data.repos").isArray())
                .andExpect(jsonPath("$.data.repos.length()").value(1))
                .andReturn();

        JsonNode repoNode = objectMapper.readTree(statusResult.getResponse().getContentAsString())
                .get("data").get("repos").get(0);

        assertThat(repoNode.get("repoId").asText()).isEqualTo(repoId);
        assertThat(repoNode.get("featureBranch").get("exists").asBoolean()).isFalse();
        assertThat(repoNode.get("releaseBranch").get("exists").asBoolean()).isFalse();
        assertThat(repoNode.get("releaseBranch").get("mergeStatus").asText()).isEqualTo("PENDING");
    }

    private String loginAndGetToken() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("admin");
        loginRequest.setPassword("admin");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").exists())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("token").asText();
    }

    private String createGroupAndGetCode(String token) throws Exception {
        String code = "G" + System.currentTimeMillis();
        String req = "{\"name\":\"UT-Group\",\"code\":\"" + code + "\",\"parentCode\":null}";
        mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists());
        return code;
    }

    private String createMockRepoAndGetId(String token, String groupCode) throws Exception {
        String req = "{" +
                "\"name\":\"repo-" + System.currentTimeMillis() + "\"," +
                "\"cloneUrl\":\"https://github.com/acme/releasehub.git\"," +
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
        String req = "{" +
                "\"name\":\"IT-" + System.currentTimeMillis() + "\"," +
                "\"description\":\"desc\"," +
                "\"groupCode\":\"" + groupCode + "\"," +
                "\"repoIds\":[\"" + repoId + "\"]" +
                "}";

        MvcResult result = mockMvc.perform(post("/api/v1/iterations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.key").exists())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("key").asText();
    }

    private String createWindowAndGetId(String token, String groupCode) throws Exception {
        String req = "{" +
                "\"name\":\"RW-" + System.currentTimeMillis() + "\"," +
                "\"groupCode\":\"" + groupCode + "\"" +
                "}";

        MvcResult result = mockMvc.perform(post("/api/v1/release-windows")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asText();
    }
}
