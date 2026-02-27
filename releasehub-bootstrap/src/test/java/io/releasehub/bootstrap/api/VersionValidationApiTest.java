package io.releasehub.bootstrap.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.releasehub.interfaces.api.releasewindow.CreateReleaseWindowRequest;
import io.releasehub.interfaces.api.releasewindow.VersionValidationRequest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 版本校验 API 集成测试
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VersionValidationApiTest {

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
        request.setName("Version Validation Test Window");
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
    void shouldReturnErrorForMissingPolicyId() throws Exception {
        // policyId 是必填字段，缺失时应该返回错误
        VersionValidationRequest request = new VersionValidationRequest();
        request.setCurrentVersion("1.0.0");
        // 故意不设置 policyId

        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/validate")
                       .header("Authorization", "Bearer " + token)
                       .contentType(MediaType.APPLICATION_JSON)
                       .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isBadRequest());
    }

    @Test
    @Order(4)
    void shouldReturnErrorForInvalidPolicyId() throws Exception {
        VersionValidationRequest request = new VersionValidationRequest();
        request.setPolicyId("invalid-policy-id");
        request.setCurrentVersion("1.0.0");

        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/validate")
                       .header("Authorization", "Bearer " + token)
                       .contentType(MediaType.APPLICATION_JSON)
                       .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isNotFound());
    }

    @Test
    @Order(5)
    void shouldReturnErrorForEmptyPolicyId() throws Exception {
        VersionValidationRequest request = new VersionValidationRequest();
        request.setPolicyId(""); // 空字符串
        request.setCurrentVersion("2.5.3");

        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/validate")
                       .header("Authorization", "Bearer " + token)
                       .contentType(MediaType.APPLICATION_JSON)
                       .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isBadRequest());
    }

    @Test
    @Order(6)
    void shouldReturnErrorForNonExistentPolicyId() throws Exception {
        // 测试当 policyId 格式正确但不存在时的行为
        VersionValidationRequest request = new VersionValidationRequest();
        request.setPolicyId("00000000-0000-0000-0000-000000000000"); // 有效 UUID 格式但不存在
        request.setCurrentVersion("1.0.0");

        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/validate")
                       .header("Authorization", "Bearer " + token)
                       .contentType(MediaType.APPLICATION_JSON)
                       .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isNotFound());
    }

    @Test
    @Order(7)
    void shouldReturnErrorForMissingCurrentVersion() throws Exception {
        // currentVersion 现在是必填字段，缺失时应该返回 400
        VersionValidationRequest request = new VersionValidationRequest();
        request.setPolicyId("MINOR");
        // 故意不设置 currentVersion

        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/validate")
                       .header("Authorization", "Bearer " + token)
                       .contentType(MediaType.APPLICATION_JSON)
                       .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isBadRequest());
    }

    @Test
    @Order(8)
    void shouldDerivePatchVersion() throws Exception {
        VersionValidationRequest request = new VersionValidationRequest();
        request.setPolicyId("PATCH");
        request.setCurrentVersion("1.2.3");

        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/validate")
                       .header("Authorization", "Bearer " + token)
                       .contentType(MediaType.APPLICATION_JSON)
                       .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.data.valid").value(true))
               .andExpect(jsonPath("$.data.derivedVersion").value("1.2.4"));
    }

    @Test
    @Order(9)
    void shouldDeriveMinorVersion() throws Exception {
        VersionValidationRequest request = new VersionValidationRequest();
        request.setPolicyId("MINOR");
        request.setCurrentVersion("1.2.3");

        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/validate")
                       .header("Authorization", "Bearer " + token)
                       .contentType(MediaType.APPLICATION_JSON)
                       .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.data.valid").value(true))
               .andExpect(jsonPath("$.data.derivedVersion").value("1.3.0"));
    }

    @Test
    @Order(10)
    void shouldDeriveMajorVersion() throws Exception {
        VersionValidationRequest request = new VersionValidationRequest();
        request.setPolicyId("MAJOR");
        request.setCurrentVersion("1.2.3");

        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/validate")
                       .header("Authorization", "Bearer " + token)
                       .contentType(MediaType.APPLICATION_JSON)
                       .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.data.valid").value(true))
               .andExpect(jsonPath("$.data.derivedVersion").value("2.0.0"));
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
}
