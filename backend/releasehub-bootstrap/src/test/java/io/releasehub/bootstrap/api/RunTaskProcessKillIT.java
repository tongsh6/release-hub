package io.releasehub.bootstrap.api;

import io.releasehub.application.gitoperation.GitOperationJournalPort;
import io.releasehub.application.iteration.IterationPort;
import io.releasehub.application.iteration.IterationRepoPort;
import io.releasehub.application.releasewindow.ReleaseWindowPort;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.application.run.RunAppService;
import io.releasehub.application.run.RunPort;
import io.releasehub.application.settings.SettingsPort;
import io.releasehub.application.window.WindowIterationPort;
import io.releasehub.bootstrap.ReleaseHubApplication;
import io.releasehub.domain.iteration.BranchCreationMode;
import io.releasehub.domain.iteration.Iteration;
import io.releasehub.domain.iteration.IterationKey;
import io.releasehub.domain.iteration.IterationStatus;
import io.releasehub.domain.releasewindow.ReleaseWindow;
import io.releasehub.domain.releasewindow.ReleaseWindowId;
import io.releasehub.domain.releasewindow.ReleaseWindowStatus;
import io.releasehub.domain.repo.CodeRepository;
import io.releasehub.domain.repo.GitProvider;
import io.releasehub.domain.repo.RepoId;
import io.releasehub.domain.repo.RepoType;
import io.releasehub.domain.run.ActionType;
import io.releasehub.domain.run.RunExecutionStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/** 独立 JVM + PostgreSQL/HTTP：原任务继续、无写意图接管及已确认结果投影；不重放未知写。 */
class RunTaskProcessKillIT {
    private static final String PROJECT_ID = "101";
    private static final String ITERATION = "ITER-C2-RESTART";
    private static final String WINDOW_KEY = "RW-C2-RESTART";
    private static final String SOURCE = "feature/" + ITERATION;
    private static final String TARGET = "release/" + WINDOW_KEY;
    private static final String SOURCE_SHA = "a".repeat(40);
    private static final String ACTOR = "process-kill-fixture";
    private static final String INTENT = "after-mr-before-merge";
    @TempDir Path temporary;

    @ParameterizedTest(name = "claimedBeforeCrash={0}")
    @ValueSource(booleans = {false, true})
    @Timeout(180)
    void restartingAfterCommittedMrContinuesTheOriginalMerge(boolean claimedBeforeCrash) throws Exception {
        GitLabHttpFixture server = new GitLabHttpFixture();
        Process first = null;
        Process restarted = null;
        try (var db = new PostgreSQLContainer<>("postgres:18.1")
                .withDatabaseName("process_kill_test").withUsername("process_test").withPassword("isolated-process-db")) {
            db.start();
            server.start();
            routes(server);
            String windowId = UUID.randomUUID().toString();
            String repoId = UUID.randomUUID().toString();
            Path marker = temporary.resolve("checkpoint.txt");
            Path receipt = temporary.resolve("receipt.txt");
            Path firstLog = temporary.resolve("first.log");
            first = launch(claimedBeforeCrash ? "first-claimed" : "first", db, server, windowId, repoId, marker, receipt, firstLog);
            awaitCheckpoint(first, marker, firstLog);

            JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                    db.getJdbcUrl(), db.getUsername(), db.getPassword()));
            String runId = Files.readString(marker).trim();
            var before = tasks(jdbc, runId);
            var originalItemIds = jdbc.queryForList("select id from run_item where run_id=? order by id", String.class, runId);
            var originalSteps = jdbc.queryForList("select s.* from run_step s join run_task t on t.id=s.task_id "
                    + "where t.run_id=? order by t.task_order", runId);
            assertThat(originalItemIds).hasSize(1);
            assertThat(originalSteps).hasSize(3);
            assertThat(before).extracting(row -> row.get("status"))
                    .containsExactly("COMPLETED", "COMPLETED", "COMPLETED", claimedBeforeCrash ? "RUNNING" : "PENDING");
            assertThat(jdbc.queryForObject("select count(*) from git_operation where task_id=?", Integer.class,
                    before.getLast().get("id"))).isZero();
            if (claimedBeforeCrash) {
                assertThat(before.getLast().get("claim_token")).isNotNull();
                assertThat(before.getLast().get("started_at")).isNotNull();
            }
            assertThat(metadata(jdbc, runId)).containsEntry("git.projectId", PROJECT_ID)
                    .containsEntry("git.mrIid", "7").containsEntry("git.sourceSha", SOURCE_SHA)
                    .containsEntry("git.sourceBranch", SOURCE).containsEntry("git.targetBranch", TARGET);
            assertThat(jdbc.queryForObject("select execution_status from run where id=?", String.class, runId))
                    .isEqualTo("RUNNING");
            server.verify("POST", "/api/v4/projects/" + PROJECT_ID + "/merge_requests", 1);
            assertThat(server.writeCount()).isEqualTo(1);

            long killedPid = first.pid();
            first.destroyForcibly();
            assertThat(first.waitFor(10, TimeUnit.SECONDS)).isTrue();
            assertThat(first.exitValue()).isNotZero();
            int requestsBeforeRestart = server.requests.size();

            Path restartedLog = temporary.resolve("restarted.log");
            restarted = launch("resume", db, server, windowId, repoId, marker, receipt, restartedLog);
            boolean finished = restarted.waitFor(55, TimeUnit.SECONDS);
            assertThat(finished).withFailMessage("Resume JVM did not finish: %s", tail(restartedLog)).isTrue();
            assertThat(restarted.exitValue()).withFailMessage("Resume JVM failed: %s", tail(restartedLog)).isZero();
            assertThat(Files.readString(receipt).trim()).isEqualTo(runId);
            var after = tasks(jdbc, runId);
            assertThat(jdbc.queryForList("select id from run_item where run_id=? order by id", String.class, runId))
                    .isEqualTo(originalItemIds);
            var completedSteps = jdbc.queryForList("select s.* from run_step s join run_task t on t.id=s.task_id "
                    + "where t.run_id=? order by t.task_order", runId);
            assertThat(completedSteps).hasSize(4);
            assertThat(completedSteps.subList(0, 3)).isEqualTo(originalSteps);
            assertThat(completedSteps.getLast().get("task_id")).isEqualTo(before.getLast().get("id"));
            assertThat(after.subList(0, 3)).isEqualTo(before.subList(0, 3));
            assertThat(after).extracting(row -> row.get("status"))
                    .containsExactly("COMPLETED", "COMPLETED", "COMPLETED", "COMPLETED");
            assertThat(after.getLast().get("id")).isEqualTo(before.getLast().get("id"));
            if (claimedBeforeCrash) {
                assertThat(after.getLast().get("claim_token")).isNotEqualTo(before.getLast().get("claim_token"));
                assertThat(after.getLast().get("started_at")).isEqualTo(before.getLast().get("started_at"));
                assertThat(before.getLast().get("claim_expires_at")).isNotNull();
                assertThat(after.getLast().get("claim_expires_at")).isNotNull()
                        .isNotEqualTo(before.getLast().get("claim_expires_at"));
            }
            assertThat(jdbc.queryForObject("select execution_status from run where id=?", String.class, runId))
                    .isEqualTo("DONE");
            assertThat(metadata(jdbc, runId)).containsEntry("git.projectId", PROJECT_ID)
                    .containsEntry("git.mrIid", "7").containsEntry("git.sourceSha", SOURCE_SHA);
            assertThat(jdbc.queryForObject("select count(*) from run where id=?", Integer.class, runId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from run", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from run_step where run_item_id in "
                    + "(select id from run_item where run_id=?)", Integer.class, runId)).isEqualTo(4);
            server.verify("POST", "/api/v4/projects/" + PROJECT_ID + "/merge_requests", 1);
            String mergePath = "/api/v4/projects/" + PROJECT_ID + "/merge_requests/7/merge";
            server.verify("PUT", mergePath, 1);
            assertThat(server.body("PUT", mergePath).path("sha").asText()).isEqualTo(SOURCE_SHA);
            assertThat(server.writeCount()).isEqualTo(2);
            assertThat(server.requests.subList(requestsBeforeRestart, server.requests.size()))
                    .noneMatch(request -> request.method().equals("POST") || request.internalHeaders());
            System.out.printf("PROCESS_%s_RESUME run=%s killedPid=%d exit=%d resumedPid=%d "
                    + "mrPost=1 mergePut=1 preservedPrefix=3 tasks=COMPLETED,COMPLETED,COMPLETED,COMPLETED%n",
                    claimedBeforeCrash ? "CLAIMED_UNWRITTEN" : "PENDING", runId, killedPid, first.exitValue(), restarted.pid());
        } finally {
            stopOwnedProcess(first);
            stopOwnedProcess(restarted);
            server.stop();
        }
    }

    @ParameterizedTest(name = "appliedAction={0}")
    @EnumSource(value = ActionType.class, names = {"ENSURE_FEATURE", "ENSURE_MR", "TRY_MERGE"})
    @Timeout(180)
    void restartingAfterAppliedWriteProjectsWithoutAnotherHttpRequest(ActionType appliedAction) throws Exception {
        boolean branchCreation = appliedAction == ActionType.ENSURE_FEATURE;
        boolean mergeAction = appliedAction == ActionType.TRY_MERGE;
        GitLabHttpFixture server = new GitLabHttpFixture();
        Process first = null;
        Process restarted = null;
        int taskIndex = branchCreation ? 0 : mergeAction ? 3 : 2;
        String caseName = branchCreation ? "branch" : mergeAction ? "merge" : "mr";
        try (var db = new PostgreSQLContainer<>("postgres:18.1")
                .withDatabaseName("applied_write_restart").withUsername("process_test").withPassword("isolated-process-db")) {
            db.start();
            server.start();
            routes(server);
            String root = "/api/v4/projects/" + PROJECT_ID;
            String featurePath = root + "/repository/branches/" + URLEncoder.encode(SOURCE, StandardCharsets.UTF_8);
            if (branchCreation) {
                server.reply("GET", featurePath, 404, "{}");
                server.reply("GET", root + "/repository/branches/main", 200,
                        "{\"name\":\"main\",\"commit\":{\"id\":\"" + SOURCE_SHA + "\"}}");
                server.reply("GET", root + "/repository/commits/main", 200, "{\"id\":\"" + SOURCE_SHA + "\"}");
                server.reply("POST", root + "/repository/branches", 201,
                        "{\"name\":\"" + SOURCE + "\",\"commit\":{\"id\":\"" + SOURCE_SHA + "\"}}");
            }
            String windowId = UUID.randomUUID().toString();
            String repoId = UUID.randomUUID().toString();
            Path marker = temporary.resolve("applied-checkpoint.txt");
            Path projected = marker.resolveSibling("projection-committed.txt");
            Path release = marker.resolveSibling("projection-release.txt");
            Path receipt = temporary.resolve("applied-receipt.txt");
            Path firstLog = temporary.resolve("applied-first.log");
            first = launch("first-applied-" + caseName, db, server, windowId, repoId, marker, receipt, firstLog);
            awaitCheckpoint(first, marker, firstLog);

            JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                    db.getJdbcUrl(), db.getUsername(), db.getPassword()));
            String runId = Files.readString(marker).trim();
            var before = tasks(jdbc, runId);
            var originalTask = before.get(taskIndex);
            var originalItemIds = jdbc.queryForList("select id from run_item where run_id=? order by id", String.class, runId);
            var originalSteps = jdbc.queryForList("select s.* from run_step s join run_task t on t.id=s.task_id "
                    + "where t.run_id=? order by t.task_order", runId);
            var operations = jdbc.queryForList("select * from git_operation where run_id=? order by id", runId);
            var events = jdbc.queryForList("select e.* from git_operation_event e join git_operation o on o.id=e.operation_id "
                    + "where o.run_id=? order by o.id,e.event_sequence", runId);
            assertThat(before).extracting(row -> row.get("status")).containsExactlyElementsOf(branchCreation
                    ? List.of("RUNNING", "PENDING", "PENDING", "PENDING")
                    : mergeAction ? List.of("COMPLETED", "COMPLETED", "COMPLETED", "RUNNING")
                    : List.of("COMPLETED", "COMPLETED", "RUNNING", "PENDING"));
            assertThat(originalSteps).hasSize(taskIndex);
            assertThat(originalItemIds).hasSize(1);
            assertThat(operations).hasSize(mergeAction ? 2 : 1);
            var appliedOperation = operations.stream()
                    .filter(row -> originalTask.get("id").equals(row.get("task_id"))).findFirst().orElseThrow();
            assertThat(appliedOperation).containsEntry("state", "APPLIED")
                    .containsEntry("action", branchCreation ? "CREATE_BRANCH" : mergeAction ? "MERGE_MR" : "CREATE_MR")
                    .containsEntry("task_id", originalTask.get("id"))
                    .containsEntry("claim_token", originalTask.get("claim_token"));
            assertThat(events).hasSize(mergeAction ? 6 : 3);
            assertThat(events.getLast()).containsEntry("event_type", "APPLIED");
            String remoteKey = appliedOperation.get("remote_key").toString();
            var guard = jdbc.queryForMap("select * from repo_execution_guard where remote_key=?", remoteKey);
            assertThat(guard.get("operation_id")).isEqualTo(appliedOperation.get("id"));
            assertThat(jdbc.queryForObject("select count(*) from run_step where task_id=?", Integer.class,
                    originalTask.get("id"))).isZero();
            var originalMetadata = metadata(jdbc, runId);
            if (appliedAction == ActionType.ENSURE_MR) {
                assertThat(originalMetadata).doesNotContainKeys("git.mrIid", "git.mrUrl", "git.sourceSha");
            }
            if (mergeAction) {
                assertThat(originalMetadata).containsEntry("git.mrIid", "7").containsEntry("git.sourceSha", SOURCE_SHA)
                        .containsEntry("git.mrDisposition", "CREATED").doesNotContainKey("git.mergeSha");
                assertThat(jdbc.queryForObject("select last_merge_at from window_iteration where window_id=? and iteration_key=?",
                        java.sql.Timestamp.class, windowId, ITERATION)).isNull();
                server.verify("PUT", root + "/merge_requests/7/merge", 1);
            }
            server.verify("POST", root + (branchCreation ? "/repository/branches" : "/merge_requests"), 1);
            assertThat(server.writeCount()).isEqualTo(mergeAction ? 2 : 1);

            long killedPid = first.pid();
            first.destroyForcibly();
            assertThat(first.waitFor(10, TimeUnit.SECONDS)).isTrue();
            assertThat(first.exitValue()).isNotZero();
            if (branchCreation) {
                server.reply("GET", featurePath, 200,
                        "{\"name\":\"" + SOURCE + "\",\"commit\":{\"id\":\"" + SOURCE_SHA + "\"}}");
            }
            if (mergeAction) {
                // Historical success must remain recoverable after the remote MR disappears or the target resets.
                server.reply("GET", root + "/merge_requests/7", 404, "{}");
                server.reply("GET", root + "/repository/branches/" + URLEncoder.encode(TARGET, StandardCharsets.UTF_8),
                        200, "{\"name\":\"" + TARGET + "\",\"commit\":{\"id\":\"" + "d".repeat(40) + "\"}}");
            }
            int requestsBeforeRestart = server.requests.size();
            Instant recoveryStarted = Instant.now();
            Path restartedLog = temporary.resolve("applied-restarted.log");
            restarted = launch("resume-applied-" + caseName, db, server, windowId, repoId, marker, receipt, restartedLog);
            System.out.printf("PROCESS_APPLIED_%s_CHECKPOINT run=%s task=%s killedPid=%d exit=%d resumedPid=%d "
                    + "operations=%d events=%d noPendingAtKill=%s%n",
                    branchCreation ? "BRANCH" : mergeAction ? "MERGE" : "MR", runId, originalTask.get("id"),
                    killedPid, first.exitValue(), restarted.pid(), operations.size(), events.size(),
                    before.stream().noneMatch(row -> "PENDING".equals(row.get("status"))));
            awaitCheckpoint(restarted, projected, restartedLog);

            // The proxy pauses after the projection transaction commits and before the next task can run.
            assertThat(Files.readString(projected).trim()).isEqualTo(runId);
            assertThat(server.requests).hasSize(requestsBeforeRestart);
            var after = tasks(jdbc, runId);
            assertThat(after.get(taskIndex)).containsEntry("status", "COMPLETED");
            for (String field : List.of("id", "task_type", "claim_token", "started_at", "claim_expires_at")) {
                assertThat(after.get(taskIndex).get(field)).as("preserve original task %s", field)
                        .isEqualTo(originalTask.get(field));
            }
            assertThat(after.subList(0, taskIndex)).isEqualTo(before.subList(0, taskIndex));
            assertThat(after.subList(taskIndex + 1, 4)).isEqualTo(before.subList(taskIndex + 1, 4));
            assertThat(jdbc.queryForList("select id from run_item where run_id=? order by id", String.class, runId))
                    .isEqualTo(originalItemIds);
            var projectedSteps = jdbc.queryForList("select s.* from run_step s join run_task t on t.id=s.task_id "
                    + "where t.run_id=? order by t.task_order", runId);
            assertThat(projectedSteps).hasSize(taskIndex + 1);
            assertThat(projectedSteps.subList(0, taskIndex)).isEqualTo(originalSteps);
            assertThat(jdbc.queryForList("select * from git_operation where run_id=? order by id", runId)).isEqualTo(operations);
            assertThat(jdbc.queryForList("select e.* from git_operation_event e join git_operation o on o.id=e.operation_id "
                    + "where o.run_id=? order by o.id,e.event_sequence", runId)).isEqualTo(events);
            var step = jdbc.queryForMap("select * from run_step where task_id=?", originalTask.get("id"));
            assertThat(step).containsEntry("action_type", appliedAction.name())
                    .containsEntry("result", mergeAction ? "MERGED" : "CREATED");
            Instant stepStart = jdbc.queryForObject("select start_at from run_step where task_id=?",
                    java.sql.Timestamp.class, originalTask.get("id")).toInstant();
            assertThat(stepStart).isAfterOrEqualTo(recoveryStarted);
            assertThat(metadata(jdbc, runId)).containsEntry("git.projectId", PROJECT_ID);
            if (appliedAction == ActionType.ENSURE_MR) {
                assertThat(metadata(jdbc, runId)).containsEntry("git.sourceBranch", SOURCE)
                        .containsEntry("git.targetBranch", TARGET).containsEntry("git.sourceSha", SOURCE_SHA)
                        .containsEntry("git.mrIid", "7").containsEntry("git.mrDisposition", "CREATED")
                        .containsEntry("git.mrUrl", server.baseUrl() + "/acme/restart/-/merge_requests/7")
                        .doesNotContainKeys("git.mergeSha", "git.blockReason", "git.requestId");
            }
            if (mergeAction) {
                var expectedMetadata = new java.util.LinkedHashMap<>(originalMetadata);
                expectedMetadata.put("git.mergeSha", "c".repeat(40));
                expectedMetadata.remove("git.blockReason");
                expectedMetadata.remove("git.requestId");
                assertThat(metadata(jdbc, runId)).isEqualTo(expectedMetadata);
                assertThat(jdbc.queryForObject("select final_result from run_item where run_id=?", String.class, runId))
                        .isEqualTo("MERGED");
                assertThat(jdbc.queryForObject("select execution_status from run where id=?", String.class, runId)).isEqualTo("DONE");
                assertThat(jdbc.queryForObject("select finished_at from run where id=?", java.sql.Timestamp.class, runId))
                        .isNotNull();
                assertThat(jdbc.queryForObject("select last_merge_at from window_iteration where window_id=? and iteration_key=?",
                        java.sql.Timestamp.class, windowId, ITERATION).toInstant()).isAfterOrEqualTo(recoveryStarted);
            }
            var releasedGuard = jdbc.queryForMap("select * from repo_execution_guard where remote_key=?", remoteKey);
            assertThat(releasedGuard).containsEntry("generation", guard.get("generation"))
                    .containsEntry("session_id", null).containsEntry("operation_id", null).containsEntry("reason", null);

            Files.writeString(release, "continue");
            assertThat(restarted.waitFor(55, TimeUnit.SECONDS))
                    .withFailMessage("Resume JVM did not finish after applied projection: %s", tail(restartedLog)).isTrue();
            assertThat(restarted.exitValue()).withFailMessage("Resume JVM failed: %s", tail(restartedLog)).isZero();
            assertThat(Files.readString(receipt).trim()).isEqualTo(runId);
            assertThat(tasks(jdbc, runId)).extracting(row -> row.get("status"))
                    .containsExactly("COMPLETED", "COMPLETED", "COMPLETED", "COMPLETED");
            assertThat(jdbc.queryForObject("select execution_status from run where id=?", String.class, runId)).isEqualTo("DONE");
            assertThat(jdbc.queryForObject("select count(*) from run", Integer.class)).isEqualTo(1);
            server.verify("POST", root + "/repository/branches", branchCreation ? 1 : 0);
            server.verify("POST", root + "/merge_requests", 1);
            server.verify("PUT", root + "/merge_requests/7/merge", 1);
            assertThat(server.body("PUT", root + "/merge_requests/7/merge").path("sha").asText()).isEqualTo(SOURCE_SHA);
            assertThat(server.writeCount()).isEqualTo(branchCreation ? 3 : 2);
            System.out.printf("PROCESS_APPLIED_%s_RESUME run=%s killedPid=%d exit=%d resumedPid=%d "
                    + "projectionHttp=0 branchPost=%d mrPost=1 mergePut=1 preservedToken=true preservedEvents=%d noPendingAtKill=%s%n",
                    branchCreation ? "BRANCH" : mergeAction ? "MERGE" : "MR", runId, killedPid, first.exitValue(), restarted.pid(),
                    branchCreation ? 1 : 0, events.size(), mergeAction);
        } finally {
            stopOwnedProcess(first);
            stopOwnedProcess(restarted);
            server.stop();
        }
    }

    @ParameterizedTest(name = "crashAgainAfterApplied={0}")
    @ValueSource(booleans = {false, true})
    @Timeout(240)
    void mergeIntentRestartUsesOriginalOperationAndCurrentSender(boolean crashAgainAfterApplied) throws Exception {
        GitLabHttpFixture server = new GitLabHttpFixture();
        Process first = null;
        Process resumed = null;
        Process projected = null;
        try (var db = new PostgreSQLContainer<>("postgres:18.1")
                .withDatabaseName("merge_intent_restart").withUsername("process_test").withPassword("isolated-process-db")) {
            db.start();
            server.start();
            routes(server);
            String root = "/api/v4/projects/" + PROJECT_ID;
            String windowId = UUID.randomUUID().toString();
            String repoId = UUID.randomUUID().toString();
            Path marker = temporary.resolve("merge-intent-checkpoint.txt");
            Path receipt = temporary.resolve("merge-intent-receipt.txt");
            Path firstLog = temporary.resolve("merge-intent-first.log");
            first = launch("first-intent-merge", db, server, windowId, repoId, marker, receipt, firstLog);
            awaitCheckpoint(first, marker, firstLog);

            JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                    db.getJdbcUrl(), db.getUsername(), db.getPassword()));
            String runId = Files.readString(marker).trim();
            var before = tasks(jdbc, runId);
            var originalTask = before.getLast();
            String taskId = originalTask.get("id").toString();
            var originalMetadata = metadata(jdbc, runId);
            var originalSteps = jdbc.queryForList("select s.* from run_step s join run_task t on t.id=s.task_id "
                    + "where t.run_id=? order by t.task_order", runId);
            var originalOperation = jdbc.queryForMap("select * from git_operation where task_id=?", taskId);
            String operationId = originalOperation.get("id").toString();
            var originalEvents = mergeEvents(jdbc, operationId);
            var originalGuard = jdbc.queryForMap("select * from repo_execution_guard where remote_key=?",
                    originalOperation.get("remote_key"));
            int executedOrder = jdbc.queryForObject("select executed_order from run_item where run_id=?", Integer.class, runId);
            assertThat(before).extracting(row -> row.get("status"))
                    .containsExactly("COMPLETED", "COMPLETED", "COMPLETED", "RUNNING");
            assertThat(originalSteps).hasSize(3);
            assertThat(executedOrder).isPositive();
            assertThat(originalTask.get("claim_token")).isNotNull();
            assertThat(originalTask.get("started_at")).isNotNull();
            assertThat(originalTask.get("claim_expires_at")).isNotNull();
            assertThat(originalOperation).containsEntry("state", "INTENT_RECORDED")
                    .containsEntry("action", "MERGE_MR").containsEntry("claim_token", originalTask.get("claim_token"))
                    .containsEntry("session_id", originalTask.get("claim_token"));
            assertThat(originalEvents).singleElement().satisfies(event -> assertThat(event)
                    .containsEntry("event_type", "INTENT_RECORDED").containsEntry("event_sequence", 0)
                    .containsEntry("attempt_id", originalOperation.get("attempt_id")));
            assertThat(originalGuard).containsEntry("operation_id", operationId)
                    .containsEntry("session_id", originalTask.get("claim_token")).containsEntry("reason", "INTENT_PENDING");
            assertThat(originalMetadata).containsEntry("git.mrIid", "7").containsEntry("git.sourceSha", SOURCE_SHA)
                    .containsEntry("git.mrDisposition", "CREATED").doesNotContainKey("git.mergeSha");
            server.verify("POST", root + "/merge_requests", 1);
            server.verify("PUT", root + "/merge_requests/7/merge", 0);
            assertThat(server.writeCount()).isEqualTo(1);

            long firstPid = first.pid();
            first.destroyForcibly();
            assertThat(first.waitFor(10, TimeUnit.SECONDS)).isTrue();
            assertThat(first.exitValue()).isEqualTo(137);
            Path resumedLog = temporary.resolve("merge-intent-resumed.log");
            Instant resultProcessingStarted = Instant.now();
            resumed = launch(crashAgainAfterApplied ? "resume-intent-applied-merge" : "resume",
                    db, server, windowId, repoId, marker, receipt, resumedLog);
            System.out.printf("PROCESS_MERGE_INTENT_CHECKPOINT run=%s task=%s operation=%s attempt=%s killedPid=%d "
                            + "exit=%d resumedPid=%d events=%d noPendingAtKill=true crashAgain=%s%n",
                    runId, taskId, operationId, originalOperation.get("attempt_id"), firstPid, first.exitValue(),
                    resumed.pid(), originalEvents.size(), crashAgainAfterApplied);

            int projectionRequests = -1;
            if (crashAgainAfterApplied) {
                Path appliedMarker = marker.resolveSibling("resumed-merge-applied.txt");
                awaitCheckpoint(resumed, appliedMarker, resumedLog);
                assertThat(Files.readString(appliedMarker).trim()).isEqualTo(runId);
                var appliedTask = tasks(jdbc, runId).getLast();
                var appliedOperation = jdbc.queryForMap("select * from git_operation where task_id=?", taskId);
                var appliedEvents = mergeEvents(jdbc, operationId);
                var appliedGuard = jdbc.queryForMap("select * from repo_execution_guard where remote_key=?",
                        originalOperation.get("remote_key"));
                assertThat(appliedTask).containsEntry("status", "RUNNING");
                assertThat(appliedTask.get("claim_token")).isNotEqualTo(originalTask.get("claim_token"));
                assertThat(appliedOperation).containsEntry("state", "APPLIED");
                assertThat(operationProvenance(appliedOperation)).isEqualTo(operationProvenance(originalOperation));
                assertThat(appliedGuard).containsEntry("session_id", originalGuard.get("session_id"))
                        .containsEntry("operation_id", operationId).containsEntry("generation", originalGuard.get("generation"))
                        .containsEntry("reason", null);
                assertThat(jdbc.queryForObject("select count(*) from run_step where task_id=?", Integer.class, taskId)).isZero();
                assertSenderEvidence(appliedEvents, originalOperation, taskId, appliedTask.get("claim_token").toString());
                server.verify("PUT", root + "/merge_requests/7/merge", 1);
                long resumedPid = resumed.pid();
                resumed.destroyForcibly();
                assertThat(resumed.waitFor(10, TimeUnit.SECONDS)).isTrue();
                assertThat(resumed.exitValue()).isEqualTo(137);
                projectionRequests = server.requests.size();
                server.reply("GET", root + "/merge_requests/7", 404, "{}");
                Path projectedLog = temporary.resolve("merge-intent-projected.log");
                resultProcessingStarted = Instant.now();
                projected = launch("resume-applied-merge", db, server, windowId, repoId, marker, receipt, projectedLog);
                System.out.printf("PROCESS_MERGE_INTENT_APPLIED_CHECKPOINT run=%s task=%s operation=%s killedPid=%d "
                                + "exit=%d resumedPid=%d senderChanged=true mergePut=1 events=%d%n",
                        runId, taskId, operationId, resumedPid, resumed.exitValue(), projected.pid(), appliedEvents.size());
                Path projectedMarker = marker.resolveSibling("projection-committed.txt");
                awaitCheckpoint(projected, projectedMarker, projectedLog);
                assertThat(Files.readString(projectedMarker).trim()).isEqualTo(runId);
                assertThat(server.requests).hasSize(projectionRequests);
                var projectedTask = tasks(jdbc, runId).getLast();
                for (String field : List.of("id", "task_type", "claim_token", "started_at", "claim_expires_at")) {
                    assertThat(projectedTask.get(field)).as("projection preserves current task %s", field)
                            .isEqualTo(appliedTask.get(field));
                }
                assertThat(jdbc.queryForMap("select * from git_operation where task_id=?", taskId)).isEqualTo(appliedOperation);
                assertThat(mergeEvents(jdbc, operationId)).isEqualTo(appliedEvents);
                Files.writeString(marker.resolveSibling("projection-release.txt"), "continue");
                assertThat(projected.waitFor(55, TimeUnit.SECONDS))
                        .withFailMessage("Projection JVM did not finish: %s", tail(projectedLog)).isTrue();
                assertThat(projected.exitValue()).withFailMessage("Projection JVM failed: %s", tail(projectedLog)).isZero();
                assertThat(server.requests).hasSize(projectionRequests);
            } else {
                assertThat(resumed.waitFor(55, TimeUnit.SECONDS))
                        .withFailMessage("Intent resume JVM did not finish: %s", tail(resumedLog)).isTrue();
                assertThat(resumed.exitValue()).withFailMessage("Intent resume JVM failed: %s", tail(resumedLog)).isZero();
            }

            assertThat(Files.readString(receipt).trim()).isEqualTo(runId);
            var after = tasks(jdbc, runId);
            var currentTask = after.getLast();
            assertThat(after).extracting(row -> row.get("status"))
                    .containsExactly("COMPLETED", "COMPLETED", "COMPLETED", "COMPLETED");
            assertThat(after.subList(0, 3)).isEqualTo(before.subList(0, 3));
            for (String field : List.of("id", "task_type", "started_at")) {
                assertThat(currentTask.get(field)).as("resume preserves original task %s", field).isEqualTo(originalTask.get(field));
            }
            assertThat(currentTask.get("claim_token")).isNotEqualTo(originalTask.get("claim_token"));
            assertThat(currentTask.get("claim_expires_at")).isNotEqualTo(originalTask.get("claim_expires_at"));
            assertThat(jdbc.queryForObject("select executed_order from run_item where run_id=?", Integer.class, runId))
                    .isEqualTo(executedOrder);
            var finalOperation = jdbc.queryForMap("select * from git_operation where task_id=?", taskId);
            assertThat(operationProvenance(finalOperation)).isEqualTo(operationProvenance(originalOperation));
            assertThat(finalOperation).containsEntry("state", "APPLIED");
            assertThat(jdbc.queryForObject("select count(*) from git_operation where run_id=?", Integer.class, runId)).isEqualTo(2);
            var finalEvents = mergeEvents(jdbc, operationId);
            assertThat(finalEvents.getFirst()).isEqualTo(originalEvents.getFirst());
            assertSenderEvidence(finalEvents, originalOperation, taskId, currentTask.get("claim_token").toString());
            var finalSteps = jdbc.queryForList("select s.* from run_step s join run_task t on t.id=s.task_id "
                    + "where t.run_id=? order by t.task_order", runId);
            assertThat(finalSteps).hasSize(4);
            assertThat(finalSteps.subList(0, 3)).isEqualTo(originalSteps);
            assertThat(finalSteps.getLast()).containsEntry("task_id", taskId).containsEntry("result", "MERGED");
            var expectedMetadata = new java.util.LinkedHashMap<>(originalMetadata);
            expectedMetadata.put("git.mergeSha", "c".repeat(40));
            expectedMetadata.remove("git.blockReason");
            expectedMetadata.remove("git.requestId");
            assertThat(metadata(jdbc, runId)).isEqualTo(expectedMetadata);
            assertThat(jdbc.queryForObject("select final_result from run_item where run_id=?", String.class, runId)).isEqualTo("MERGED");
            assertThat(jdbc.queryForObject("select execution_status from run where id=?", String.class, runId)).isEqualTo("DONE");
            assertThat(jdbc.queryForObject("select count(*) from run", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select last_merge_at from window_iteration where window_id=? and iteration_key=?",
                    java.sql.Timestamp.class, windowId, ITERATION).toInstant()).isAfterOrEqualTo(resultProcessingStarted);
            var finalGuard = jdbc.queryForMap("select * from repo_execution_guard where remote_key=?",
                    originalOperation.get("remote_key"));
            assertThat(finalGuard).containsEntry("generation", originalGuard.get("generation"))
                    .containsEntry("session_id", null).containsEntry("operation_id", null).containsEntry("reason", null);
            var frozenRequest = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(originalOperation.get("target_json").toString());
            String message = "Merge " + SOURCE + " into " + TARGET;
            assertThat(frozenRequest.path("sha").isTextual()).isTrue();
            assertThat(frozenRequest.path("sha").textValue()).isEqualTo(SOURCE_SHA);
            assertThat(frozenRequest.path("merge_commit_message").isTextual()).isTrue();
            assertThat(frozenRequest.path("merge_commit_message").textValue()).isEqualTo(message);
            assertThat(frozenRequest.path("should_remove_source_branch").isBoolean()).isTrue();
            assertThat(frozenRequest.path("should_remove_source_branch").booleanValue()).isFalse();
            var sentBody = server.body("PUT", root + "/merge_requests/7/merge");
            assertThat(sentBody.size()).isEqualTo(3);
            assertThat(sentBody.path("sha")).isEqualTo(frozenRequest.path("sha"));
            assertThat(sentBody.path("merge_commit_message")).isEqualTo(frozenRequest.path("merge_commit_message"));
            assertThat(sentBody.path("should_remove_source_branch")).isEqualTo(frozenRequest.path("should_remove_source_branch"));
            server.verify("POST", root + "/repository/branches", 0);
            server.verify("POST", root + "/merge_requests", 1);
            server.verify("PUT", root + "/merge_requests/7/merge", 1);
            assertThat(server.writeCount()).isEqualTo(2);
            assertThat(server.requests).noneMatch(request -> request.internalHeaders());
            System.out.printf("PROCESS_MERGE_INTENT_RESUME run=%s task=%s operation=%s attempt=%s killedPid=%d "
                            + "exit=%d resumedPid=%d projectionPid=%d senderChanged=true originalProvenance=true "
                            + "mrPost=1 mergePut=1 mergeEvents=%d secondCrash=%s projectionHttp=%s%n",
                    runId, taskId, operationId, originalOperation.get("attempt_id"), firstPid, first.exitValue(),
                    resumed.pid(), projected == null ? -1 : projected.pid(), finalEvents.size(), crashAgainAfterApplied,
                    projectionRequests < 0 ? "not-applicable" : "0");
        } finally {
            stopOwnedProcess(first);
            stopOwnedProcess(resumed);
            stopOwnedProcess(projected);
            server.stop();
        }
    }

    @org.junit.jupiter.api.Test
    @Timeout(180)
    void mergeSendRestartDoesNotReplayCommittedSendBeforeHttp() throws Exception {
        GitLabHttpFixture server = new GitLabHttpFixture();
        Process first = null;
        Process restarted = null;
        try (var db = new PostgreSQLContainer<>("postgres:18.1")
                .withDatabaseName("merge_sent_restart").withUsername("process_test").withPassword("isolated-process-db")) {
            db.start();
            server.start();
            routes(server);
            String root = "/api/v4/projects/" + PROJECT_ID;
            String windowId = UUID.randomUUID().toString();
            String repoId = UUID.randomUUID().toString();
            Path marker = temporary.resolve("merge-sent-checkpoint.txt");
            Path receipt = temporary.resolve("merge-sent-receipt.txt");
            Path firstLog = temporary.resolve("merge-sent-first.log");
            first = launch("first-sent-merge", db, server, windowId, repoId, marker, receipt, firstLog);
            awaitCheckpoint(first, marker, firstLog);
            JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                    db.getJdbcUrl(), db.getUsername(), db.getPassword()));
            String runId = Files.readString(marker).trim();
            var before = tasks(jdbc, runId);
            String taskId = before.getLast().get("id").toString();
            var originalMetadata = metadata(jdbc, runId);
            var originalOperation = jdbc.queryForMap("select * from git_operation where task_id=?", taskId);
            String operationId = originalOperation.get("id").toString();
            var originalEvents = mergeEvents(jdbc, operationId);
            var originalGuard = jdbc.queryForMap("select * from repo_execution_guard where remote_key=?",
                    originalOperation.get("remote_key"));
            var originalSteps = jdbc.queryForList("select s.* from run_step s join run_task t on t.id=s.task_id "
                    + "where t.run_id=? order by t.task_order", runId);
            assertThat(before).extracting(row -> row.get("status"))
                    .containsExactly("COMPLETED", "COMPLETED", "COMPLETED", "RUNNING");
            assertThat(originalOperation).containsEntry("state", "SEND_STARTED").containsEntry("action", "MERGE_MR");
            assertThat(originalEvents).extracting(row -> row.get("event_type")).containsExactly("INTENT_RECORDED", "SEND_STARTED");
            assertThat(originalGuard).containsEntry("session_id", before.getLast().get("claim_token"))
                    .containsEntry("operation_id", operationId).containsEntry("reason", "REQUEST_IN_FLIGHT");
            assertThat(originalSteps).hasSize(3);
            server.verify("POST", root + "/merge_requests", 1);
            server.verify("PUT", root + "/merge_requests/7/merge", 0);
            long killedPid = first.pid();
            first.destroyForcibly();
            assertThat(first.waitFor(10, TimeUnit.SECONDS)).isTrue();
            assertThat(first.exitValue()).isEqualTo(137);
            int requestsBeforeRestart = server.requests.size();
            Path restartedLog = temporary.resolve("merge-sent-restarted.log");
            restarted = launch("resume-sent-merge", db, server, windowId, repoId, marker, receipt, restartedLog);
            System.out.printf("PROCESS_MERGE_SEND_CHECKPOINT run=%s task=%s operation=%s attempt=%s killedPid=%d "
                            + "exit=%d resumedPid=%d mergePut=0 noPendingAtKill=true%n",
                    runId, taskId, operationId, originalOperation.get("attempt_id"), killedPid, first.exitValue(), restarted.pid());
            assertThat(restarted.waitFor(55, TimeUnit.SECONDS))
                    .withFailMessage("Sent observation JVM did not finish: %s", tail(restartedLog)).isTrue();
            assertThat(restarted.exitValue()).withFailMessage("Sent observation JVM failed: %s", tail(restartedLog)).isZero();
            int completedScans = Integer.parseInt(Files.readString(marker.resolveSibling("sent-completed-scans.txt")).trim());
            assertThat(completedScans).isGreaterThanOrEqualTo(3);
            assertThat(Files.readString(receipt).trim()).isEqualTo(runId);
            assertThat(tasks(jdbc, runId)).isEqualTo(before);
            assertThat(metadata(jdbc, runId)).isEqualTo(originalMetadata);
            assertThat(jdbc.queryForMap("select * from git_operation where task_id=?", taskId)).isEqualTo(originalOperation);
            assertThat(mergeEvents(jdbc, operationId)).isEqualTo(originalEvents);
            assertThat(jdbc.queryForMap("select * from repo_execution_guard where remote_key=?",
                    originalOperation.get("remote_key"))).isEqualTo(originalGuard);
            assertThat(jdbc.queryForList("select s.* from run_step s join run_task t on t.id=s.task_id "
                    + "where t.run_id=? order by t.task_order", runId)).isEqualTo(originalSteps);
            assertThat(jdbc.queryForObject("select execution_status from run where id=?", String.class, runId)).isEqualTo("RUNNING");
            assertThat(jdbc.queryForObject("select count(*) from run", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select last_merge_at from window_iteration where window_id=? and iteration_key=?",
                    java.sql.Timestamp.class, windowId, ITERATION)).isNull();
            assertThat(server.requests).hasSize(requestsBeforeRestart);
            server.verify("PUT", root + "/merge_requests/7/merge", 0);
            assertThat(server.writeCount()).isEqualTo(1);
            System.out.printf("PROCESS_MERGE_SEND_NO_REPLAY run=%s task=%s operation=%s killedPid=%d exit=%d resumedPid=%d "
                            + "completedScansAfterExpiry=%d httpAfterRestart=0 mergePut=0 preservedTask=true preservedEvents=%d%n",
                    runId, taskId, operationId, killedPid, first.exitValue(), restarted.pid(), completedScans, originalEvents.size());
        } finally {
            stopOwnedProcess(first);
            stopOwnedProcess(restarted);
            server.stop();
        }
    }

    @ParameterizedTest(name = "unsentRestart={0}")
    @ValueSource(strings = {"contained", "blocked", "unknown"})
    @Timeout(240)
    void mergeNotSentRestartClosesOriginalIntentWithoutReplay(String observation) throws Exception {
        GitLabHttpFixture server = new GitLabHttpFixture();
        Process first = null;
        Process resumed = null;
        Process observed = null;
        try (var db = new PostgreSQLContainer<>("postgres:18.1")
                .withDatabaseName("merge_not_sent_restart").withUsername("process_test").withPassword("isolated-process-db")) {
            db.start();
            server.start();
            routes(server);
            String root = "/api/v4/projects/" + PROJECT_ID;
            String windowId = UUID.randomUUID().toString();
            String repoId = UUID.randomUUID().toString();
            Path marker = temporary.resolve("not-sent-original-checkpoint.txt");
            Path receipt = temporary.resolve("not-sent-resumed-receipt.txt");
            Path firstLog = temporary.resolve("not-sent-first.log");
            first = launch("first-intent-merge", db, server, windowId, repoId, marker, receipt, firstLog);
            awaitCheckpoint(first, marker, firstLog);
            JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword()));
            String runId = Files.readString(marker).trim();
            var originalTasks = tasks(jdbc, runId);
            var originalTask = originalTasks.getLast();
            String taskId = originalTask.get("id").toString();
            int originalTaskOrder = jdbc.queryForObject("select task_order from run_task where id=?", Integer.class, taskId);
            var originalOperation = jdbc.queryForMap("select * from git_operation where task_id=?", taskId);
            String operationId = originalOperation.get("id").toString();
            var originalEvents = mergeEvents(jdbc, operationId);
            var originalMetadata = metadata(jdbc, runId);
            var originalGuard = jdbc.queryForMap("select * from repo_execution_guard where remote_key=?", originalOperation.get("remote_key"));
            var originalSteps = jdbc.queryForList("select s.* from run_step s join run_task t on t.id=s.task_id where t.run_id=? order by t.task_order", runId);
            var originalOrder = jdbc.queryForMap("select planned_order,executed_order from run_item where run_id=?", runId);
            assertThat(originalTasks).extracting(row -> row.get("status")).containsExactly("COMPLETED", "COMPLETED", "COMPLETED", "RUNNING");
            assertThat(originalOperation).containsEntry("state", "INTENT_RECORDED").containsEntry("action", "MERGE_MR")
                    .containsEntry("claim_token", originalTask.get("claim_token")).containsEntry("session_id", originalTask.get("claim_token"));
            assertThat(originalEvents).singleElement().satisfies(event -> assertThat(event)
                    .containsEntry("event_type", "INTENT_RECORDED").containsEntry("event_sequence", 0)
                    .containsEntry("attempt_id", originalOperation.get("attempt_id")));
            assertThat(originalGuard).containsEntry("operation_id", operationId)
                    .containsEntry("session_id", originalTask.get("claim_token")).containsEntry("reason", "INTENT_PENDING");
            assertThat(originalSteps).hasSize(3);
            server.verify("POST", root + "/merge_requests", 1);
            server.verify("PUT", root + "/merge_requests/7/merge", 0);
            long killedPid = first.pid();
            first.destroyForcibly();
            assertThat(first.waitFor(10, TimeUnit.SECONDS)).isTrue();
            assertThat(first.exitValue()).isEqualTo(137);
            System.out.printf("PROCESS_MERGE_NOT_SENT_CHECKPOINT observation=%s run=%s task=%s operation=%s attempt=%s "
                            + "killedPid=%d exit=%d events=1 post=1 put=0 noPendingAtKill=true%n",
                    observation, runId, taskId, operationId, originalOperation.get("attempt_id"), killedPid, first.exitValue());

            int requestsBeforeResume = server.requests.size();
            String currentMr = server.responses.get("GET " + root + "/merge_requests/7").body();
            switch (observation) {
                case "contained" -> {
                    server.reply("GET", root + "/repository/commits/" + SOURCE_SHA + "/refs", 200,
                            "[{\"type\":\"branch\",\"name\":\"" + TARGET + "\"}]");
                    server.reply("GET", root + "/merge_requests/7", 404, "{}");
                }
                case "blocked" -> server.reply("GET", root + "/merge_requests/7", 200,
                        currentMr.replace("\"mergeable\"", "\"not_approved\""));
                case "unknown" -> server.reply("GET", root + "/merge_requests/7", 503, "{}");
                default -> throw new AssertionError(observation);
            }
            Path resumedLog = temporary.resolve("not-sent-resumed.log");
            resumed = launch("resume-not-sent-" + observation, db, server, windowId, repoId, marker, receipt, resumedLog);
            assertThat(resumed.waitFor(55, TimeUnit.SECONDS))
                    .withFailMessage("Unsent completion JVM did not finish: %s", tail(resumedLog)).isTrue();
            assertThat(resumed.exitValue()).withFailMessage("Unsent completion JVM failed: %s", tail(resumedLog)).isZero();
            int resumedScans = Integer.parseInt(Files.readString(marker.resolveSibling("not-sent-resumed-scans.txt")).trim());
            assertThat(resumedScans).isGreaterThanOrEqualTo(3);
            assertThat(Files.readString(receipt).trim()).isEqualTo(runId);

            String result = mergeNotSentProcessResult(observation);
            String expectedTaskStatus = observation.equals("contained") ? "COMPLETED" : observation.equals("blocked") ? "FAILED" : "UNKNOWN";
            String expectedRunStatus = observation.equals("contained") ? "DONE" : observation.equals("blocked") ? "FAILED" : "NEEDS_REVIEW";
            var completedTasks = tasks(jdbc, runId);
            var completedTask = completedTasks.getLast();
            String currentToken = completedTask.get("claim_token").toString();
            assertThat(currentToken).isNotEqualTo(originalTask.get("claim_token"));
            assertThat(completedTasks.subList(0, 3)).isEqualTo(originalTasks.subList(0, 3));
            assertThat(jdbc.queryForObject("select task_order from run_task where id=?", Integer.class, taskId)).isEqualTo(originalTaskOrder);
            assertThat(completedTask).containsEntry("id", taskId).containsEntry("status", expectedTaskStatus)
                    .containsEntry("started_at", originalTask.get("started_at"));
            assertThat(completedTask.get("finished_at")).isNotNull();
            var completedOperation = jdbc.queryForMap("select * from git_operation where task_id=?", taskId);
            var patch = mergeNotSentProcessPatch(observation);
            assertThat(completedOperation).containsEntry("state", "NOT_SENT").containsEntry("reason", patch.get("git.blockReason"));
            assertThat(operationProvenance(completedOperation)).isEqualTo(operationProvenance(originalOperation));
            assertThat(((Number) completedOperation.get("state_version")).longValue())
                    .isGreaterThan(((Number) originalOperation.get("state_version")).longValue());
            var completedEvents = mergeEvents(jdbc, operationId);
            assertThat(completedEvents).hasSize(2).extracting(row -> row.get("event_type")).containsExactly("INTENT_RECORDED", "NOT_SENT");
            assertThat(completedEvents.getFirst()).isEqualTo(originalEvents.getFirst());
            assertThat(completedEvents.getLast()).containsEntry("event_sequence", 1)
                    .containsEntry("attempt_id", originalOperation.get("attempt_id")).containsEntry("reason", patch.get("git.blockReason"));
            var json = new com.fasterxml.jackson.databind.ObjectMapper();
            var local = json.createObjectNode().put("taskId", taskId).put("claimToken", currentToken).put("result", result);
            local.set("metadata", json.valueToTree(patch));
            assertThat(json.readTree(completedEvents.getLast().get("evidence_json").toString())).isEqualTo(local);
            var completedMetadata = new java.util.LinkedHashMap<>(originalMetadata);
            patch.forEach((key, value) -> { if (value == null) completedMetadata.remove(key); else completedMetadata.put(key, value); });
            assertThat(metadata(jdbc, runId)).isEqualTo(completedMetadata);
            assertThat(completedMetadata).doesNotContainKey("git.requestId");
            assertThat(jdbc.queryForMap("select planned_order,executed_order from run_item where run_id=?", runId)).isEqualTo(originalOrder);
            var completedGuard = jdbc.queryForMap("select * from repo_execution_guard where remote_key=?", originalOperation.get("remote_key"));
            assertThat(completedGuard).containsEntry("generation", originalGuard.get("generation"))
                    .containsEntry("session_id", null).containsEntry("operation_id", null).containsEntry("reason", null);
            var completedSteps = jdbc.queryForList("select s.* from run_step s join run_task t on t.id=s.task_id where t.run_id=? order by t.task_order", runId);
            assertThat(completedSteps).hasSize(4);
            assertThat(completedSteps.subList(0, 3)).isEqualTo(originalSteps);
            assertThat(completedSteps.getLast()).containsEntry("task_id", taskId).containsEntry("result", result);
            assertThat(jdbc.queryForObject("select final_result from run_item where run_id=?", String.class, runId)).isEqualTo(result);
            assertThat(jdbc.queryForObject("select execution_status from run where id=?", String.class, runId)).isEqualTo(expectedRunStatus);
            var runFinishedAt = jdbc.queryForObject("select finished_at from run where id=?", java.sql.Timestamp.class, runId);
            if (observation.equals("unknown")) assertThat(runFinishedAt).isNull();
            else assertThat(runFinishedAt).isNotNull();
            var lastMergeAt = jdbc.queryForObject("select last_merge_at from window_iteration where window_id=? and iteration_key=?",
                    java.sql.Timestamp.class, windowId, ITERATION);
            if (observation.equals("contained")) assertThat(lastMergeAt).isNotNull();
            else assertThat(lastMergeAt).isNull();
            if (observation.equals("contained")) {
                assertThat(server.requests.subList(requestsBeforeResume, server.requests.size()))
                        .noneMatch(request -> request.method().equals("GET") && request.path().equals(root + "/merge_requests/7"));
            }
            server.verify("POST", root + "/merge_requests", 1);
            server.verify("PUT", root + "/merge_requests/7/merge", 0);
            assertThat(server.writeCount()).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from git_operation where task_id=?", Integer.class, taskId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from run", Integer.class)).isEqualTo(1);

            int requestsBeforeObservation = server.requests.size();
            Path observedLog = temporary.resolve("not-sent-observed.log");
            Path observedReceipt = temporary.resolve("not-sent-observed-receipt.txt");
            observed = launch("observe-not-sent-" + observation, db, server, windowId, repoId, marker, observedReceipt, observedLog);
            assertThat(observed.waitFor(55, TimeUnit.SECONDS))
                    .withFailMessage("Unsent observation JVM did not finish: %s", tail(observedLog)).isTrue();
            assertThat(observed.exitValue()).withFailMessage("Unsent observation JVM failed: %s", tail(observedLog)).isZero();
            int observedScans = Integer.parseInt(Files.readString(marker.resolveSibling("not-sent-observed-scans.txt")).trim());
            assertThat(observedScans).isGreaterThanOrEqualTo(3);
            assertThat(Files.readString(observedReceipt).trim()).isEqualTo(runId);
            assertThat(tasks(jdbc, runId)).isEqualTo(completedTasks);
            assertThat(jdbc.queryForMap("select * from git_operation where task_id=?", taskId)).isEqualTo(completedOperation);
            assertThat(mergeEvents(jdbc, operationId)).isEqualTo(completedEvents);
            assertThat(metadata(jdbc, runId)).isEqualTo(completedMetadata);
            assertThat(jdbc.queryForMap("select * from repo_execution_guard where remote_key=?", originalOperation.get("remote_key"))).isEqualTo(completedGuard);
            assertThat(jdbc.queryForList("select s.* from run_step s join run_task t on t.id=s.task_id where t.run_id=? order by t.task_order", runId)).isEqualTo(completedSteps);
            assertThat(server.requests).hasSize(requestsBeforeObservation);
            server.verify("POST", root + "/merge_requests", 1);
            server.verify("PUT", root + "/merge_requests/7/merge", 0);
            System.out.printf("PROCESS_MERGE_NOT_SENT_RESULT observation=%s run=%s task=%s operation=%s attempt=%s "
                            + "killedPid=%d exit=%d resumedPid=%d observedPid=%d result=%s oldTokenRevoked=true currentTokenPreserved=true "
                            + "resumedScans=%d observedScans=%d events=%d post=1 put=0 observationHttp=0%n",
                    observation, runId, taskId, operationId, originalOperation.get("attempt_id"), killedPid, first.exitValue(),
                    resumed.pid(), observed.pid(), result, resumedScans, observedScans, completedEvents.size());
        } finally {
            stopOwnedProcess(first);
            stopOwnedProcess(resumed);
            stopOwnedProcess(observed);
            server.stop();
        }
    }

    private static String mergeNotSentProcessResult(String observation) {
        return switch (observation) {
            case "contained" -> "ALREADY_MERGED";
            case "blocked" -> "MERGE_BLOCKED";
            case "unknown" -> "UNKNOWN";
            default -> throw new AssertionError(observation);
        };
    }

    private static Map<String, String> mergeNotSentProcessPatch(String observation) {
        Map<String, String> patch = new java.util.LinkedHashMap<>();
        patch.put("git.requestId", null);
        patch.put("git.blockReason", switch (observation) {
            case "contained" -> null;
            case "blocked" -> "APPROVAL_REQUIRED";
            case "unknown" -> "UNKNOWN";
            default -> throw new AssertionError(observation);
        });
        if (observation.equals("contained")) {
            patch.put("git.mergeSha", null);
            patch.put("git.mrDisposition", "ALREADY_MERGED");
        }
        return patch;
    }

    private static List<Map<String, Object>> mergeEvents(JdbcTemplate jdbc, String operationId) {
        return jdbc.queryForList("select * from git_operation_event where operation_id=? order by event_sequence", operationId);
    }

    private static Map<String, Object> operationProvenance(Map<String, Object> operation) {
        var historical = new java.util.LinkedHashMap<>(operation);
        for (String current : List.of("state", "state_version", "reason", "updated_at")) historical.remove(current);
        return historical;
    }

    private static void assertSenderEvidence(List<Map<String, Object>> events, Map<String, Object> originalOperation,
                                             String taskId, String claimToken) throws Exception {
        assertThat(events).hasSize(3);
        assertThat(events).extracting(row -> row.get("event_type")).containsExactly("INTENT_RECORDED", "SEND_STARTED", "APPLIED");
        assertThat(events).allSatisfy(event -> assertThat(event.get("attempt_id")).isEqualTo(originalOperation.get("attempt_id")));
        var sender = new com.fasterxml.jackson.databind.ObjectMapper().readTree(events.get(1).get("evidence_json").toString());
        assertThat(sender.path("taskId").isTextual()).isTrue();
        assertThat(sender.path("taskId").textValue()).isEqualTo(taskId);
        assertThat(sender.path("claimToken").isTextual()).isTrue();
        assertThat(sender.path("claimToken").textValue()).isEqualTo(claimToken);
    }

    private Process launch(String mode, PostgreSQLContainer<?> db, GitLabHttpFixture server,
                           String windowId, String repoId, Path marker, Path receipt, Path log) throws Exception {
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        var command = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", classpath, Child.class.getName(), mode, marker.toString(), receipt.toString());
        command.environment().putAll(Map.of(
                "RH_C2_DB_URL", db.getJdbcUrl(), "RH_C2_DB_USER", db.getUsername(),
                "RH_C2_DB_PASSWORD", db.getPassword(), "RH_C2_GITLAB_URL", server.baseUrl(),
                "RH_C2_WINDOW_ID", windowId, "RH_C2_REPO_ID", repoId));
        return command.redirectErrorStream(true).redirectOutput(log.toFile()).start();
    }

    private static void awaitCheckpoint(Process process, Path marker, Path log) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(55);
        while (!Files.exists(marker)) {
            if (!process.isAlive()) fail("JVM exited before the requested checkpoint: " + tail(log));
            if (System.nanoTime() >= deadline) fail("Requested checkpoint was not committed: " + tail(log));
            Thread.sleep(100);
        }
    }

    private static void stopOwnedProcess(Process process) throws InterruptedException {
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
        }
    }

    private static String tail(Path path) throws Exception {
        if (!Files.exists(path)) return "(no child output)";
        String text = Files.readString(path);
        return text.substring(Math.max(0, text.length() - 9000));
    }

    private static List<Map<String, Object>> tasks(JdbcTemplate jdbc, String runId) {
        return jdbc.queryForList("select id, task_type, status, claim_token, started_at, claim_expires_at, finished_at "
                + "from run_task where run_id=? order by task_order", runId);
    }

    private static Map<String, String> metadata(JdbcTemplate jdbc, String runId) {
        Map<String, String> facts = new java.util.LinkedHashMap<>();
        jdbc.query("select metadata_key, metadata_value from run_item_metadata where run_item_id in "
                        + "(select id from run_item where run_id=?)",
                row -> { facts.put(row.getString(1), row.getString(2)); }, runId);
        return facts;
    }

    private static void routes(GitLabHttpFixture server) {
        String root = "/api/v4/projects/" + PROJECT_ID;
        server.reply("GET", "/api/v4/projects/acme%2Frestart", 200, "{\"id\":101}");
        for (String branch : List.of(SOURCE, TARGET)) {
            server.reply("GET", root + "/repository/branches/" + URLEncoder.encode(branch, StandardCharsets.UTF_8),
                    200, "{\"name\":\"" + branch + "\",\"commit\":{\"id\":\""
                            + (branch.equals(SOURCE) ? SOURCE_SHA : "b".repeat(40)) + "\"}}");
        }
        server.reply("GET", root + "/merge_requests", 200, "[]");
        server.reply("GET", root + "/repository/commits/" + SOURCE_SHA + "/refs", 200, "[]");
        String mr = "{\"iid\":7,\"project_id\":101,\"source_project_id\":101,\"target_project_id\":101,"
                + "\"source_branch\":\"" + SOURCE + "\",\"target_branch\":\"" + TARGET
                + "\",\"state\":\"opened\",\"sha\":\"" + SOURCE_SHA + "\",\"web_url\":\""
                + server.baseUrl() + "/acme/restart/-/merge_requests/7\",\"detailed_merge_status\":\"mergeable\"}";
        server.reply("POST", root + "/merge_requests", 201, mr);
        server.reply("GET", root + "/merge_requests/7", 200, mr);
        server.reply("PUT", root + "/merge_requests/7/merge", 200,
                mr.replace("\"state\":\"opened\"", "\"state\":\"merged\"")
                        .replace("\"detailed_merge_status\"", "\"merge_commit_sha\":\"" + "c".repeat(40)
                                + "\",\"detailed_merge_status\""));
    }

    /** 显式注册的测试配置；装饰已初始化的事务代理，不替换默认 worker 或执行器。 */
    public static final class CheckpointConfiguration {
        private static final java.util.concurrent.atomic.AtomicInteger COMPLETED_SCANS =
                new java.util.concurrent.atomic.AtomicInteger();

        @Bean
        static BeanPostProcessor checkpointPause(Environment environment) {
            boolean pauseAfterMr = environment.getProperty("releasehub.fixture.pause-after-mr", Boolean.class, false);
            boolean pauseAfterClaim = environment.getProperty("releasehub.fixture.pause-after-claim", Boolean.class, false);
            String completionAction = environment.getProperty("releasehub.fixture.pause-before-completion", "");
            boolean pauseAfterProjection = environment.getProperty("releasehub.fixture.pause-after-projection", Boolean.class, false);
            boolean observeScans = environment.getProperty("releasehub.fixture.observe-scans", Boolean.class, false);
            String journalPoint = environment.getProperty("releasehub.fixture.pause-after-merge-journal", "");
            Path marker = Path.of(environment.getRequiredProperty("releasehub.fixture.checkpoint-file"));
            Path completionMarker = Path.of(environment.getProperty(
                    "releasehub.fixture.completion-checkpoint-file", marker.toString()));
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (!journalPoint.isBlank() && bean instanceof GitOperationJournalPort originalJournal) {
                        return Proxy.newProxyInstance(GitOperationJournalPort.class.getClassLoader(),
                                new Class<?>[]{GitOperationJournalPort.class}, (proxy, method, arguments) -> {
                                    Object result;
                                    try { result = method.invoke(originalJournal, arguments); }
                                    catch (InvocationTargetException error) { throw error.getCause(); }
                                    String pausedRunId = null;
                                    if ("INTENT_RECORDED".equals(journalPoint) && method.getName().equals("begin")
                                            && arguments[0] instanceof GitOperationJournalPort.Command command
                                            && command.taskFence() != null && "MERGE_MR".equals(command.action())) {
                                        pausedRunId = command.runId();
                                    }
                                    if ("SEND_STARTED".equals(journalPoint) && method.getName().equals("sending")) {
                                        var operation = originalJournal.find((String) arguments[0]).orElseThrow().operation();
                                        if ("MERGE_MR".equals(operation.action())) pausedRunId = operation.runId();
                                    }
                                    if (pausedRunId != null) {
                                        // The actual Journal transaction has returned; only the next caller instruction is paused.
                                        Files.writeString(marker, pausedRunId);
                                        new CountDownLatch(1).await();
                                        throw new IllegalStateException("Committed merge journal pause unexpectedly ended");
                                    }
                                    return result;
                                });
                    }
                    if (!(pauseAfterMr || pauseAfterClaim || !completionAction.isBlank() || pauseAfterProjection || observeScans)
                            || !(bean instanceof RunPort original)) return bean;
                    return Proxy.newProxyInstance(RunPort.class.getClassLoader(), new Class<?>[]{RunPort.class},
                            (proxy, method, arguments) -> {
                                if (method.getName().equals("completeTask")
                                        && arguments[0] instanceof RunPort.TaskClaim task
                                        && task.actionType().name().equals(completionAction)) {
                                    Files.writeString(completionMarker, task.runId());
                                    new CountDownLatch(1).await();
                                    throw new IllegalStateException("Applied action pause unexpectedly ended");
                                }
                                if (pauseAfterMr && method.getName().equals("claimNextTask")) {
                                    String runId = (String) arguments[0];
                                    boolean committed = original.findTasks(runId).stream().anyMatch(task ->
                                            task.actionType() == ActionType.ENSURE_MR
                                                    && task.status().equals("COMPLETED"));
                                    if (committed) {
                                        Files.writeString(marker, runId);
                                        new CountDownLatch(1).await();
                                        throw new IllegalStateException("Checkpoint pause unexpectedly ended");
                                    }
                                }
                                Object result;
                                try { result = method.invoke(original, arguments); }
                                catch (InvocationTargetException error) { throw error.getCause(); }
                                if (observeScans && method.getName().equals("findTaskCandidateRunIds")) {
                                    COMPLETED_SCANS.incrementAndGet();
                                }
                                if (pauseAfterClaim && method.getName().equals("claimNextTask")
                                        && result instanceof java.util.Optional<?> claimed
                                        && claimed.orElse(null) instanceof RunPort.TaskClaim task
                                        && task.actionType() == ActionType.TRY_MERGE) {
                                    Files.writeString(marker, task.runId());
                                    new CountDownLatch(1).await();
                                    throw new IllegalStateException("Claim pause unexpectedly ended");
                                }
                                if (pauseAfterProjection && method.getName().equals("projectNextAppliedTask")
                                        && Boolean.TRUE.equals(result)) {
                                    Files.writeString(marker.resolveSibling("projection-committed.txt"), (String) arguments[0]);
                                    while (!Files.exists(marker.resolveSibling("projection-release.txt"))) Thread.sleep(25);
                                }
                                return result;
                            });
                }
            };
        }
    }

    public static final class Child {
        private Child() {}
        public static void main(String[] args) throws Exception {
            boolean claimedBeforeCrash = "first-claimed".equals(args[0]);
            boolean appliedBranchBeforeCrash = "first-applied-branch".equals(args[0]);
            boolean appliedMrBeforeCrash = "first-applied-mr".equals(args[0]);
            boolean branchProjectionResume = "resume-applied-branch".equals(args[0]);
            boolean mrProjectionResume = "resume-applied-mr".equals(args[0]);
            boolean appliedMergeBeforeCrash = "first-applied-merge".equals(args[0]);
            boolean mergeProjectionResume = "resume-applied-merge".equals(args[0]);
            boolean intentMergeBeforeCrash = "first-intent-merge".equals(args[0]);
            boolean sentMergeBeforeCrash = "first-sent-merge".equals(args[0]);
            boolean intentThenAppliedCrash = "resume-intent-applied-merge".equals(args[0]);
            boolean sentMergeResume = "resume-sent-merge".equals(args[0]);
            boolean notSentResume = args[0].startsWith("resume-not-sent-");
            boolean notSentObservation = args[0].startsWith("observe-not-sent-");
            boolean first = "first".equals(args[0]) || claimedBeforeCrash || appliedBranchBeforeCrash
                    || appliedMrBeforeCrash || appliedMergeBeforeCrash || intentMergeBeforeCrash || sentMergeBeforeCrash;
            Path marker = Path.of(args[1]);
            Path receipt = Path.of(args[2]);
            try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
                    ReleaseHubApplication.class, CheckpointConfiguration.class)
                    .run("--spring.profiles.active=e2e", "--server.port=0", "--spring.jpa.open-in-view=false",
                            "--releasehub.run.worker.enabled=true", "--releasehub.run.worker.parallelism=1",
                            "--releasehub.run.worker.queue-capacity=0", "--releasehub.run.worker.scan-delay-ms=25",
                            "--releasehub.run.worker.claim-lease-ms=2000",
                            "--releasehub.fixture.pause-after-mr=" + (first && !claimedBeforeCrash && !appliedBranchBeforeCrash && !appliedMrBeforeCrash && !appliedMergeBeforeCrash && !intentMergeBeforeCrash && !sentMergeBeforeCrash),
                            "--releasehub.fixture.pause-after-claim=" + claimedBeforeCrash,
                            "--releasehub.fixture.pause-before-completion=" + (appliedBranchBeforeCrash ? "ENSURE_FEATURE" : appliedMrBeforeCrash ? "ENSURE_MR" : appliedMergeBeforeCrash || intentThenAppliedCrash ? "TRY_MERGE" : ""),
                            "--releasehub.fixture.pause-after-projection=" + (branchProjectionResume || mrProjectionResume || mergeProjectionResume),
                            "--releasehub.fixture.pause-after-merge-journal=" + (intentMergeBeforeCrash ? "INTENT_RECORDED" : sentMergeBeforeCrash ? "SEND_STARTED" : ""),
                            "--releasehub.fixture.observe-scans=" + (sentMergeResume || notSentResume || notSentObservation),
                            "--releasehub.fixture.completion-checkpoint-file=" + (intentThenAppliedCrash
                                    ? marker.resolveSibling("resumed-merge-applied.txt") : marker),
                            "--releasehub.fixture.checkpoint-file=" + marker,
                            "--spring.datasource.url=" + environment("RH_C2_DB_URL"),
                            "--spring.datasource.username=" + environment("RH_C2_DB_USER"),
                            "--spring.datasource.password=" + environment("RH_C2_DB_PASSWORD"),
                            "--releasehub.gitlab.base-url=" + environment("RH_C2_GITLAB_URL"))) {
                String windowId = environment("RH_C2_WINDOW_ID");
                String repoId = environment("RH_C2_REPO_ID");
                RunAppService service = context.getBean(RunAppService.class);
                if (first) {
                    fixtures(context, windowId, repoId);
                    service.startOrchestrate(windowId, List.of(repoId), List.of(ITERATION), true, ACTOR, INTENT);
                    new CountDownLatch(1).await();
                    throw new IllegalStateException("First process unexpectedly stopped");
                }
                // 重启由生产扫描继续原任务；只读等待，不重发受理或手动领取。
                String originalRunId = Files.readString(marker).trim();
                RunPort runs = context.getBean(RunPort.class);
                if (notSentResume || notSentObservation) {
                    String observation = args[0].substring((notSentObservation ? "observe-not-sent-" : "resume-not-sent-").length());
                    observeNotSentMerge(context, runs, originalRunId, marker, receipt, observation, notSentObservation);
                    return;
                }
                if (sentMergeResume) {
                    observeCommittedSend(context, runs, originalRunId, marker, receipt);
                    return;
                }
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(35);
                while (runs.findById(originalRunId).orElseThrow().getExecutionStatus() != RunExecutionStatus.DONE) {
                    if (System.nanoTime() >= deadline) throw new IllegalStateException(
                            "Scheduled worker did not finish original task: " + runs.findTasks(originalRunId));
                    Thread.sleep(50);
                }
                if (mergeProjectionResume) {
                    // The terminal result is already DONE while the worker is held at the projection boundary.
                    while (!Files.exists(marker.resolveSibling("projection-release.txt"))) Thread.sleep(25);
                }
                assertThat(context.getBean(GitOperationJournalPort.class).list(repoId, 0, 10))
                        .hasSize(branchProjectionResume ? 3 : 2).allMatch(operation -> operation.state().equals("APPLIED") && !operation.blocked());
                var run = service.startOrchestrate(windowId, List.of(repoId), List.of(ITERATION), true, ACTOR, INTENT);
                assertThat(run.getId().value()).isEqualTo(originalRunId);
                Files.writeString(receipt, run.getId().value());
            }
        }

        private static void observeNotSentMerge(ConfigurableApplicationContext context, RunPort runs,
                                                String runId, Path marker, Path receipt, String observation,
                                                boolean observeOnly) throws Exception {
            String expectedTaskStatus = observation.equals("contained") ? "COMPLETED" : observation.equals("blocked") ? "FAILED" : "UNKNOWN";
            RunExecutionStatus expectedRunStatus = observation.equals("contained") ? RunExecutionStatus.DONE
                    : observation.equals("blocked") ? RunExecutionStatus.FAILED : RunExecutionStatus.NEEDS_REVIEW;
            String taskId = runs.findTasks(runId).stream().filter(task -> task.actionType() == ActionType.TRY_MERGE)
                    .findFirst().orElseThrow().taskId();
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(35);
            while (runs.findTasks(runId).stream().filter(task -> task.taskId().equals(taskId))
                    .findFirst().orElseThrow().status().equals("RUNNING")) {
                if (observeOnly) throw new AssertionError("A terminal unsent task was revived on JVM restart");
                if (System.nanoTime() >= deadline) fail(
                        "Default worker did not complete the original unsent task: " + runs.findTasks(runId));
                Thread.sleep(25);
            }
            assertThat(runs.findTasks(runId).stream().filter(task -> task.taskId().equals(taskId))
                    .findFirst().orElseThrow().status()).isEqualTo(expectedTaskStatus);
            assertThat(jdbc.queryForObject("select state from git_operation where task_id=?", String.class, taskId)).isEqualTo("NOT_SENT");
            assertThat(runs.findById(runId).orElseThrow().getExecutionStatus()).isEqualTo(expectedRunStatus);
            int before = CheckpointConfiguration.COMPLETED_SCANS.get();
            while (CheckpointConfiguration.COMPLETED_SCANS.get() - before < 3) {
                if (System.nanoTime() >= deadline) fail("Default scanner did not complete three terminal-state scans");
                Thread.sleep(25);
            }
            int completed = CheckpointConfiguration.COMPLETED_SCANS.get() - before;
            assertThat(runs.findTasks(runId).stream().filter(task -> task.taskId().equals(taskId))
                    .findFirst().orElseThrow().status()).isEqualTo(expectedTaskStatus);
            assertThat(jdbc.queryForObject("select state from git_operation where task_id=?", String.class, taskId)).isEqualTo("NOT_SENT");
            assertThat(runs.findById(runId).orElseThrow().getExecutionStatus()).isEqualTo(expectedRunStatus);
            Files.writeString(marker.resolveSibling(observeOnly ? "not-sent-observed-scans.txt" : "not-sent-resumed-scans.txt"),
                    Integer.toString(completed));
            Files.writeString(receipt, runId);
        }

        private static void observeCommittedSend(ConfigurableApplicationContext context, RunPort runs,
                                                  String runId, Path marker, Path receipt) throws Exception {
            String taskId = runs.findTasks(runId).stream().filter(task -> task.actionType() == ActionType.TRY_MERGE)
                    .findFirst().orElseThrow().taskId();
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!Boolean.TRUE.equals(jdbc.queryForObject(
                    "select claim_expires_at <= clock_timestamp() from run_task where id=?", Boolean.class, taskId))) {
                if (System.nanoTime() >= deadline) throw new IllegalStateException("Original sent lease did not expire");
                Thread.sleep(25);
            }
            int before = CheckpointConfiguration.COMPLETED_SCANS.get();
            while (CheckpointConfiguration.COMPLETED_SCANS.get() - before < 3) {
                if (System.nanoTime() >= deadline) throw new IllegalStateException("Default scanner did not complete three scans");
                Thread.sleep(25);
            }
            int completed = CheckpointConfiguration.COMPLETED_SCANS.get() - before;
            assertThat(runs.findTasks(runId).stream().filter(task -> task.taskId().equals(taskId))
                    .findFirst().orElseThrow().status()).isEqualTo("RUNNING");
            Files.writeString(marker.resolveSibling("sent-completed-scans.txt"), Integer.toString(completed));
            Files.writeString(receipt, runId);
        }

        private static void fixtures(ConfigurableApplicationContext context, String windowId, String repoId) {
            var windows = context.getBean(ReleaseWindowPort.class);
            var repositories = context.getBean(CodeRepositoryPort.class);
            var iterations = context.getBean(IterationPort.class);
            var versions = context.getBean(IterationRepoPort.class);
            var bindings = context.getBean(WindowIterationPort.class);
            context.getBean(SettingsPort.class).saveGitLab(new SettingsPort.SettingsGitLab(
                    environment("RH_C2_GITLAB_URL"), "fixture-token"));
            Instant now = Instant.now();
            new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(tx -> {
                windows.save(ReleaseWindow.rehydrate(ReleaseWindowId.of(windowId), WINDOW_KEY,
                        "进程重启验证", "", now, "G001", ReleaseWindowStatus.DRAFT, now, now, false, null));
                repositories.save(CodeRepository.rehydrate(RepoId.of(repoId), "进程验证仓库",
                        environment("RH_C2_GITLAB_URL") + "/acme/restart.git", "main", "G001",
                        RepoType.SERVICE, GitProvider.GITLAB, "fixture-token", false,
                        0, 0, 0, 0, 0, 0, 0, null, now, now, 0));
                iterations.save(Iteration.rehydrate(IterationKey.of(ITERATION), ITERATION, "", null, "G001",
                        Set.of(RepoId.of(repoId)), IterationStatus.ACTIVE, now, now));
                versions.saveWithVersion(ITERATION, repoId, "1.0.0", "1.1.0-SNAPSHOT", "1.1.0",
                        SOURCE, "SYSTEM", now, BranchCreationMode.AUTO);
                bindings.attach(ReleaseWindowId.of(windowId), IterationKey.of(ITERATION), now);
                bindings.updateReleaseBranch(windowId, ITERATION, TARGET, now);
            });
        }

        private static String environment(String name) {
            return java.util.Objects.requireNonNull(System.getenv(name), name);
        }
    }
}
