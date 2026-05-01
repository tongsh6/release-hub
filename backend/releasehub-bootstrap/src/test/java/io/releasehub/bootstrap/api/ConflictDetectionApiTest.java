package io.releasehub.bootstrap.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.releasehub.interfaces.api.releasewindow.CreateReleaseWindowRequest;
import io.releasehub.interfaces.auth.AuthController.LoginRequest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 冲突检测 API 集成测试
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConflictDetectionApiTest {

    private static String token;
    private static String windowId;
    private static String groupCode;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @Order(1)
    void shouldLogin() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("admin");
        loginRequest.setPassword("admin");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                                          .contentType(MediaType.APPLICATION_JSON)
                                          .content(objectMapper.writeValueAsString(loginRequest)))
                                  .andExpect(status().isOk())
                                  .andExpect(jsonPath("$.data.token").exists())
                                  .andReturn();

        token = objectMapper.readTree(result.getResponse().getContentAsString())
                            .get("data").get("token").asText();
        assertThat(token).isNotBlank();

        groupCode = createGroupAndGetCode(token);
    }

    @Test
    @Order(2)
    void shouldCreateReleaseWindow() throws Exception {
        CreateReleaseWindowRequest request = new CreateReleaseWindowRequest();
        request.setName("Conflict Detection Test Window");
        request.setGroupCode(groupCode);

        MvcResult result = mockMvc.perform(post("/api/v1/release-windows")
                                          .header("Authorization", "Bearer " + token)
                                          .contentType(MediaType.APPLICATION_JSON)
                                          .content(objectMapper.writeValueAsString(request)))
                                  .andExpect(status().isOk())
                                  .andExpect(jsonPath("$.data.id").exists())
                                  .andReturn();

        windowId = objectMapper.readTree(result.getResponse().getContentAsString())
                               .get("data").get("id").asText();
        assertThat(windowId).isNotBlank();
    }

    @Test
    @Order(3)
    void shouldScanConflictsSuccessfully() throws Exception {
        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/conflicts/check")
                       .header("Authorization", "Bearer " + token))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.data.windowId").value(windowId))
               .andExpect(jsonPath("$.data.hasConflicts").exists())
               .andExpect(jsonPath("$.data.totalCount").exists())
               .andExpect(jsonPath("$.data.conflicts").isArray());
    }

    @Test
    @Order(4)
    void shouldGetConflictsAfterScan() throws Exception {
        mockMvc.perform(get("/api/v1/release-windows/" + windowId + "/conflicts")
                       .header("Authorization", "Bearer " + token))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.data.windowId").value(windowId));
    }

    @Test
    @Order(5)
    void shouldReturn401WithoutToken() throws Exception {
        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/conflicts/check"))
               .andExpect(status().isUnauthorized());
    }

    private String createGroupAndGetCode(String token) throws Exception {
        String code = "G" + System.currentTimeMillis();
        String req = "{\"name\":\"CD-Group\",\"code\":\"" + code + "\",\"parentCode\":null}";
        mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists());
        return code;
    }
}
