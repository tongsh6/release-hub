package io.releasehub.bootstrap.e2e;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IterationE2eTest extends AbstractE2ETest {

    private static String token;
    private static String groupCode;
    private static String repoId;
    private static String iterKey;

    @Test
    @Order(1)
    void createIteration() throws Exception {
        token = loginAndGetToken();
        groupCode = createGroup(token);
        repoId = createRepo(token, groupCode);

        String name = "e2e-iter-" + System.currentTimeMillis();
        mockMvc.perform(post("/api/v1/iterations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name, "groupCode", groupCode, "repoIds", List.of(repoId)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.key").isNotEmpty())
                .andExpect(jsonPath("$.data.name").value(name))
                .andDo(result -> {
                    iterKey = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("key").asText();
                });
    }

    @Test
    @Order(2)
    void getByKey() throws Exception {
        mockMvc.perform(get("/api/v1/iterations/" + iterKey)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.key").value(iterKey));
    }

    @Test
    @Order(3)
    void listAll() throws Exception {
        mockMvc.perform(get("/api/v1/iterations")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @Order(4)
    void listPaged() throws Exception {
        mockMvc.perform(get("/api/v1/iterations/paged?page=1&size=10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.page.page").value(1));
    }

    @Test
    @Order(5)
    void listRepos() throws Exception {
        mockMvc.perform(get("/api/v1/iterations/" + iterKey + "/repos")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    @Order(6)
    void removeRepo() throws Exception {
        mockMvc.perform(post("/api/v1/iterations/" + iterKey + "/repos/remove")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("repoIds", List.of(repoId)))))
                .andExpect(status().isOk());
    }

    @Test
    @Order(7)
    void addRepo() throws Exception {
        mockMvc.perform(post("/api/v1/iterations/" + iterKey + "/repos/add")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("repoIds", List.of(repoId)))))
                .andExpect(status().isOk());
    }

    @Test
    @Order(8)
    void updateIteration() throws Exception {
        mockMvc.perform(put("/api/v1/iterations/" + iterKey)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "E2E Iter Updated", "groupCode", groupCode))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("E2E Iter Updated"));
    }

    @Test
    @Order(9)
    void deleteIteration() throws Exception {
        mockMvc.perform(delete("/api/v1/iterations/" + iterKey)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
