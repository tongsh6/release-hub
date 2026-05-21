package io.releasehub.bootstrap.e2e;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RepositoryE2ETest extends AbstractE2ETest {

    private static String token;
    private static String groupCode;
    private static String repoId;
    private static String repoName;
    private static String filterParentCode;
    private static String filterLeafCode;
    private static String filterRepoName;

    @Test
    @Order(1)
    void createRepository() throws Exception {
        token = loginAndGetToken();
        groupCode = createGroup(token);
        repoName = "e2e-repo-" + System.currentTimeMillis();

        mockMvc.perform(post("/api/v1/repositories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"name\":\"%s\",\"cloneUrl\":\"https://git.example.com/%s.git\",\"groupCode\":\"%s\",\"defaultBranch\":\"main\",\"initialVersion\":\"1.2.3\"}",
                                repoName, repoName, groupCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.name").value(repoName))
                .andExpect(jsonPath("$.data.groupCode").value(groupCode))
                .andDo(result -> {
                    repoId = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asText();
                });
    }

    @Test
    @Order(2)
    void getById() throws Exception {
        mockMvc.perform(get("/api/v1/repositories/" + repoId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(repoName));
    }

    @Test
    @Order(3)
    void listAll() throws Exception {
        mockMvc.perform(get("/api/v1/repositories")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").isNumber());
    }

    @Test
    @Order(4)
    void listPaged() throws Exception {
        mockMvc.perform(get("/api/v1/repositories/paged?page=1&size=10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.page.page").value(1));
    }

    @Test
    @Order(5)
    void searchByKeyword() throws Exception {
        mockMvc.perform(get("/api/v1/repositories?keyword=" + repoName)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    @Order(6)
    void createDuplicateRepositoryFails() throws Exception {
        mockMvc.perform(post("/api/v1/repositories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"name\":\"%s-duplicate\",\"cloneUrl\":\"git@git.example.com:%s.git\",\"groupCode\":\"%s\",\"defaultBranch\":\"main\"}",
                                repoName, repoName, groupCode)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPO_012"));
    }

    @Test
    @Order(7)
    void updateRepository() throws Exception {
        String updatedName = repoName + "-updated";
        mockMvc.perform(put("/api/v1/repositories/" + repoId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"name\":\"%s\",\"cloneUrl\":\"https://git.example.com/%s.git\",\"groupCode\":\"%s\"}",
                                updatedName, updatedName, groupCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(updatedName));
    }

    @Test
    @Order(8)
    void listPagedByGroupIncludesChildGroups() throws Exception {
        filterParentCode = "e2e-repo-filter-p-" + System.currentTimeMillis();
        mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"name\":\"FilterParent\",\"code\":\"%s\",\"parentCode\":null}", filterParentCode)))
                .andExpect(status().isOk());

        filterLeafCode = "e2e-repo-filter-c-" + System.currentTimeMillis();
        mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"name\":\"FilterLeaf\",\"code\":\"%s\",\"parentCode\":\"%s\"}", filterLeafCode, filterParentCode)))
                .andExpect(status().isOk());

        filterRepoName = "e2e-filter-repo-" + System.currentTimeMillis();
        mockMvc.perform(post("/api/v1/repositories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"name\":\"%s\",\"cloneUrl\":\"https://git.example.com/%s.git\",\"groupCode\":\"%s\",\"defaultBranch\":\"main\"}",
                                filterRepoName, filterRepoName, filterLeafCode)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/repositories/paged?page=1&size=10&groupCode=" + filterParentCode)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value(filterRepoName))
                .andExpect(jsonPath("$.data[0].groupCode").value(filterLeafCode));
    }

    @Test
    @Order(9)
    void initialVersion() throws Exception {
        mockMvc.perform(get("/api/v1/repositories/" + repoId + "/initial-version")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value("1.2.3"))
                .andExpect(jsonPath("$.data.versionSource").value("MANUAL"));
    }

    @Test
    @Order(10)
    void setInitialVersion() throws Exception {
        mockMvc.perform(put("/api/v1/repositories/" + repoId + "/initial-version")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"1.0.0\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(11)
    void createOnNonLeafGroupFails() throws Exception {
        // Create a child group to make another group non-leaf
        String parentCode = "e2e-repo-p-" + System.currentTimeMillis();
        mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"name\":\"RParent\",\"code\":\"%s\",\"parentCode\":null}", parentCode)))
                .andExpect(status().isOk());

        String childCode = "e2e-repo-c-" + System.currentTimeMillis();
        mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"name\":\"RChild\",\"code\":\"%s\",\"parentCode\":\"%s\"}", childCode, parentCode)))
                .andExpect(status().isOk());

        // Create repo on non-leaf parent → should fail
        mockMvc.perform(post("/api/v1/repositories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"name\":\"FailRepo\",\"cloneUrl\":\"https://x.com/fail.git\",\"groupCode\":\"%s\"}", parentCode)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @Order(12)
    void deleteRepository() throws Exception {
        mockMvc.perform(delete("/api/v1/repositories/" + repoId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
