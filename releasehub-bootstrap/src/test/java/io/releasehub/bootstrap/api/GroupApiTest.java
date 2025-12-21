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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("unitTest")
class GroupApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldCreateAndGetGroup() throws Exception {
        String token = loginAndGetToken();
        String req = "{\"name\":\"A\",\"code\":\"001\",\"parentCode\":null}";
        MvcResult createRes = mockMvc.perform(post("/api/v1/groups")
                                             .header("Authorization", "Bearer " + token)
                                             .contentType(MediaType.APPLICATION_JSON)
                                             .content(req))
                                     .andExpect(status().isOk())
                                     .andExpect(jsonPath("$.data").exists())
                                     .andReturn();
        String id = objectMapper.readTree(createRes.getResponse().getContentAsString()).get("data").asText();
        assertThat(id).isNotEmpty();

        mockMvc.perform(get("/api/v1/groups/by-code/001")
                       .header("Authorization", "Bearer " + token))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.data.code").value("001"))
               .andExpect(jsonPath("$.data.name").value("A"));
    }

    private String loginAndGetToken() throws Exception {
        String body = "{\"username\":\"admin\",\"password\":\"admin\"}";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                                          .contentType(MediaType.APPLICATION_JSON)
                                          .content(body))
                                  .andExpect(status().isOk())
                                  .andExpect(jsonPath("$.token").exists())
                                  .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("token").asText();
    }
}
