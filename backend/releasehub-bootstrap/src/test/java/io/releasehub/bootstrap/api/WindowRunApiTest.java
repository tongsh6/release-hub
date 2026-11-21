package io.releasehub.bootstrap.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.releasehub.application.port.out.GitBranchAdapterFactory;
import io.releasehub.application.port.out.GitBranchPort;
import io.releasehub.domain.repo.GitProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:window-run-api;MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WindowRunApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GitBranchAdapterFactory gitFactory;

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
    void shouldAttachPlanOrchestrateAndRetry() throws Exception {
        // HTTP + JPA contract test with explicit Git port outcomes; this is not a real GitLab acceptance run.
        GitBranchPort git = mock(GitBranchPort.class);
        when(gitFactory.getAdapter(GitProvider.GITLAB)).thenReturn(git);
        when(git.getBranchStatus(anyString(), anyString(), anyString()))
                .thenReturn(GitBranchPort.BranchStatus.present("a".repeat(40)));
        when(git.createBranch(anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        String token = loginAndGetToken();
        String groupCode = createGroupAndGetCode(token);

        String createWindow = "{\"name\":\"RW-UT\",\"groupCode\":\"" + groupCode + "\"}";
        MvcResult rwCreate = mockMvc.perform(post("/api/v1/release-windows")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createWindow))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").exists())
            .andReturn();
        String windowId = objectMapper.readTree(rwCreate.getResponse().getContentAsString()).get("data").get("id").asText();
        String windowKey = objectMapper.readTree(rwCreate.getResponse().getContentAsString()).get("data").get("windowKey").asText();
        CreatedRepo repo1 = createRepo(token, groupCode, "repo-1");

        var it1Result = mockMvc.perform(post("/api/v1/iterations")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"IT-1\",\"description\":\"d\",\"groupCode\":\"" + groupCode + "\",\"repoIds\":[\"" + repo1.id() + "\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.key").exists())
            .andReturn();
        String it1Key = objectMapper.readTree(it1Result.getResponse().getContentAsString())
                .get("data").get("key").asText();
        var it2Result = mockMvc.perform(post("/api/v1/iterations")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"IT-2\",\"description\":\"d\",\"groupCode\":\"" + groupCode + "\",\"repoIds\":[\"" + repo1.id() + "\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.key").exists())
            .andReturn();
        String it2Key = objectMapper.readTree(it2Result.getResponse().getContentAsString())
                .get("data").get("key").asText();
        clearInvocations(git);
        String releaseBranch = "release/" + windowKey;
        when(git.mergeBranch(eq(repo1.cloneUrl()), anyString(), eq("feature/" + it1Key), eq(releaseBranch), anyString()))
                .thenReturn(GitBranchPort.MergeResult.conflict("Fixture conflict").withEvidence(
                        evidence("feature/" + it1Key, releaseBranch, "11")));

        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/attach")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("iterationKeys", java.util.List.of(it1Key, it2Key)))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].iterationKey").value(it1Key))
            .andExpect(jsonPath("$.data[1].iterationKey").value(it2Key));
        verifyNoInteractions(git);

        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/freeze")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/release-windows/" + windowId + "/plan")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isArray());

        String orchestrationRequest = objectMapper.writeValueAsString(Map.of(
                "repoIds", java.util.List.of(repo1.id()), "iterationKeys", java.util.List.of(), "failFast", true));
        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/orchestrate")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content(orchestrationRequest))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(git);
        mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/unfreeze")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/windows/dry-plan").header("Authorization", "Bearer " + token)
                        .param("windowKey", windowKey))
                .andExpect(status().isNotFound());

        MvcResult orch = mockMvc.perform(post("/api/v1/release-windows/" + windowId + "/orchestrate")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(orchestrationRequest))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").exists())
            .andReturn();
        String runId = objectMapper.readTree(orch.getResponse().getContentAsString()).get("data").asText();
        assertThat(runId).isNotBlank();

        mockMvc.perform(get("/api/v1/runs/" + runId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.runType").value("WINDOW_ORCHESTRATION"));

        MvcResult exported = mockMvc.perform(get("/api/v1/runs/" + runId + "/export.json")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode exportJson = objectMapper.readTree(exported.getResponse().getContentAsString());
        assertThat(exportJson.get("runId").asText()).isEqualTo(runId);
        assertThat(exportJson.path("operator").asText()).isEqualTo("admin");
        assertThat(exportJson.path("status").asText()).isEqualTo("FAILED");
        assertThat(exportJson.path("items")).hasSize(2);
        assertThat(exportJson.path("items").get(0).path("finalResult").asText()).isEqualTo("MERGE_BLOCKED");
        assertThat(exportJson.path("items").get(1).path("finalResult").asText()).isEqualTo("SKIPPED_DUE_TO_BLOCK");
        assertThat(exportJson.path("items").get(0).path("metadata").path("git.blockReason").asText()).isEqualTo("CONFLICT");
        verify(git, never()).mergeBranch(eq(repo1.cloneUrl()), anyString(), eq("feature/" + it2Key), anyString(), anyString());

        MvcResult windowReport = mockMvc.perform(get("/api/v1/release-windows/" + windowId + "/report.json")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.windowId").value(windowId))
            .andExpect(jsonPath("$.windowKey").value(windowKey))
            .andExpect(jsonPath("$.runCount").value(1))
            .andExpect(jsonPath("$.itemCount").value(2))
            .andExpect(jsonPath("$.runs[0].runId").value(runId))
            .andReturn();
        JsonNode reportJson = objectMapper.readTree(windowReport.getResponse().getContentAsString());
        assertThat(reportJson.get("runs").get(0).get("items")).hasSize(2);

        MvcResult windowReportCsv = mockMvc.perform(get("/api/v1/release-windows/" + windowId + "/report.csv")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
        String csv = windowReportCsv.getResponse().getContentAsString();
        assertThat(csv).contains("windowId,windowKey,runId,runType,runStatus,repo,iterationKey,finalResult,stepType,stepResult,stepStart,stepEnd,message");
        assertThat(csv).contains(windowId, windowKey, runId);

        MvcResult windowReportMarkdown = mockMvc.perform(get("/api/v1/release-windows/" + windowId + "/report.md")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
        String markdown = windowReportMarkdown.getResponse().getContentAsString();
        assertThat(markdown)
                .contains("# Release Window Report: " + windowKey)
                .contains("| Window ID | " + windowId + " |")
                .contains("## Runs")
                .contains(runId);

        MvcResult windowReportPackage = mockMvc.perform(get("/api/v1/release-windows/" + windowId + "/report.zip")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("release-window-" + windowKey + "-evidence.zip")))
            .andReturn();
        Map<String, byte[]> entries = readZipEntries(windowReportPackage.getResponse().getContentAsByteArray());
        assertThat(entries.keySet()).containsExactly("manifest.txt", "report.json", "report.csv", "report.md");
        assertThat(new String(entries.get("manifest.txt"), StandardCharsets.UTF_8))
                .contains("ReleaseHub 发布窗口证据制品包")
                .contains("windowId=" + windowId)
                .contains("windowKey=" + windowKey)
                .contains("report.json")
                .contains("report.csv")
                .contains("report.md");
        assertThat(objectMapper.readTree(entries.get("report.json")).get("windowId").asText()).isEqualTo(windowId);
        assertThat(new String(entries.get("report.csv"), StandardCharsets.UTF_8)).contains(windowId, windowKey, runId);
        assertThat(new String(entries.get("report.md"), StandardCharsets.UTF_8)).contains("# Release Window Report: " + windowKey);

        String firstItemId = exportJson.path("items").get(0).path("id").asText();
        String skippedItemId = exportJson.path("items").get(1).path("id").asText();
        assertThat(firstItemId).isNotBlank();
        assertThat(skippedItemId).isNotBlank();
        String legacyTriple = windowKey + "::" + repo1.id() + "::" + it1Key;
        for (String invalidSelection : java.util.List.of(legacyTriple, skippedItemId)) {
            mockMvc.perform(post("/api/v1/runs/" + runId + "/retry")
                            .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("items", java.util.List.of(invalidSelection)))))
                    .andExpect(status().isBadRequest());
        }

        when(git.mergeBranch(eq(repo1.cloneUrl()), anyString(), eq("feature/" + it1Key), eq(releaseBranch), anyString()))
                .thenReturn(GitBranchPort.MergeResult.success().withEvidence(evidence("feature/" + it1Key, releaseBranch, "11")));
        when(git.mergeBranch(eq(repo1.cloneUrl()), anyString(), eq("feature/" + it2Key), eq(releaseBranch), anyString()))
                .thenReturn(GitBranchPort.MergeResult.success().withEvidence(evidence("feature/" + it2Key, releaseBranch, "12")));
        MvcResult retry = mockMvc.perform(post("/api/v1/runs/" + runId + "/retry")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("items", java.util.List.of(firstItemId, skippedItemId)))))
                .andExpect(status().isOk()).andReturn();
        String retryRunId = objectMapper.readTree(retry.getResponse().getContentAsString()).path("data").asText();
        assertThat(retryRunId).isNotBlank().isNotEqualTo(runId);
        MvcResult retried = mockMvc.perform(get("/api/v1/runs/" + retryRunId + "/export.json")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        JsonNode retryJson = objectMapper.readTree(retried.getResponse().getContentAsString());
        assertThat(retryJson.path("status").asText()).isEqualTo("DONE");
        assertThat(retryJson.path("operator").asText()).isEqualTo("admin");
        assertThat(retryJson.path("items")).hasSize(2);
        assertThat(retryJson.path("items").get(0).path("metadata").path("retry.sourceItemId").asText()).isEqualTo(firstItemId);
        assertThat(retryJson.path("items").get(1).path("metadata").path("retry.sourceItemId").asText()).isEqualTo(skippedItemId);
        assertThat(retryJson.path("items").get(0).path("executedOrder").asInt()).isEqualTo(1);
        assertThat(retryJson.path("items").get(1).path("executedOrder").asInt()).isEqualTo(2);
        verify(git, times(2)).mergeBranch(eq(repo1.cloneUrl()), anyString(), eq("feature/" + it1Key), eq(releaseBranch), anyString());
        verify(git).mergeBranch(eq(repo1.cloneUrl()), anyString(), eq("feature/" + it2Key), eq(releaseBranch), anyString());    }

    private Map<String, byte[]> readZipEntries(byte[] zipBytes) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            var entry = zip.getNextEntry();
            while (entry != null) {
                entries.put(entry.getName(), zip.readAllBytes());
                zip.closeEntry();
                entry = zip.getNextEntry();
            }
        }
        return entries;
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

    private GitBranchPort.MrEvidence evidence(String source, String target, String iid) {
        return new GitBranchPort.MrEvidence("101", source, target, iid, "https://gitlab.invalid/project/-/merge_requests/" + iid,
                "a".repeat(40), "b".repeat(40), GitBranchPort.MrDisposition.MR_REUSED, null);
    }

    private CreatedRepo createRepo(String token, String groupCode, String suffix) throws Exception {
        String name = "UT-" + suffix + "-" + System.currentTimeMillis();
        String cloneUrl = "https://git.example.com/" + name + ".git";
        String req = "{" +
                "\"name\":\"" + name + "\"," +
                "\"cloneUrl\":\"" + cloneUrl + "\"," +
                "\"groupCode\":\"" + groupCode + "\"," +
                "\"defaultBranch\":\"main\"," +
                "\"gitProvider\":\"GITLAB\"," +
                "\"gitAccessToken\":\"test-token\"" +
                "}";
        MvcResult result = mockMvc.perform(post("/api/v1/repositories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();
        String id = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asText();
        return new CreatedRepo(id, cloneUrl);
    }

    private record CreatedRepo(String id, String cloneUrl) {
    }
}
