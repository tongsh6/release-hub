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
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WindowIterationApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldListAttachAndDetachIterations() throws Exception {
        String token = loginAndGetToken();
        String groupCode = createGroupAndGetCode(token);

        String createWindow = "{\"name\":\"RW-ITER\",\"groupCode\":\"" + groupCode + "\"}";
        MvcResult rwCreate = mockMvc.perform(post("/api/v1/release-windows")
                                            .header("Authorization", "Bearer " + token)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(createWindow))
                                    .andExpect(status().isOk())
                                    .andExpect(jsonPath("$.data.id").exists())
                                    .andReturn();
        String windowId = objectMapper.readTree(rwCreate.getResponse().getContentAsString()).get("data").get("id").asText();

        var itAResult = mockMvc.perform(post("/api/v1/iterations")
                       .header("Authorization", "Bearer " + token)
                       .contentType(MediaType.APPLICATION_JSON)
                       .content("{\"name\":\"IT-A\",\"description\":\"d\",\"groupCode\":\"" + groupCode + "\",\"repoIds\":[\"repo-1\"]}"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.data.key").exists())
               .andReturn();
        String itAKey = objectMapper.readTree(itAResult.getResponse().getContentAsString()).get("data").get("key").asText();
        var itBResult = mockMvc.perform(post("/api/v1/iterations")
                       .header("Authorization", "Bearer " + token)
                       .contentType(MediaType.APPLICATION_JSON)
                       .content("{\"name\":\"IT-B\",\"description\":\"d\",\"groupCode\":\"" + groupCode + "\",\"repoIds\":[\"repo-2\"]}"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.data.key").exists())
               .andReturn();
        String itBKey = objectMapper.readTree(itBResult.getResponse().getContentAsString()).get("data").get("key").asText();

        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/attach")
                       .header("Authorization", "Bearer " + token)
                       .contentType(MediaType.APPLICATION_JSON)
                       .content(objectMapper.writeValueAsString(java.util.Map.of("iterationKeys", java.util.List.of(itAKey, itBKey)))))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.data[0]").value(itAKey))
               .andExpect(jsonPath("$.data[1]").value(itBKey));

        mockMvc.perform(get("/api/v1/release-windows/" + windowId + "/iterations")
                       .header("Authorization", "Bearer " + token))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.success").value(true))
               .andExpect(jsonPath("$.data").isArray())
               .andExpect(jsonPath("$.data[0].iterationKey").exists())
               .andExpect(jsonPath("$.data[0].attachAt").exists());

        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/detach")
                       .header("Authorization", "Bearer " + token)
                       .contentType(MediaType.APPLICATION_JSON)
                       .content(objectMapper.writeValueAsString(java.util.Map.of("iterationKey", itAKey))))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(get("/api/v1/release-windows/" + windowId + "/iterations")
                       .header("Authorization", "Bearer " + token))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.data").isArray());
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
}
