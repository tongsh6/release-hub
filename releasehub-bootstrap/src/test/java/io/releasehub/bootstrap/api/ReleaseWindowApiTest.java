package io.releasehub.bootstrap.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.releasehub.interfaces.api.releasewindow.CreateReleaseWindowRequest;
import io.releasehub.interfaces.auth.AuthController.LoginRequest;
import org.junit.jupiter.api.Test;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReleaseWindowApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldCreateGetAndListReleaseWindow() throws Exception {
        String token = loginAndGetToken();
        String groupCode = createGroupAndGetCode(token);

        CreateReleaseWindowRequest createRequest = new CreateReleaseWindowRequest();
        createRequest.setName("UT-RW-01");
        createRequest.setGroupCode(groupCode);

        MvcResult createResult = mockMvc.perform(post("/api/v1/release-windows")
                                                .header("Authorization", "Bearer " + token)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(createRequest)))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.data.id").exists())
                                        .andReturn();

        String id = objectMapper.readTree(createResult.getResponse().getContentAsString())
                                .get("data").get("id").asText();
        assertThat(id).isNotBlank();

        mockMvc.perform(get("/api/v1/release-windows/" + id)
                       .header("Authorization", "Bearer " + token))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.data.id").value(id));

        mockMvc.perform(get("/api/v1/release-windows")
                       .header("Authorization", "Bearer " + token))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.data").isArray());
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
        String response = result.getResponse().getContentAsString();
        return objectMapper.readTree(response).get("data").get("token").asText();
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
