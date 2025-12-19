package io.releasehub.bootstrap.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.releasehub.application.releasewindow.ReleaseWindowView;
import io.releasehub.bootstrap.ReleaseHubApplication;
import io.releasehub.common.response.ApiResponse;
import io.releasehub.interfaces.api.releasewindow.ConfigureReleaseWindowRequest;
import io.releasehub.interfaces.api.releasewindow.CreateReleaseWindowRequest;
import io.releasehub.interfaces.api.releasewindow.FreezeReleaseWindowRequest;
import io.releasehub.interfaces.api.releasewindow.PublishReleaseWindowRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
        // 1. Create
        CreateReleaseWindowRequest createRequest = new CreateReleaseWindowRequest();
        createRequest.setName("2024-IT-01");

        MvcResult createResult = mockMvc.perform(post("/api/v1/release-windows")
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

        // 2. Configure
        ConfigureReleaseWindowRequest configRequest = new ConfigureReleaseWindowRequest();
        Instant now = Instant.now();
        configRequest.setStartAt(now.plus(1, ChronoUnit.DAYS).toString());
        configRequest.setEndAt(now.plus(3, ChronoUnit.DAYS).toString());

        mockMvc.perform(put("/api/v1/release-windows/" + id + "/window")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(configRequest)))
                .andExpect(status().isOk());

        // 3. Freeze
        FreezeReleaseWindowRequest freezeRequest = new FreezeReleaseWindowRequest();
        mockMvc.perform(post("/api/v1/release-windows/" + id + "/freeze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(freezeRequest)))
                .andExpect(status().isOk());

        // 4. Publish
        PublishReleaseWindowRequest publishRequest = new PublishReleaseWindowRequest();
        MvcResult publishResult = mockMvc.perform(post("/api/v1/release-windows/" + id + "/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(publishRequest)))
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
}
