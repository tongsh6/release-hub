package io.releasehub.bootstrap.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.releasehub.application.releasewindow.ReleaseWindowView;
import io.releasehub.bootstrap.ReleaseHubApplication;
import io.releasehub.common.response.ApiResponse;
import io.releasehub.interfaces.api.releasewindow.CreateReleaseWindowRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ReleaseWindow 全流程集成测试
 * 覆盖：创建 -> 配置 -> 冻结 -> 发布
 */
@SpringBootTest(classes = ReleaseHubApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReleaseWindowFlowIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void should_complete_release_window_lifecycle() throws Exception {
        String token = loginAndGetToken();
        String groupCode = createGroupAndGetCode(token);

        // 1. Create
        CreateReleaseWindowRequest createRequest = new CreateReleaseWindowRequest();
        createRequest.setName("2024-IT-01");
        createRequest.setGroupCode(groupCode);

        MvcResult createResult = mockMvc.perform(post("/api/v1/release-windows")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andReturn();

        ApiResponse<ReleaseWindowView> createResponse = objectMapper.readValue(
                createResult.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructParametricType(ApiResponse.class, ReleaseWindowView.class)
        );
        String id = createResponse.getData().getId();
        assertThat(id).isNotNull();
        assertThat(createResponse.getData().getStatus()).isEqualTo("DRAFT");

        // 2. Attach iteration（publish 需要至少一个迭代）
        String iterKey = createIterationAndGetKey(token, groupCode);
        mockMvc.perform(post("/api/v1/release-windows/" + id + "/attach")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"iterationKeys\":[\"" + iterKey + "\"]}"))
                .andExpect(status().isOk());

        // 4. Freeze
        mockMvc.perform(post("/api/v1/release-windows/" + id + "/freeze")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        // 5. Publish
        MvcResult publishResult = mockMvc.perform(post("/api/v1/release-windows/" + id + "/publish")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn();

        ApiResponse<ReleaseWindowView> publishResponse = objectMapper.readValue(
                publishResult.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructParametricType(ApiResponse.class, ReleaseWindowView.class)
        );

        assertThat(publishResponse.getData().getStatus()).isEqualTo("PUBLISHED");
        assertThat(publishResponse.getData().isFrozen()).isTrue();
        assertThat(publishResponse.getData().getPublishedAt()).isNotNull();
    }

    private String loginAndGetToken() throws Exception {
        String body = "{\"username\":\"admin\",\"password\":\"admin\"}";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").exists())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("data").get("token").asText();
    }

    private String createGroupAndGetCode(String token) throws Exception {
        String code = "G" + System.currentTimeMillis();
        String req = "{\"name\":\"IT-Group\",\"code\":\"" + code + "\",\"parentCode\":null}";
        mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(objectMapper.readTree(req))))
                .andExpect(status().isOk());
        return code;
    }

    private String createIterationAndGetKey(String token, String groupCode) throws Exception {
        String name = "IT-Iter-" + System.currentTimeMillis();
        String req = String.format("{\"name\":\"%s\",\"description\":\"IT iter\",\"groupCode\":\"%s\"}", name, groupCode);
        MvcResult result = mockMvc.perform(post("/api/v1/iterations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("key").asText();
    }
}
