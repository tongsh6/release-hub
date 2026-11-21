package io.releasehub.bootstrap.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.releasehub.application.run.RunPort;
import io.releasehub.application.run.plan.RunPlanPort;
import io.releasehub.application.run.plan.SavedRunPlan;
import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.releasewindow.ReleaseWindow;
import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.run.ActionType;
import io.releasehub.domain.run.Run;
import io.releasehub.domain.run.RunExecutionStatus;
import io.releasehub.domain.run.RunItem;
import io.releasehub.domain.run.RunItemResult;
import io.releasehub.domain.run.RunPlanSnapshot;
import io.releasehub.domain.run.RunStep;
import io.releasehub.domain.run.RunType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.jpa.open-in-view=false",
        "spring.datasource.url=jdbc:h2:mem:run-paged-api;MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RunPagedApiTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RunPort runPort;
    @Autowired private RunPlanPort runPlanPort;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private io.releasehub.application.releasewindow.ReleaseWindowPort releaseWindowPort;

    private String token;
    private String operator;

    @BeforeEach
    void setUp() throws Exception {
        operator = "run-api-" + UUID.randomUUID();
        var response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin\"}"))
                .andExpect(status().isOk()).andReturn();
        token = objectMapper.readTree(response.getResponse().getContentAsString()).path("data").path("token").asText();
        assertThat(token).isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(RunExecutionStatus.class)
    void filtersEachPersistedExecutionStatusWithNonEmptyResults(RunExecutionStatus expectedStatus) throws Exception {
        List<Run> fixtures = new ArrayList<>();
        for (RunExecutionStatus state : RunExecutionStatus.values()) {
            fixtures.add(createRun(state, operator, "WK-STATUS", Instant.now()));
        }
        Run expected = fixtures.stream().filter(run -> run.getExecutionStatus() == expectedStatus).findFirst().orElseThrow();

        JsonNode response = page(expectedStatus.name(), null, null, 1, 20);
        assertThat(response.path("data")).hasSize(1);
        assertThat(response.path("page").path("total").asLong()).isEqualTo(1);
        JsonNode actual = response.path("data").get(0);
        assertThat(actual.path("id").asText()).isEqualTo(expected.getId().value());
        assertThat(actual.path("status").asText()).isEqualTo(expectedStatus.name());
        assertThat(actual.path("operator").asText()).isEqualTo(operator);
        assertThat(actual.path("items")).hasSize(expected.getItems().size());
    }

    @Test
    void filtersStatusAndOperatorWithoutReturningOtherOperators() throws Exception {
        Run expected = createRun(RunExecutionStatus.FAILED, operator, "WK-OPERATOR", Instant.now());
        createRun(RunExecutionStatus.FAILED, operator + "-other", "WK-OPERATOR", Instant.now());
        createRun(RunExecutionStatus.DONE, operator, "WK-OPERATOR", Instant.now());

        JsonNode data = page("FAILED", null, null, 1, 20).path("data");
        assertThat(data).hasSize(1);
        assertThat(data.get(0).path("id").asText()).isEqualTo(expected.getId().value());
    }

    @Test
    void filtersStatusAndWindowKeyWithoutReturningOtherWindows() throws Exception {
        String windowKey = "WK-" + UUID.randomUUID();
        Run expected = createRun(RunExecutionStatus.FAILED, operator, windowKey, Instant.now());
        createRun(RunExecutionStatus.FAILED, operator, windowKey + "-other", Instant.now());
        createRun(RunExecutionStatus.DONE, operator, windowKey, Instant.now());

        JsonNode data = page("FAILED", windowKey, null, 1, 20).path("data");
        assertThat(data).hasSize(1);
        assertThat(data.get(0).path("id").asText()).isEqualTo(expected.getId().value());
        assertThat(data.get(0).path("items").get(0).path("windowKey").asText()).isEqualTo(windowKey);
    }

    @Test
    void filtersByReleaseWindowGroup() throws Exception {
        String group = "G-" + UUID.randomUUID();
        String window = "WK-" + UUID.randomUUID();
        Instant now = Instant.now();
        releaseWindowPort.save(ReleaseWindow.createDraft(window, "Selected group", null, now, group, now));
        releaseWindowPort.save(ReleaseWindow.createDraft(window + "-other", "Other group", null, now, group + "-other", now));
        Run expected = createRun(RunExecutionStatus.FAILED, operator, window, now);
        createRun(RunExecutionStatus.FAILED, operator, window + "-other", now);

        JsonNode data = page("FAILED", null, group, 1, 20).path("data");
        assertThat(data).hasSize(1);
        assertThat(data.get(0).path("id").asText()).isEqualTo(expected.getId().value());
    }

    @Test
    void returnsRecentRunsFirst() throws Exception {
        String window = "WK-" + UUID.randomUUID();
        Instant base = Instant.parse("2026-05-23T01:00:00Z");
        Run older = createRun(RunExecutionStatus.DONE, operator, window, base);
        Run newer = createRun(RunExecutionStatus.FAILED, operator, window, base.plusSeconds(60));

        JsonNode data = page(null, window, null, 1, 5).path("data");
        assertThat(data).hasSize(2);
        assertThat(data.get(0).path("id").asText()).isEqualTo(newer.getId().value());
        assertThat(data.get(1).path("id").asText()).isEqualTo(older.getId().value());
    }

    @Test
    void paginatesAnIsolatedNonEmptyResultSet() throws Exception {
        Instant base = Instant.parse("2026-05-23T02:00:00Z");
        for (int i = 0; i < 25; i++) createRun(RunExecutionStatus.DONE, operator, "WK-PAGE", base.plusSeconds(i));

        JsonNode response = page("DONE", null, null, 1, 10);
        JsonNode pagination = response.path("page");
        assertThat(pagination.path("total").asLong()).isEqualTo(25);
        assertThat(pagination.path("totalPages").asInt()).isEqualTo(3);
        assertThat(pagination.path("hasNext").asBoolean()).isTrue();
        assertThat(pagination.path("hasPrevious").asBoolean()).isFalse();
        assertThat(response.path("data")).hasSize(10);
        for (JsonNode run : response.path("data")) assertThat(run.path("status").asText()).isEqualTo("DONE");
    }

    @Test
    void returnsAllRealExecutionStatesWhenNoStatusFilterIsSupplied() throws Exception {
        for (RunExecutionStatus state : RunExecutionStatus.values()) createRun(state, operator, "WK-ALL", Instant.now());

        JsonNode response = page(null, null, null, 1, 20);
        List<String> actual = new ArrayList<>();
        response.path("data").forEach(run -> actual.add(run.path("status").asText()));
        assertThat(actual).containsExactlyInAnyOrderElementsOf(
                java.util.Arrays.stream(RunExecutionStatus.values()).map(Enum::name).toList());
        assertThat(response.path("page").path("total").asLong()).isEqualTo(RunExecutionStatus.values().length);
    }

    @Test
    void loadsRealJpaItemsStepsAndMetadataWithoutOsivOrAnOuterTransaction() {
        String window = "WK-DETACHED-" + UUID.randomUUID();
        Run persisted = createRun(RunExecutionStatus.DONE, operator, window, Instant.now());
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from run_item where run_id = ?", Integer.class,
                persisted.getId().value())).isEqualTo(1);

        assertMaterialized(runPort.findById(persisted.getId().value()).orElseThrow(), persisted);
        assertMaterialized(runPort.findAll().stream().filter(run -> run.getId().equals(persisted.getId())).findFirst().orElseThrow(), persisted);
        assertMaterialized(runPort.findByWindowKey(window).getFirst(), persisted);
        assertMaterialized(runPort.findPaged(null, operator, window, null, null, "DONE", null, 1, 10).items().getFirst(), persisted);
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    @Test
    void planEndpointReportsPreviewAndCurrentExecutionStateWithoutPermittingExecution() throws Exception {
        Run preview = createRun(RunExecutionStatus.PLANNED, operator, "WK-PREVIEW", Instant.now());
        saveSnapshot(preview);
        assertThat(plan(preview).path("status").asText()).isEqualTo("PLANNED");

        Run execution = createRun(RunExecutionStatus.RUNNING, operator, "WK-EXECUTING", Instant.now());
        saveSnapshot(execution);
        JsonNode running = plan(execution);
        assertThat(running.path("status").asText()).isEqualTo("RUNNING");
        assertThat(running.path("executionPermitted").asBoolean()).isFalse();

        RunItem item = execution.getItems().getFirst();
        finishItem(item, RunItemResult.MERGED, Instant.now());
        execution.finish(Instant.now());
        runPort.save(execution);
        JsonNode done = plan(execution);
        assertThat(done.path("status").asText()).isEqualTo("DONE");
        assertThat(done.path("executionVersion").asInt()).isEqualTo(execution.getExecutionVersion());
        assertThat(done.path("executionPermitted").asBoolean()).isFalse();
        assertThat(done.path("snapshot")).isEqualTo(running.path("snapshot"));
    }

    @Test
    void appliesV37OnTheIsolatedH2Database() {
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where version = '37' and success = true",
                Integer.class)).isEqualTo(1);
    }

    private JsonNode page(String statusFilter, String windowKey, String groupCode, int page, int size) throws Exception {
        var request = get("/api/v1/runs/paged").header("Authorization", "Bearer " + token)
                .param("operator", operator).param("page", Integer.toString(page)).param("size", Integer.toString(size));
        if (statusFilter != null) request.param("status", statusFilter);
        if (windowKey != null) request.param("windowKey", windowKey);
        if (groupCode != null) request.param("groupCode", groupCode);
        var response = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        JsonNode body = objectMapper.readTree(response.getResponse().getContentAsString());
        assertThat(body.path("success").asBoolean()).isTrue();
        return body;
    }

    private JsonNode plan(Run run) throws Exception {
        var response = mockMvc.perform(get("/api/v1/runs/{id}/plan", run.getId().value())
                        .header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn();
        JsonNode data = objectMapper.readTree(response.getResponse().getContentAsString()).path("data");
        assertThat(data.path("executionPermitted").asBoolean()).isFalse();
        return data;
    }

    private void saveSnapshot(Run run) {
        RunItem item = run.getItems().getFirst();
        var planned = new RunPlanSnapshot.Item(item.getId().value(), item.getPlannedOrder(), item.getRepo().value(),
                "Persisted repository", item.getIterationKey().value(), "GITLAB", "main", "feature/fixture", "AUTO",
                "release/fixture", null, null, null, List.of("ENSURE_FEATURE", "ENSURE_RELEASE", "ENSURE_MR", "TRY_MERGE"));
        var snapshot = new RunPlanSnapshot(item.getWindowKey(), "G001", "DRAFT", false, true, List.of(planned));
        new TransactionTemplate(transactions).executeWithoutResult(tx -> runPlanPort.save(new SavedRunPlan(
                run.getId().value(), "window-" + run.getId().value(), run.getOperator(), UUID.randomUUID().toString(),
                "request-hash", "plan-hash", run.getStartedAt(), snapshot)));
    }

    private void assertMaterialized(Run actual, Run expected) {
        assertThat(actual.getId()).isEqualTo(expected.getId());
        assertThat(actual.getExecutionStatus()).isEqualTo(RunExecutionStatus.DONE);
        assertThat(actual.getItems()).hasSize(1);
        RunItem item = actual.getItems().getFirst();
        assertThat(item.getFinalResult()).isEqualTo(RunItemResult.MERGED);
        assertThat(item.getMetadata()).containsEntry("test.fixture", "persisted");
        assertThat(item.getSteps()).hasSize(1);
        assertThat(item.getSteps().getFirst().actionType()).isEqualTo(ActionType.TRY_MERGE);
        assertThat(item.getSteps().getFirst().result()).isEqualTo(RunItemResult.MERGED);
    }

    private Run createRun(RunExecutionStatus state, String actor, String windowKey, Instant now) {
        Run run = state == RunExecutionStatus.PLANNED ? Run.preview(actor, now)
                : Run.start(RunType.WINDOW_ORCHESTRATION, actor, now);
        RunItem item = RunItem.create(windowKey, RepoId.of("repo-primary"), IterationKey.of("IT-" + UUID.randomUUID()), 1, now);
        item.putMetadata("test.fixture", "persisted");
        run.addItem(item);
        switch (state) {
            case DONE -> finishItem(item, RunItemResult.MERGED, now);
            case FAILED -> finishItem(item, RunItemResult.FAILED, now);
            case NEEDS_REVIEW -> finishItem(item, RunItemResult.UNKNOWN, now);
            case PARTIAL_FAILED -> {
                finishItem(item, RunItemResult.MERGED, now);
                RunItem blocked = RunItem.create(windowKey, RepoId.of("repo-blocked"), item.getIterationKey(), 2, now);
                finishItem(blocked, RunItemResult.MERGE_BLOCKED, now);
                run.addItem(blocked);
            }
            case PLANNED, RUNNING -> { }
        }
        if (state != RunExecutionStatus.PLANNED && state != RunExecutionStatus.RUNNING) run.finish(now.plusSeconds(2));
        assertThat(run.getExecutionStatus()).isEqualTo(state);
        runPort.save(run);
        return run;
    }

    private void finishItem(RunItem item, RunItemResult result, Instant now) {
        item.setExecutedOrder(item.getPlannedOrder());
        item.addStep(new RunStep(ActionType.TRY_MERGE, result, now, now.plusSeconds(1), "Persisted fixture"));
        item.finishWith(result, now.plusSeconds(1));
    }
}
