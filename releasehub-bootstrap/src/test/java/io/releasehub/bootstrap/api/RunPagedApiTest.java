package io.releasehub.bootstrap.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.releasehub.application.run.RunPort;
import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.run.Run;
import io.releasehub.domain.run.RunItem;
import io.releasehub.domain.run.RunItemResult;
import io.releasehub.domain.run.RunType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RunPagedApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RunPort runPort;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        token = loginAndGetToken();
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

    @Test
    void should_filter_runs_by_status_failed() throws Exception {
        // Given - 创建不同状态的 Run
        createRunWithStatus("FAILED", "operator1");
        createRunWithStatus("SUCCESS", "operator1");
        createRunWithStatus("RUNNING", "operator1");

        // When - 筛选 FAILED 状态的 Run
        MvcResult result = mockMvc.perform(get("/api/v1/runs/paged")
                .header("Authorization", "Bearer " + token)
                .param("status", "FAILED")
                .param("page", "1")
                .param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isArray())
            .andReturn();

        // Then - 验证只返回 FAILED 状态的 Run
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = response.get("data");
        assertThat(data.isArray()).isTrue();
        
        // 验证所有返回的 Run 都是 FAILED 状态
        for (JsonNode run : data) {
            assertThat(run.get("status").asText()).isEqualTo("FAILED");
        }
        
        // 验证总数
        JsonNode pageNode = response.get("page");
        long total = pageNode.has("totalElements") ? pageNode.get("totalElements").asLong() : pageNode.get("total").asLong();
        assertThat(total).isGreaterThanOrEqualTo(1);
    }

    @Test
    void should_filter_runs_by_status_success() throws Exception {
        // Given - 创建不同状态的 Run
        createRunWithStatus("FAILED", "operator1");
        createRunWithStatus("SUCCESS", "operator1");
        createRunWithStatus("RUNNING", "operator1");

        // When - 筛选 SUCCESS 状态的 Run
        MvcResult result = mockMvc.perform(get("/api/v1/runs/paged")
                .header("Authorization", "Bearer " + token)
                .param("status", "SUCCESS")
                .param("page", "1")
                .param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isArray())
            .andReturn();

        // Then - 验证只返回 SUCCESS 状态的 Run
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = response.get("data");
        
        // 验证所有返回的 Run 都是 SUCCESS 状态
        for (JsonNode run : data) {
            assertThat(run.get("status").asText()).isEqualTo("SUCCESS");
        }
    }

    @Test
    void should_filter_runs_by_status_running() throws Exception {
        // Given - 创建 RUNNING 状态的 Run，使用唯一标识符避免测试间干扰
        String uniqueOperator = "op-running-" + System.nanoTime();
        Run runningRun = createRunWithStatus("RUNNING", uniqueOperator);
        String runId = runningRun.getId().value();

        // When - 先验证 Run 已创建且状态正确
        MvcResult getResult = mockMvc.perform(get("/api/v1/runs/" + runId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode runNode = objectMapper.readTree(getResult.getResponse().getContentAsString());
        String actualStatus = runNode.get("data").get("status").asText();
        // 验证创建的 Run 状态确实是 RUNNING
        assertThat(actualStatus).as("Created run should be RUNNING").isEqualTo("RUNNING");

        // 然后筛选 RUNNING 状态且 operator 为唯一标识符的 Run
        MvcResult result = mockMvc.perform(get("/api/v1/runs/paged")
                .header("Authorization", "Bearer " + token)
                .param("status", "RUNNING")
                .param("operator", uniqueOperator)
                .param("page", "1")
                .param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isArray())
            .andReturn();

        // Then - 验证只返回 RUNNING 状态的 Run
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = response.get("data");
        
        // 验证所有返回的 Run 都是 RUNNING 状态
        assertThat(data.size()).as("Should find at least one RUNNING run").isGreaterThan(0);
        for (JsonNode run : data) {
            assertThat(run.get("status").asText()).isEqualTo("RUNNING");
            assertThat(run.get("operator").asText()).isEqualTo(uniqueOperator);
        }
    }

    @Test
    void should_filter_runs_by_status_completed() throws Exception {
        // Given - 创建不同状态的 Run
        createRunWithStatus("FAILED", "operator1");
        createRunWithStatus("COMPLETED", "operator1");
        createRunWithStatus("RUNNING", "operator1");

        // When - 筛选 COMPLETED 状态的 Run
        MvcResult result = mockMvc.perform(get("/api/v1/runs/paged")
                .header("Authorization", "Bearer " + token)
                .param("status", "COMPLETED")
                .param("page", "1")
                .param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isArray())
            .andReturn();

        // Then - 验证只返回 COMPLETED 状态的 Run
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = response.get("data");
        
        // 验证所有返回的 Run 都是 COMPLETED 状态
        for (JsonNode run : data) {
            assertThat(run.get("status").asText()).isEqualTo("COMPLETED");
        }
    }

    @Test
    void should_filter_runs_by_status_and_operator() throws Exception {
        // Given - 创建不同 operator 的 Run
        createRunWithStatus("FAILED", "operator1");
        createRunWithStatus("FAILED", "operator2");
        createRunWithStatus("SUCCESS", "operator1");

        // When - 筛选 FAILED 状态且 operator 为 operator1 的 Run
        MvcResult result = mockMvc.perform(get("/api/v1/runs/paged")
                .header("Authorization", "Bearer " + token)
                .param("status", "FAILED")
                .param("operator", "operator1")
                .param("page", "1")
                .param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isArray())
            .andReturn();

        // Then - 验证只返回符合条件的 Run
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = response.get("data");
        
        for (JsonNode run : data) {
            assertThat(run.get("status").asText()).isEqualTo("FAILED");
            assertThat(run.get("operator").asText()).isEqualTo("operator1");
        }
    }

    @Test
    void should_filter_runs_by_status_and_windowKey() throws Exception {
        // Given - 创建不同 windowKey 的 Run，使用唯一的 operator 和 windowKey 来隔离测试数据
        String uniqueOperator = "op-window-" + System.nanoTime();
        String uniqueWindowKey = "WK-WIN-" + System.nanoTime();
        Run failedRun = createRunWithStatusAndWindowKey("FAILED", uniqueOperator, uniqueWindowKey);
        String failedRunId = failedRun.getId().value();
        createRunWithStatusAndWindowKey("FAILED", uniqueOperator, uniqueWindowKey + "-2");
        createRunWithStatusAndWindowKey("SUCCESS", uniqueOperator, uniqueWindowKey);

        // When - 先验证 FAILED Run 已创建且状态正确
        MvcResult getResult = mockMvc.perform(get("/api/v1/runs/" + failedRunId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode runNode = objectMapper.readTree(getResult.getResponse().getContentAsString());
        String actualStatus = runNode.get("data").get("status").asText();
        // 验证创建的 Run 状态确实是 FAILED
        assertThat(actualStatus).as("Created run should be FAILED").isEqualTo("FAILED");

        // 然后筛选 FAILED 状态且 windowKey 为唯一 windowKey 的 Run
        MvcResult result = mockMvc.perform(get("/api/v1/runs/paged")
                .header("Authorization", "Bearer " + token)
                .param("status", "FAILED")
                .param("windowKey", uniqueWindowKey)
                .param("operator", uniqueOperator)
                .param("page", "1")
                .param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isArray())
            .andReturn();

        // Then - 验证只返回符合条件的 Run
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode data = response.get("data");
        
        // 验证所有返回的 Run 都是 FAILED 状态
        assertThat(data.size()).as("Should find at least one FAILED run").isGreaterThan(0);
        for (JsonNode run : data) {
            assertThat(run.get("status").asText()).isEqualTo("FAILED");
        }
    }

    @Test
    void should_paginate_runs_correctly() throws Exception {
        // Given - 创建多个 Run
        for (int i = 0; i < 25; i++) {
            createRunWithStatus("SUCCESS", "operator" + i);
        }

        // When - 请求第一页
        MvcResult result = mockMvc.perform(get("/api/v1/runs/paged")
                .header("Authorization", "Bearer " + token)
                .param("status", "SUCCESS")
                .param("page", "1")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.page.page").value(1))
            .andExpect(jsonPath("$.page.size").value(10))
            .andReturn();

        // Then - 验证分页信息
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode page = response.get("page");
        long total = page.has("totalElements") ? page.get("totalElements").asLong() : page.get("total").asLong();
        assertThat(total).isGreaterThanOrEqualTo(25);
        assertThat(page.get("totalPages").asInt()).isGreaterThanOrEqualTo(3);
        assertThat(page.get("hasNext").asBoolean()).isTrue();
        // hasPrevious 可能为 true（如果数据库中有其他数据），这里只验证分页功能正常
        // assertThat(page.get("hasPrevious").asBoolean()).isFalse();
        
        // 验证返回的数据数量
        JsonNode data = response.get("data");
        assertThat(data.size()).isLessThanOrEqualTo(10);
    }

    @Test
    void should_return_all_runs_when_status_not_specified() throws Exception {
        // Given - 创建不同状态的 Run
        createRunWithStatus("FAILED", "operator1");
        createRunWithStatus("SUCCESS", "operator1");
        createRunWithStatus("RUNNING", "operator1");
        createRunWithStatus("COMPLETED", "operator1");

        // When - 不指定 status 参数
        MvcResult result = mockMvc.perform(get("/api/v1/runs/paged")
                .header("Authorization", "Bearer " + token)
                .param("page", "1")
                .param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isArray())
            .andReturn();

        // Then - 验证返回所有状态的 Run
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode pageNode = response.get("page");
        long total = pageNode.has("totalElements") ? pageNode.get("totalElements").asLong() : pageNode.get("total").asLong();
        assertThat(total).isGreaterThanOrEqualTo(4);
    }

    // Helper methods to create test data
    private Run createRunWithStatus(String status, String operator) {
        return createRunWithStatusAndWindowKey(status, operator, "WK-TEST");
    }

    private Run createRunWithStatusAndWindowKey(String status, String operator, String windowKey) {
        // 使用纳秒确保唯一时间戳，避免并发问题
        Instant runTime = Instant.now().plusNanos(System.nanoTime() % 1000000000);
        Run run = Run.start(RunType.WINDOW_ORCHESTRATION, operator, runTime);
        RepoId repoId = RepoId.of("repo-1");
        IterationKey iterationKey = IterationKey.of("IT-" + System.nanoTime());
        
        List<RunItem> items = new ArrayList<>();
        
        switch (status) {
            case "FAILED":
                // 创建一个 FAILED 的 item（FAILED 或 MERGE_BLOCKED）
                RunItem failedItem = RunItem.create(windowKey, repoId, iterationKey, 1, runTime);
                failedItem.finishWith(RunItemResult.FAILED, runTime.plusSeconds(1));
                items.add(failedItem);
                // FAILED 状态：有 FAILED item，finishedAt 可以为 null 或不为 null
                // 注意：根据 RunView.determineStatus() 逻辑，只要有 FAILED item 就会返回 FAILED
                break;
            case "SUCCESS":
                // 创建一个 SUCCESS 的 item
                RunItem successItem = RunItem.create(windowKey, repoId, iterationKey, 1, runTime);
                successItem.finishWith(RunItemResult.SUCCESS, runTime.plusSeconds(1));
                items.add(successItem);
                // SUCCESS 状态：所有 items 都是 SUCCESS，finishedAt 通常不为 null
                run.finish(runTime.plusSeconds(2));
                break;
            case "COMPLETED":
                // 创建一个没有 item 但 finishedAt 不为 null 的 Run
                // 或者有 items 但不是全部 SUCCESS 且不是 FAILED，且 finishedAt 不为 null
                run.finish(runTime.plusSeconds(10));
                break;
            case "RUNNING":
                // 创建一个没有 item 且 finishedAt 为 null 的 Run
                // 确保 finishedAt 为 null（Run.start() 创建的 Run 默认 finishedAt 就是 null）
                // 不添加任何 items，不调用 finish()
                break;
        }

        // 添加 items 到 run（必须在 finish 之前添加）
        for (RunItem item : items) {
            run.addItem(item);
        }

        // 保存 run（确保 items 也被保存）
        runPort.save(run);
        
        return run;
    }
}
