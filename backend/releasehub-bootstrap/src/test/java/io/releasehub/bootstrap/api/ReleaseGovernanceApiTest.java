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
class ReleaseGovernanceApiTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldReadCandidateReviewAndPersistManualSignoff() throws Exception {
        String token = loginAndGetToken();

        mockMvc.perform(get("/api/v1/release-governance/candidate-review")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candidateId").value("release-candidate-2026-05-23"))
                .andExpect(jsonPath("$.data.evidence.length()").value(4))
                .andExpect(jsonPath("$.data.checklist.length()").value(4));

        String body = """
                {
                  "candidateId": "release-candidate-2026-05-23",
                  "decision": "APPROVE_FOR_CONTROLLED_REVIEW",
                  "note": "进入 staging 评审",
                  "checklist": [
                    {
                      "key": "acceptance-baseline",
                      "status": "CONFIRMED",
                      "note": "170/0/0 已确认"
                    },
                    {
                      "key": "static-scan",
                      "status": "CONFIRMED",
                      "note": "静态扫描已确认"
                    },
                    {
                      "key": "data-quality-boundary",
                      "status": "CONFIRMED",
                      "note": "数据质量队列已确认"
                    },
                    {
                      "key": "deferred-scope",
                      "status": "CONFIRMED",
                      "note": "暂缓项已确认"
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/release-governance/candidate-review/signoffs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candidateId").value("release-candidate-2026-05-23"))
                .andExpect(jsonPath("$.data.reviewer").value("admin"))
                .andExpect(jsonPath("$.data.decision").value("APPROVE_FOR_CONTROLLED_REVIEW"))
                .andExpect(jsonPath("$.data.checklist[0].status").value("CONFIRMED"));

        mockMvc.perform(get("/api/v1/release-governance/candidate-review")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.latestSignoff.reviewer").value("admin"))
                .andExpect(jsonPath("$.data.latestSignoff.decision").value("APPROVE_FOR_CONTROLLED_REVIEW"));
    }

    private String loginAndGetToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").exists())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("data").get("token").asText();
    }
}
