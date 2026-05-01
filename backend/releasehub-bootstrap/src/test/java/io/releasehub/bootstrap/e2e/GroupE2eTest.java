package io.releasehub.bootstrap.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GroupE2eTest extends AbstractE2ETest {

    private static String token;
    private static String parentCode;
    private static String parentId;
    private static String childCode;
    private static String childId;

    @Test
    @Order(1)
    void createParentGroup() throws Exception {
        token = loginAndGetToken();
        parentCode = "e2e-parent-" + System.currentTimeMillis();

        MvcResult result = mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"name\":\"E2E Parent\",\"code\":\"%s\",\"parentCode\":null}", parentCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists())
                .andReturn();

        parentId = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").asText();
        assertThat(parentId).isNotBlank();
    }

    @Test
    @Order(2)
    void createChildGroup() throws Exception {
        childCode = "e2e-child-" + System.currentTimeMillis();

        MvcResult result = mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"name\":\"E2E Child\",\"code\":\"%s\",\"parentCode\":\"%s\"}", childCode, parentCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists())
                .andReturn();

        childId = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").asText();
        assertThat(childId).isNotBlank();
    }

    @Test
    @Order(3)
    void getById() throws Exception {
        mockMvc.perform(get("/api/v1/groups/" + parentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("E2E Parent"))
                .andExpect(jsonPath("$.data.code").value(parentCode));
    }

    @Test
    @Order(4)
    void getByCode() throws Exception {
        mockMvc.perform(get("/api/v1/groups/by-code/" + childCode)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value(childCode))
                .andExpect(jsonPath("$.data.parentCode").value(parentCode));
    }

    @Test
    @Order(5)
    void updateGroup() throws Exception {
        mockMvc.perform(put("/api/v1/groups/" + parentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "E2E Parent Updated"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("E2E Parent Updated"));
    }

    @Test
    @Order(6)
    void listAll() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/groups")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        assertThat(data.size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @Order(7)
    void listPaged() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/groups/paged?page=1&size=10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(root.get("data").size()).isGreaterThanOrEqualTo(2);
        assertThat(root.get("page").get("total").asLong()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @Order(8)
    void children() throws Exception {
        mockMvc.perform(get("/api/v1/groups/children/" + parentCode)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].code").value(childCode));
    }

    @Test
    @Order(9)
    void topLevel() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/groups/top-level")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        boolean hasParent = false, hasChild = false;
        for (JsonNode g : data) {
            if (g.get("code").asText().equals(parentCode)) hasParent = true;
            if (g.get("code").asText().equals(childCode)) hasChild = true;
        }
        assertThat(hasParent).isTrue();
        assertThat(hasChild).isFalse();
    }

    @Test
    @Order(10)
    void tree() throws Exception {
        mockMvc.perform(get("/api/v1/groups/tree")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @Order(11)
    void duplicateCodeReturnsError() throws Exception {
        mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"name\":\"Dup\",\"code\":\"%s\",\"parentCode\":null}", parentCode)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @Order(12)
    void deleteGroupWithChildrenFails() throws Exception {
        mockMvc.perform(delete("/api/v1/groups/" + parentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @Order(13)
    void deleteChildThenParent() throws Exception {
        mockMvc.perform(delete("/api/v1/groups/" + childId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/groups/" + parentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
