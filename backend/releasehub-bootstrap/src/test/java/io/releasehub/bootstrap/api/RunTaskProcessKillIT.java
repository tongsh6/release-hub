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

    @ParameterizedTest(name = "rejectedMergeRestart={0}")
    @ValueSource(strings = {"permission", "no_commits"})
    @Timeout(180)
    void b14RestartingAfterRejectedMergeProjectsTheOriginalResultWithoutHttp(String rejection) throws Exception {
        boolean noCommits = rejection.equals("no_commits");
        int status = noCommits ? 422 : 403;
        String reason = noCommits ? "NO_COMMITS" : "PERMISSION_DENIED";
        String result = noCommits ? "UNKNOWN" : "MERGE_BLOCKED";
        String taskStatus = noCommits ? "UNKNOWN" : "FAILED";
        String runStatus = noCommits ? "NEEDS_REVIEW" : "FAILED";
        GitLabHttpFixture server = new GitLabHttpFixture();
        Process first = null;
        Process restarted = null;
        try (var db = new PostgreSQLContainer<>("postgres:18.1")
                .withDatabaseName("rejected_merge_restart").withUsername("process_test").withPassword("isolated-process-db")) {
            db.start();
            server.start();
            routes(server);
            String root = "/api/v4/projects/" + PROJECT_ID;
            server.reply("PUT", root + "/merge_requests/7/merge", status,
                    noCommits ? "{\"message\":\"No commits between source and target\"}" : "{\"message\":\"Forbidden\"}");
            String windowId = UUID.randomUUID().toString();
            String repoId = UUID.randomUUID().toString();
            Path marker = temporary.resolve("rejected-checkpoint.txt");
            Path projected = marker.resolveSibling("projection-committed.txt");
            Path release = marker.resolveSibling("projection-release.txt");
            Path receipt = temporary.resolve("rejected-receipt.txt");
            Path firstLog = temporary.resolve("rejected-first.log");
            first = launch("first-rejected-merge", db, server, windowId, repoId, marker, receipt, firstLog);
            awaitCheckpoint(first, marker, firstLog);

            JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                    db.getJdbcUrl(), db.getUsername(), db.getPassword()));
            String runId = Files.readString(marker).trim();
            var before = tasks(jdbc, runId);
            var originalTask = before.getLast();
            assertThat(before).extracting(row -> row.get("status"))
                    .containsExactly("COMPLETED", "COMPLETED", "COMPLETED", "RUNNING");
            assertThat(originalTask.get("claim_token")).isNotNull();
            assertThat(originalTask.get("claim_expires_at")).isNotNull();
            var originalSteps = jdbc.queryForList("select s.* from run_step s join run_task t on t.id=s.task_id "
                    + "where t.run_id=? order by t.task_order", runId);
            assertThat(originalSteps).hasSize(3);
            var originalOrder = jdbc.queryForMap("select id,planned_order,executed_order from run_item where run_id=?", runId);
            var originalMetadata = metadata(jdbc, runId);
            assertThat(originalMetadata).containsEntry("git.projectId", PROJECT_ID)
                    .containsEntry("git.mrIid", "7").containsEntry("git.sourceSha", SOURCE_SHA)
                    .containsEntry("git.sourceBranch", SOURCE).containsEntry("git.targetBranch", TARGET)
                    .containsEntry("git.mrDisposition", "CREATED").doesNotContainKey("git.mergeSha");
            var operations = jdbc.queryForList("select * from git_operation where run_id=? order by id", runId);
            assertThat(operations).hasSize(2);
            var originalOperation = operations.stream().filter(row -> originalTask.get("id").equals(row.get("task_id")))
                    .findFirst().orElseThrow();
            assertThat(originalOperation).containsEntry("state", "REJECTED").containsEntry("reason", reason)
                    .containsEntry("action", "MERGE_MR").containsEntry("claim_token", originalTask.get("claim_token"));
            String operationId = originalOperation.get("id").toString();
            var originalEvents = mergeEvents(jdbc, operationId);
            assertThat(originalEvents).extracting(row -> row.get("event_type"))
                    .containsExactly("INTENT_RECORDED", "SEND_STARTED", "REJECTED");
            assertThat(originalEvents).extracting(row -> row.get("event_sequence")).containsExactly(0, 1, 2);
            assertThat(originalEvents).allSatisfy(event -> assertThat(event.get("attempt_id"))
                    .isEqualTo(originalOperation.get("attempt_id")));
            assertThat(originalEvents.getLast()).containsEntry("reason", reason);
            var json = new com.fasterxml.jackson.databind.ObjectMapper();
            assertThat(json.readTree(originalEvents.getLast().get("evidence_json").toString()))
                    .isEqualTo(json.createObjectNode().put("httpStatus", status).put("rejectionReason", reason));
            var sender = json.readTree(originalEvents.get(1).get("evidence_json").toString());
            assertThat(sender.path("taskId").textValue()).isEqualTo(originalTask.get("id"));
            assertThat(sender.path("claimToken").textValue()).isEqualTo(originalTask.get("claim_token"));
            var allEvents = jdbc.queryForList("select e.* from git_operation_event e join git_operation o on o.id=e.operation_id "
                    + "where o.run_id=? order by o.id,e.event_sequence", runId);
            assertThat(allEvents).hasSize(6);
            String remoteKey = originalOperation.get("remote_key").toString();
            var guard = jdbc.queryForMap("select * from repo_execution_guard where remote_key=?", remoteKey);
            assertThat(guard).containsEntry("operation_id", operationId).containsEntry("reason", null);
            assertThat(jdbc.queryForObject("select execution_status from run where id=?", String.class, runId)).isEqualTo("RUNNING");
            server.verify("PUT", root + "/merge_requests/7/merge", 1);

            long killedPid = first.pid();
            first.destroyForcibly();
            assertThat(first.waitFor(10, TimeUnit.SECONDS)).isTrue();
            assertThat(first.exitValue()).isNotZero();
            server.reply("GET", root + "/merge_requests/7", 404, "{}");
            int requestsBeforeRestart = server.requests.size();
            Path restartedLog = temporary.resolve("rejected-restarted.log");
            restarted = launch("resume-rejected-merge-" + rejection, db, server, windowId, repoId, marker, receipt, restartedLog);
            awaitCheckpoint(restarted, projected, restartedLog);

            assertThat(Files.readString(projected).trim()).isEqualTo(runId);
            assertThat(server.requests).as("default worker restores known rejection without any HTTP").hasSize(requestsBeforeRestart);
            var after = tasks(jdbc, runId);
            assertThat(after.subList(0, 3)).isEqualTo(before.subList(0, 3));
            assertThat(after.getLast()).containsEntry("status", taskStatus);
            for (String field : List.of("id", "task_type", "claim_token", "started_at", "claim_expires_at")) {
                assertThat(after.getLast().get(field)).as("preserve rejected original task " + field)
                        .isEqualTo(originalTask.get(field));
            }
            assertThat(after.getLast().get("finished_at")).isNotNull();
            assertThat(jdbc.queryForMap("select id,planned_order,executed_order from run_item where run_id=?", runId))
                    .isEqualTo(originalOrder);
            var completedSteps = jdbc.queryForList("select s.* from run_step s join run_task t on t.id=s.task_id "
                    + "where t.run_id=? order by t.task_order", runId);
            assertThat(completedSteps).hasSize(4);
            assertThat(completedSteps.subList(0, 3)).isEqualTo(originalSteps);
            assertThat(completedSteps.getLast()).containsEntry("task_id", originalTask.get("id"))
                    .containsEntry("action_type", "TRY_MERGE").containsEntry("result", result);
            assertThat(jdbc.queryForObject("select final_result from run_item where run_id=?", String.class, runId)).isEqualTo(result);
            assertThat(jdbc.queryForObject("select execution_status from run where id=?", String.class, runId)).isEqualTo(runStatus);
            var finishedAt = jdbc.queryForObject("select finished_at from run where id=?", java.sql.Timestamp.class, runId);
            if (noCommits) assertThat(finishedAt).isNull();
            else assertThat(finishedAt).isNotNull();
            var expectedMetadata = new java.util.LinkedHashMap<>(originalMetadata);
            expectedMetadata.put("git.blockReason", noCommits ? "UNKNOWN" : "PERMISSION_DENIED");
            expectedMetadata.remove("git.requestId");
            assertThat(metadata(jdbc, runId)).isEqualTo(expectedMetadata);
            assertThat(jdbc.queryForObject("select last_merge_at from window_iteration where window_id=? and iteration_key=?",
                    java.sql.Timestamp.class, windowId, ITERATION)).isNull();
            assertThat(jdbc.queryForList("select * from git_operation where run_id=? order by id", runId)).isEqualTo(operations);
            assertThat(jdbc.queryForList("select e.* from git_operation_event e join git_operation o on o.id=e.operation_id "
                    + "where o.run_id=? order by o.id,e.event_sequence", runId)).isEqualTo(allEvents);
            assertThat(jdbc.queryForMap("select * from repo_execution_guard where remote_key=?", remoteKey))
                    .containsEntry("generation", guard.get("generation")).containsEntry("session_id", null)
                    .containsEntry("operation_id", null).containsEntry("reason", null);
            Files.writeString(release, "continue");
            assertThat(restarted.waitFor(55, TimeUnit.SECONDS))
                    .withFailMessage("Rejected recovery JVM did not finish: %s", tail(restartedLog)).isTrue();
            assertThat(restarted.exitValue()).withFailMessage("Rejected recovery JVM failed: %s", tail(restartedLog)).isZero();
            assertThat(Files.readString(receipt).trim()).isEqualTo(runId);
            assertThat(jdbc.queryForObject("select count(*) from run", Integer.class)).isEqualTo(1);
            assertThat(server.requests).hasSize(requestsBeforeRestart);
            server.verify("POST", root + "/merge_requests", 1);
            server.verify("PUT", root + "/merge_requests/7/merge", 1);
            assertThat(server.body("PUT", root + "/merge_requests/7/merge").path("sha").textValue()).isEqualTo(SOURCE_SHA);
            System.out.printf("PROCESS_REJECTED_MERGE_RESUME case=%s run=%s task=%s killedPid=%d exit=%d resumedPid=%d "
                            + "projectionHttp=0 mergePut=1 noPendingAtKill=true preservedToken=true preservedEvents=6 result=%s runStatus=%s%n",
                    rejection, runId, originalTask.get("id"), killedPid, first.exitValue(), restarted.pid(), result, runStatus);
        } finally {
            stopOwnedProcess(first);
            stopOwnedProcess(restarted);
            server.stop();
        }
    }

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

    @ParameterizedTest(name = "branchIntentRestart={0}")
    @ValueSource(strings = {"feature", "release", "feature_applied_crash"})
    @Timeout(240)
    void branchIntentRestartUsesFrozenRequest(String scenario) throws Exception {
        boolean releaseAction = scenario.equals("release");
        boolean crashAfterApplied = scenario.equals("feature_applied_crash");
        ActionType action = releaseAction ? ActionType.ENSURE_RELEASE : ActionType.ENSURE_FEATURE;
        int index = releaseAction ? 1 : 0;
        String branch = releaseAction ? TARGET : SOURCE;
        GitLabHttpFixture server = new GitLabHttpFixture();
        Process first = null, resumed = null, projected = null;
        try (var db = new PostgreSQLContainer<>("postgres:18.1")
                .withDatabaseName("branch_intent_restart").withUsername("process_test").withPassword("isolated-process-db")) {
            db.start(); server.start(); routes(server);
            String root = "/api/v4/projects/" + PROJECT_ID;
            String branchPath = root + "/repository/branches/" + URLEncoder.encode(branch, StandardCharsets.UTF_8);
            server.reply("GET", branchPath, 404, "{}");
            server.reply("GET", root + "/repository/commits/main", 200, "{\"id\":\"" + SOURCE_SHA + "\"}");
            server.reply("GET", root + "/repository/branches/main", 200, branchIntentResponse("main", SOURCE_SHA));
            server.reply("POST", root + "/repository/branches", 201, branchIntentResponse(branch, SOURCE_SHA));
            String windowId = UUID.randomUUID().toString(), repoId = UUID.randomUUID().toString();
            Path marker = temporary.resolve("branch-original-intent.txt");
            Path appliedMarker = marker.resolveSibling("resumed-branch-applied.txt");
            Path receipt = temporary.resolve("branch-intent-receipt.txt");
            Path firstLog = temporary.resolve("branch-intent-first.log");
            first = launch("first-intent-branch-" + (releaseAction ? "release" : "feature"),
                    db, server, windowId, repoId, marker, receipt, firstLog);
            awaitCheckpoint(first, marker, firstLog);
            JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword()));
            String runId = Files.readString(marker).trim();
            var originalTasks = tasks(jdbc, runId);
            var originalTask = originalTasks.get(index);
            String taskId = originalTask.get("id").toString();
            int originalTaskOrder = jdbc.queryForObject("select task_order from run_task where id=?", Integer.class, taskId);
            var originalOperation = jdbc.queryForMap("select * from git_operation where task_id=?", taskId);
            String operationId = originalOperation.get("id").toString();
            var originalEvents = mergeEvents(jdbc, operationId);
            var originalMetadata = metadata(jdbc, runId);
            var originalOrder = jdbc.queryForMap("select planned_order,executed_order from run_item where run_id=?", runId);
            var originalGuard = jdbc.queryForMap("select * from repo_execution_guard where remote_key=?", originalOperation.get("remote_key"));
            assertThat(originalTasks).extracting(row -> row.get("status")).containsExactlyElementsOf(releaseAction
                    ? List.of("COMPLETED", "RUNNING", "PENDING", "PENDING") : List.of("RUNNING", "PENDING", "PENDING", "PENDING"));
            assertThat(originalOperation).containsEntry("state", "INTENT_RECORDED").containsEntry("action", "CREATE_BRANCH")
                    .containsEntry("claim_token", originalTask.get("claim_token")).containsEntry("session_id", originalTask.get("claim_token"));
            assertThat(originalEvents).singleElement().satisfies(event -> assertThat(event)
                    .containsEntry("event_sequence", 0).containsEntry("event_type", "INTENT_RECORDED")
                    .containsEntry("attempt_id", originalOperation.get("attempt_id")));
            assertThat(originalGuard).containsEntry("operation_id", operationId)
                    .containsEntry("session_id", originalTask.get("claim_token")).containsEntry("reason", "INTENT_PENDING");
            server.verify("POST", root + "/repository/branches", 0);
            long killedPid = first.pid();
            first.destroyForcibly();
            assertThat(first.waitFor(10, TimeUnit.SECONDS)).isTrue();
            assertThat(first.exitValue()).isEqualTo(137);
            System.out.printf("PROCESS_BRANCH_INTENT_CHECKPOINT action=%s run=%s task=%s operation=%s attempt=%s "
                            + "pid=%d exit=%d pendingSuccessors=%d branchPost=0%n",
                    action, runId, taskId, operationId, originalOperation.get("attempt_id"), killedPid, first.exitValue(), 3 - index);
            server.reply("GET", root + "/repository/commits/main", 200, "{\"id\":\"" + "b".repeat(40) + "\"}");
            server.reply("GET", root + "/repository/branches/main", 200, branchIntentResponse("main", "b".repeat(40)));
            int requestsBeforeResume = server.requests.size();
            Path resumedLog = temporary.resolve("branch-intent-resumed.log");
            resumed = launch("resume-intent-branch-" + (releaseAction ? "release" : "feature") + (crashAfterApplied ? "-crash" : ""),
                    db, server, windowId, repoId, marker, receipt, resumedLog);
            awaitCheckpoint(resumed, appliedMarker, resumedLog);
            assertThat(Files.readString(appliedMarker).trim()).isEqualTo(runId);
            var appliedTask = tasks(jdbc, runId).get(index);
            String currentToken = appliedTask.get("claim_token").toString();
            assertThat(currentToken).isNotEqualTo(originalTask.get("claim_token"));
            assertThat(appliedTask).containsEntry("status", "RUNNING").containsEntry("started_at", originalTask.get("started_at"));
            assertThat(jdbc.queryForObject("select task_order from run_task where id=?", Integer.class, taskId)).isEqualTo(originalTaskOrder);
            var appliedOperation = jdbc.queryForMap("select * from git_operation where task_id=?", taskId);
            var appliedEvents = mergeEvents(jdbc, operationId);
            assertThat(appliedOperation).containsEntry("state", "APPLIED");
            assertThat(operationProvenance(appliedOperation)).isEqualTo(operationProvenance(originalOperation));
            assertThat(appliedEvents.getFirst()).isEqualTo(originalEvents.getFirst());
            assertSenderEvidence(appliedEvents, originalOperation, taskId, currentToken);
            assertThat(metadata(jdbc, runId)).isEqualTo(originalMetadata);
            assertThat(jdbc.queryForObject("select count(*) from run_step where task_id=?", Integer.class, taskId)).isZero();
            assertThat(jdbc.queryForMap("select planned_order,executed_order from run_item where run_id=?", runId)).isEqualTo(originalOrder);
            branchIntentAssertFrozenRequest(server, jdbc, operationId, branch);
            assertThat(server.requests.subList(requestsBeforeResume, server.requests.size()))
                    .noneMatch(request -> request.path().equals(root + "/repository/commits/main")
                            || request.path().equals(root + "/repository/branches/main"));
            server.reply("GET", branchPath, 200, branchIntentResponse(branch, SOURCE_SHA));
            if (releaseAction) {
                // A release created at A already contains the existing feature at A.
                server.reply("GET", root + "/repository/commits/" + SOURCE_SHA + "/refs", 200,
                        "[{\"type\":\"branch\",\"name\":\"" + TARGET + "\"}]");
            }

            Process completionProcess = resumed;
            Path completionLog = resumedLog;
            if (crashAfterApplied) {
                long secondPid = resumed.pid();
                resumed.destroyForcibly();
                assertThat(resumed.waitFor(10, TimeUnit.SECONDS)).isTrue();
                assertThat(resumed.exitValue()).isEqualTo(137);
                int requestsBeforeProjection = server.requests.size();
                Path projectedLog = temporary.resolve("branch-intent-projected.log");
                projected = launch("project-intent-branch-feature", db, server, windowId, repoId, marker, receipt, projectedLog);
                awaitCheckpoint(projected, marker.resolveSibling("projection-committed.txt"), projectedLog);
                assertThat(tasks(jdbc, runId).get(index)).containsEntry("status", "COMPLETED")
                        .containsEntry("claim_token", currentToken).containsEntry("started_at", originalTask.get("started_at"));
                assertThat(jdbc.queryForMap("select * from git_operation where task_id=?", taskId)).isEqualTo(appliedOperation);
                assertThat(mergeEvents(jdbc, operationId)).isEqualTo(appliedEvents);
                var projectedMetadata = new java.util.LinkedHashMap<>(originalMetadata);
                projectedMetadata.put("git.projectId", PROJECT_ID);
                assertThat(metadata(jdbc, runId)).isEqualTo(projectedMetadata);
                assertThat(server.requests).hasSize(requestsBeforeProjection);
                assertThat(jdbc.queryForMap("select * from repo_execution_guard where remote_key=?", originalOperation.get("remote_key")))
                        .containsEntry("session_id", null).containsEntry("operation_id", null).containsEntry("reason", null);
                System.out.printf("PROCESS_BRANCH_INTENT_APPLIED_PROJECTION run=%s task=%s operation=%s secondPid=%d "
                                + "secondExit=%d projectedPid=%d projectionHttp=0 currentTokenPreserved=true events=%d%n",
                        runId, taskId, operationId, secondPid, resumed.exitValue(), projected.pid(), appliedEvents.size());
                Files.writeString(marker.resolveSibling("projection-release.txt"), "continue");
                completionProcess = projected;
                completionLog = projectedLog;
            } else {
                Files.writeString(marker.resolveSibling("branch-completion-release.txt"), "continue");
            }
            assertThat(completionProcess.waitFor(55, TimeUnit.SECONDS))
                    .withFailMessage("Branch continuation JVM did not finish: %s", tail(completionLog)).isTrue();
            assertThat(completionProcess.exitValue()).withFailMessage("Branch continuation failed: %s", tail(completionLog)).isZero();
            assertThat(Files.readString(receipt).trim()).isEqualTo(runId);
            var completedTasks = tasks(jdbc, runId);
            assertThat(completedTasks).extracting(row -> row.get("id")).containsExactlyElementsOf(
                    originalTasks.stream().map(row -> row.get("id")).toList());
            assertThat(completedTasks).allSatisfy(task -> assertThat(task.get("status")).isEqualTo("COMPLETED"));
            assertThat(completedTasks.get(index)).containsEntry("claim_token", currentToken)
                    .containsEntry("started_at", originalTask.get("started_at"));
            assertThat(operationProvenance(jdbc.queryForMap("select * from git_operation where task_id=?", taskId)))
                    .isEqualTo(operationProvenance(originalOperation));
            assertThat(mergeEvents(jdbc, operationId)).isEqualTo(appliedEvents);
            assertThat(jdbc.queryForList("select result from run_step where task_id=?", taskId))
                    .singleElement().satisfies(row -> assertThat(row).containsEntry("result", "CREATED"));
            assertThat(metadata(jdbc, runId)).containsEntry("git.projectId", PROJECT_ID);
            assertThat(jdbc.queryForObject("select execution_status from run where id=?", String.class, runId)).isEqualTo("DONE");
            assertThat(jdbc.queryForObject("select count(*) from git_operation where task_id=?", Integer.class, taskId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from run", Integer.class)).isEqualTo(1);
            server.verify("POST", root + "/repository/branches", 1);
            System.out.printf("PROCESS_BRANCH_INTENT_RESULT action=%s run=%s task=%s operation=%s attempt=%s killedPid=%d "
                            + "exit=%d resumedPid=%d branchPost=1 refA=true originalOwnerPreserved=true events=%d crashAgain=%s%n",
                    action, runId, taskId, operationId, originalOperation.get("attempt_id"), killedPid, first.exitValue(),
                    resumed.pid(), appliedEvents.size(), crashAfterApplied);
        } finally {
            stopOwnedProcess(first); stopOwnedProcess(resumed); stopOwnedProcess(projected); server.stop();
        }
    }

    @org.junit.jupiter.api.Test
    @Timeout(180)
    void branchIntentSendRestartDoesNotReplay() throws Exception {
        GitLabHttpFixture server = new GitLabHttpFixture();
        Process first = null, observed = null;
        try (var db = new PostgreSQLContainer<>("postgres:18.1")
                .withDatabaseName("branch_send_restart").withUsername("process_test").withPassword("isolated-process-db")) {
            db.start(); server.start(); routes(server);
            String root = "/api/v4/projects/" + PROJECT_ID;
            server.reply("GET", root + "/repository/branches/" + URLEncoder.encode(TARGET, StandardCharsets.UTF_8), 404, "{}");
            server.reply("GET", root + "/repository/commits/main", 200, "{\"id\":\"" + SOURCE_SHA + "\"}");
            server.reply("GET", root + "/repository/branches/main", 200, branchIntentResponse("main", SOURCE_SHA));
            server.reply("POST", root + "/repository/branches", 201, branchIntentResponse(TARGET, SOURCE_SHA));
            String windowId = UUID.randomUUID().toString(), repoId = UUID.randomUUID().toString();
            Path marker = temporary.resolve("branch-send-original.txt"), receipt = temporary.resolve("branch-send-receipt.txt");
            Path firstLog = temporary.resolve("branch-send-first.log");
            first = launch("first-sent-branch-release", db, server, windowId, repoId, marker, receipt, firstLog);
            awaitCheckpoint(first, marker, firstLog);
            JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword()));
            String runId = Files.readString(marker).trim();
            var originalTasks = tasks(jdbc, runId);
            String taskId = originalTasks.get(1).get("id").toString();
            var originalOperation = jdbc.queryForMap("select * from git_operation where task_id=?", taskId);
            String operationId = originalOperation.get("id").toString();
            var originalEvents = mergeEvents(jdbc, operationId);
            var originalMetadata = metadata(jdbc, runId);
            var originalGuard = jdbc.queryForMap("select * from repo_execution_guard where remote_key=?", originalOperation.get("remote_key"));
            var originalSteps = jdbc.queryForList("select s.* from run_step s join run_task t on s.task_id=t.id where t.run_id=? order by t.task_order", runId);
            assertThat(originalTasks).extracting(task -> task.get("status")).containsExactly("COMPLETED", "RUNNING", "PENDING", "PENDING");
            assertThat(originalOperation).containsEntry("state", "SEND_STARTED").containsEntry("action", "CREATE_BRANCH");
            assertThat(originalEvents).extracting(event -> event.get("event_type")).containsExactly("INTENT_RECORDED", "SEND_STARTED");
            assertThat(originalGuard).containsEntry("operation_id", operationId).containsEntry("reason", "REQUEST_IN_FLIGHT");
            server.verify("POST", root + "/repository/branches", 0);
            long killedPid = first.pid();
            first.destroyForcibly();
            assertThat(first.waitFor(10, TimeUnit.SECONDS)).isTrue();
            assertThat(first.exitValue()).isEqualTo(137);
            int requestsBeforeRestart = server.requests.size();
            Path observedLog = temporary.resolve("branch-send-observed.log");
            observed = launch("observe-sent-branch-release", db, server, windowId, repoId, marker, receipt, observedLog);
            assertThat(observed.waitFor(55, TimeUnit.SECONDS)).withFailMessage("Branch SEND observer timed out: %s", tail(observedLog)).isTrue();
            assertThat(observed.exitValue()).withFailMessage("Branch SEND observer failed: %s", tail(observedLog)).isZero();
            int scans = Integer.parseInt(Files.readString(marker.resolveSibling("branch-send-scans.txt")).trim());
            assertThat(scans).isGreaterThanOrEqualTo(3);
            assertThat(tasks(jdbc, runId)).isEqualTo(originalTasks);
            assertThat(jdbc.queryForMap("select * from git_operation where task_id=?", taskId)).isEqualTo(originalOperation);
            assertThat(mergeEvents(jdbc, operationId)).isEqualTo(originalEvents);
            assertThat(metadata(jdbc, runId)).isEqualTo(originalMetadata);
            assertThat(jdbc.queryForMap("select * from repo_execution_guard where remote_key=?", originalOperation.get("remote_key"))).isEqualTo(originalGuard);
            assertThat(jdbc.queryForList("select s.* from run_step s join run_task t on s.task_id=t.id where t.run_id=? order by t.task_order", runId)).isEqualTo(originalSteps);
            assertThat(server.requests).hasSize(requestsBeforeRestart);
            server.verify("POST", root + "/repository/branches", 0);
            assertThat(Files.readString(receipt).trim()).isEqualTo(runId);
            System.out.printf("PROCESS_BRANCH_SEND_NO_REPLAY run=%s task=%s operation=%s attempt=%s killedPid=%d exit=%d "
                            + "observedPid=%d completedScans=%d branchPost=0 observationHttp=0 originalTaskPreserved=true%n",
                    runId, taskId, operationId, originalOperation.get("attempt_id"), killedPid, first.exitValue(), observed.pid(), scans);
        } finally { stopOwnedProcess(first); stopOwnedProcess(observed); server.stop(); }
    }

    @ParameterizedTest(name = "createMrIntentShaLength={0}")
    @ValueSource(ints = {40, 64})
    @Timeout(240)
    void createMrIntentRestartPreservesOriginalPostAndProjectsAppliedResult(int shaLength) throws Exception {
        String frozenSha = "aBcD".repeat(shaLength / 4);
        boolean crashAfterApplied = shaLength == 64;
        GitLabHttpFixture server = new GitLabHttpFixture();
        Process first = null, resumed = null, projected = null;
        try (var db = new PostgreSQLContainer<>("postgres:18.1")
                .withDatabaseName("create_mr_intent_restart").withUsername("process_test").withPassword("isolated-process-db")) {
            db.start(); server.start(); routes(server, frozenSha);
            String root = "/api/v4/projects/" + PROJECT_ID;
            String windowId = UUID.randomUUID().toString(), repoId = UUID.randomUUID().toString();
            Path marker = temporary.resolve("create-mr-intent-checkpoint.txt"), receipt = temporary.resolve("create-mr-intent-receipt.txt");
            Path appliedMarker = marker.resolveSibling("resumed-create-mr-applied.txt");
            Path firstLog = temporary.resolve("create-mr-intent-first.log");
            first = launch("first-intent-create-mr", db, server, windowId, repoId, marker, receipt, firstLog);
            awaitCheckpoint(first, marker, firstLog);
            JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword()));
            String runId = Files.readString(marker).trim();
            var originalTasks = tasks(jdbc, runId);
            var originalTask = originalTasks.get(2);
            String taskId = originalTask.get("id").toString();
            var originalOperation = jdbc.queryForMap("select * from git_operation where task_id=?", taskId);
            String operationId = originalOperation.get("id").toString();
            var originalEvents = mergeEvents(jdbc, operationId);
            var originalMetadata = metadata(jdbc, runId);
            var originalSteps = jdbc.queryForList("select s.* from run_step s join run_task t on s.task_id=t.id where t.run_id=? order by t.task_order", runId);
            var originalOrder = jdbc.queryForMap("select planned_order,executed_order from run_item where run_id=?", runId);
            assertThat(originalTasks).extracting(task -> task.get("status")).containsExactly("COMPLETED", "COMPLETED", "RUNNING", "PENDING");
            assertThat(originalOperation).containsEntry("action", "CREATE_MR").containsEntry("state", "INTENT_RECORDED")
                    .containsEntry("project_id", PROJECT_ID).containsEntry("claim_token", originalTask.get("claim_token"))
                    .containsEntry("session_id", originalTask.get("claim_token"));
            assertThat(originalEvents).singleElement().satisfies(event -> assertThat(event)
                    .containsEntry("event_type", "INTENT_RECORDED").containsEntry("event_sequence", 0)
                    .containsEntry("attempt_id", originalOperation.get("attempt_id")));
            assertThat(originalSteps).hasSize(2);
            assertThat(jdbc.queryForMap("select * from repo_execution_guard where remote_key=?", originalOperation.get("remote_key")))
                    .containsEntry("operation_id", operationId).containsEntry("reason", "INTENT_PENDING");
            assertThat(server.writeCount()).isZero();
            long firstPid = first.pid();
            first.destroyForcibly();
            assertThat(first.waitFor(10, TimeUnit.SECONDS)).isTrue();
            assertThat(first.exitValue()).isEqualTo(137);

            Path resumedLog = temporary.resolve("create-mr-intent-resumed.log");
            resumed = launch("resume-intent-create-mr", db, server, windowId, repoId, marker, receipt, resumedLog);
            awaitCheckpoint(resumed, appliedMarker, resumedLog);
            assertThat(Files.readString(appliedMarker).trim()).isEqualTo(runId);
            var appliedTask = tasks(jdbc, runId).get(2);
            String currentToken = appliedTask.get("claim_token").toString();
            var appliedOperation = jdbc.queryForMap("select * from git_operation where task_id=?", taskId);
            var appliedEvents = mergeEvents(jdbc, operationId);
            assertThat(currentToken).isNotEqualTo(originalTask.get("claim_token"));
            assertThat(appliedTask).containsEntry("status", "RUNNING").containsEntry("started_at", originalTask.get("started_at"));
            assertThat(appliedOperation).containsEntry("state", "APPLIED");
            assertThat(operationProvenance(appliedOperation)).isEqualTo(operationProvenance(originalOperation));
            assertThat(appliedEvents.getFirst()).isEqualTo(originalEvents.getFirst());
            assertSenderEvidence(appliedEvents, originalOperation, taskId, currentToken);
            assertThat(metadata(jdbc, runId)).isEqualTo(originalMetadata);
            assertThat(jdbc.queryForObject("select count(*) from run_step where task_id=?", Integer.class, taskId)).isZero();
            assertThat(jdbc.queryForMap("select planned_order,executed_order from run_item where run_id=?", runId)).isEqualTo(originalOrder);
            createMrIntentAssertFrozenRequest(server, jdbc, operationId, frozenSha);

            Process completing = resumed;
            Path completionLog = resumedLog;
            if (crashAfterApplied) {
                long secondPid = resumed.pid();
                resumed.destroyForcibly();
                assertThat(resumed.waitFor(10, TimeUnit.SECONDS)).isTrue();
                assertThat(resumed.exitValue()).isEqualTo(137);
                int requestsBeforeProjection = server.requests.size();
                Path projectedLog = temporary.resolve("create-mr-intent-projected.log");
                projected = launch("project-intent-create-mr", db, server, windowId, repoId, marker, receipt, projectedLog);
                awaitCheckpoint(projected, marker.resolveSibling("projection-committed.txt"), projectedLog);
                assertThat(tasks(jdbc, runId).get(2)).containsEntry("status", "COMPLETED")
                        .containsEntry("claim_token", currentToken).containsEntry("started_at", originalTask.get("started_at"));
                assertThat(tasks(jdbc, runId).getLast()).containsEntry("status", "PENDING");
                assertThat(jdbc.queryForMap("select * from git_operation where task_id=?", taskId)).isEqualTo(appliedOperation);
                assertThat(mergeEvents(jdbc, operationId)).isEqualTo(appliedEvents);
                assertThat(metadata(jdbc, runId)).containsEntry("git.projectId", PROJECT_ID).containsEntry("git.sourceSha", frozenSha)
                        .containsEntry("git.sourceBranch", SOURCE).containsEntry("git.targetBranch", TARGET)
                        .containsEntry("git.mrIid", "7").containsEntry("git.mrDisposition", "CREATED");
                assertThat(server.requests).hasSize(requestsBeforeProjection);
                assertThat(jdbc.queryForMap("select * from repo_execution_guard where remote_key=?", originalOperation.get("remote_key")))
                        .containsEntry("session_id", null).containsEntry("operation_id", null).containsEntry("reason", null);
                System.out.printf("PROCESS_CREATE_MR_INTENT_PROJECTION run=%s task=%s operation=%s secondPid=%d "
                        + "secondExit=%d projectedPid=%d projectionHttp=0 currentTokenPreserved=true%n",
                        runId, taskId, operationId, secondPid, resumed.exitValue(), projected.pid());
                Files.writeString(marker.resolveSibling("projection-release.txt"), "continue");
                completing = projected;
                completionLog = projectedLog;
            } else {
                Files.writeString(marker.resolveSibling("create-mr-completion-release.txt"), "continue");
            }
            assertThat(completing.waitFor(55, TimeUnit.SECONDS)).withFailMessage("CREATE_MR continuation timed out: %s", tail(completionLog)).isTrue();
            assertThat(completing.exitValue()).withFailMessage("CREATE_MR continuation failed: %s", tail(completionLog)).isZero();
            assertThat(Files.readString(receipt).trim()).isEqualTo(runId);
            var completedTasks = tasks(jdbc, runId);
            assertThat(completedTasks.subList(0, 2)).isEqualTo(originalTasks.subList(0, 2));
            assertThat(completedTasks).extracting(task -> task.get("id")).containsExactlyElementsOf(originalTasks.stream().map(task -> task.get("id")).toList());
            assertThat(completedTasks).allSatisfy(task -> assertThat(task.get("status")).isEqualTo("COMPLETED"));
            assertThat(completedTasks.get(2)).containsEntry("claim_token", currentToken).containsEntry("started_at", originalTask.get("started_at"));
            assertThat(jdbc.queryForMap("select * from git_operation where task_id=?", taskId)).isEqualTo(appliedOperation);
            assertThat(mergeEvents(jdbc, operationId)).isEqualTo(appliedEvents);
            var completedSteps = jdbc.queryForList("select s.* from run_step s join run_task t on s.task_id=t.id where t.run_id=? order by t.task_order", runId);
            assertThat(completedSteps).hasSize(4);
            assertThat(completedSteps.subList(0, 2)).isEqualTo(originalSteps);
            assertThat(completedSteps.get(2)).containsEntry("task_id", taskId).containsEntry("result", "CREATED");
            assertThat(completedSteps.getLast()).containsEntry("result", "MERGED");
            assertThat(metadata(jdbc, runId)).containsEntry("git.projectId", PROJECT_ID).containsEntry("git.sourceSha", frozenSha);
            assertThat(jdbc.queryForObject("select execution_status from run where id=?", String.class, runId)).isEqualTo("DONE");
            assertThat(jdbc.queryForObject("select count(*) from git_operation where task_id=?", Integer.class, taskId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from run", Integer.class)).isEqualTo(1);
            server.verify("POST", root + "/merge_requests", 1);
            server.verify("PUT", root + "/merge_requests/7/merge", 1);
            assertThat(server.body("PUT", root + "/merge_requests/7/merge").path("sha").textValue()).isEqualTo(frozenSha);
            assertThat(server.writeCount()).isEqualTo(2);
            System.out.printf("PROCESS_CREATE_MR_INTENT_RESULT shaLength=%d run=%s task=%s operation=%s attempt=%s "
                    + "firstPid=%d firstExit=%d resumedPid=%d projectedPid=%d originalBytes=true mrPost=1 mergePut=1%n",
                    shaLength, runId, taskId, operationId, originalOperation.get("attempt_id"), firstPid, first.exitValue(),
                    resumed.pid(), projected == null ? -1 : projected.pid());
        } finally { stopOwnedProcess(first); stopOwnedProcess(resumed); stopOwnedProcess(projected); server.stop(); }
    }

    @ParameterizedTest(name = "createMrIntentLocal={0}")
    @ValueSource(strings = {"contained", "source_drift"})
    @Timeout(240)
    void createMrIntentRestartPreservesCommittedNotSentResult(String observation) throws Exception {
        String frozenSha = "aBcD".repeat(10);
        boolean contained = observation.equals("contained");
        GitLabHttpFixture server = new GitLabHttpFixture();
        Process first = null, resumed = null, observed = null;
        try (var db = new PostgreSQLContainer<>("postgres:18.1")
                .withDatabaseName("create_mr_local_restart").withUsername("process_test").withPassword("isolated-process-db")) {
            db.start(); server.start(); routes(server, frozenSha);
            String root = "/api/v4/projects/" + PROJECT_ID;
            String sourcePath = root + "/repository/branches/" + URLEncoder.encode(SOURCE, StandardCharsets.UTF_8);
            String windowId = UUID.randomUUID().toString(), repoId = UUID.randomUUID().toString();
            Path marker = temporary.resolve("create-mr-local-original.txt"), receipt = temporary.resolve("create-mr-local-receipt.txt");
            Path firstLog = temporary.resolve("create-mr-local-first.log");
            first = launch("first-intent-create-mr", db, server, windowId, repoId, marker, receipt, firstLog);
            awaitCheckpoint(first, marker, firstLog);
            JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword()));
            String runId = Files.readString(marker).trim();
            var originalTasks = tasks(jdbc, runId);
            var originalTask = originalTasks.get(2);
            String taskId = originalTask.get("id").toString();
            var originalOperation = jdbc.queryForMap("select * from git_operation where task_id=?", taskId);
            String operationId = originalOperation.get("id").toString();
            var originalEvents = mergeEvents(jdbc, operationId);
            var originalMetadata = metadata(jdbc, runId);
            assertThat(originalOperation).containsEntry("state", "INTENT_RECORDED").containsEntry("action", "CREATE_MR");
            assertThat(originalEvents).singleElement().satisfies(event -> assertThat(event).containsEntry("event_type", "INTENT_RECORDED"));
            assertThat(server.writeCount()).isZero();
            long firstPid = first.pid();
            first.destroyForcibly();
            assertThat(first.waitFor(10, TimeUnit.SECONDS)).isTrue();
            assertThat(first.exitValue()).isEqualTo(137);
            server.reply("GET", sourcePath, 200, branchIntentResponse(SOURCE, "b".repeat(40)));
            if (contained) {
                server.reply("GET", root + "/repository/commits/" + frozenSha + "/refs", 200,
                        "[{\"type\":\"branch\",\"name\":\"" + TARGET + "\"}]");
                server.reply("GET", root + "/merge_requests", 503, "{}");
                server.reply("GET", root + "/merge_requests/7", 503, "{}");
            }
            int requestsBeforeResume = server.requests.size();
            Path resumedLog = temporary.resolve("create-mr-local-resumed.log");
            resumed = launch("resume-local-create-mr", db, server, windowId, repoId, marker, receipt, resumedLog);
            awaitCheckpoint(resumed, marker.resolveSibling("create-mr-local-committed.txt"), resumedLog);
            var completedTasks = tasks(jdbc, runId);
            var completedTask = completedTasks.get(2);
            String currentToken = completedTask.get("claim_token").toString();
            String result = contained ? "ALREADY_MERGED" : "UNKNOWN";
            assertThat(currentToken).isNotEqualTo(originalTask.get("claim_token"));
            assertThat(completedTasks.subList(0, 2)).isEqualTo(originalTasks.subList(0, 2));
            assertThat(completedTask).containsEntry("status", contained ? "COMPLETED" : "UNKNOWN")
                    .containsEntry("started_at", originalTask.get("started_at"));
            assertThat(completedTasks.getLast()).containsEntry("status", contained ? "PENDING" : "SKIPPED");
            var completedOperation = jdbc.queryForMap("select * from git_operation where task_id=?", taskId);
            var completedEvents = mergeEvents(jdbc, operationId);
            var patch = createMrIntentNotSentPatch(contained, frozenSha);
            assertThat(completedOperation).containsEntry("state", "NOT_SENT").containsEntry("reason", patch.get("git.blockReason"));
            assertThat(operationProvenance(completedOperation)).isEqualTo(operationProvenance(originalOperation));
            assertThat(completedEvents).extracting(event -> event.get("event_type")).containsExactly("INTENT_RECORDED", "NOT_SENT");
            assertThat(completedEvents.getFirst()).isEqualTo(originalEvents.getFirst());
            assertThat(completedEvents.getLast()).containsEntry("event_sequence", 1).containsEntry("attempt_id", originalOperation.get("attempt_id"));
            var json = new com.fasterxml.jackson.databind.ObjectMapper();
            var event = json.createObjectNode().put("taskId", taskId).put("claimToken", currentToken).put("result", result);
            event.set("metadata", json.valueToTree(patch));
            assertThat(json.readTree(completedEvents.getLast().get("evidence_json").toString())).isEqualTo(event);
            var completedMetadata = new java.util.LinkedHashMap<>(originalMetadata);
            patch.forEach((key, value) -> { if (value == null) completedMetadata.remove(key); else completedMetadata.put(key, value); });
            assertThat(metadata(jdbc, runId)).isEqualTo(completedMetadata);
            var completedGuard = jdbc.queryForMap("select * from repo_execution_guard where remote_key=?", originalOperation.get("remote_key"));
            assertThat(completedGuard).containsEntry("operation_id", null).containsEntry("session_id", null).containsEntry("reason", null);
            assertThat(jdbc.queryForList("select result from run_step where task_id=?", taskId))
                    .singleElement().satisfies(step -> assertThat(step).containsEntry("result", result));
            assertThat(jdbc.queryForObject("select execution_status from run where id=?", String.class, runId))
                    .isEqualTo(contained ? "RUNNING" : "NEEDS_REVIEW");
            assertThat(server.writeCount()).isZero();
            if (contained) {
                assertThat(server.requests.subList(requestsBeforeResume, server.requests.size())).noneMatch(request ->
                        request.path().equals(sourcePath) || request.path().startsWith(root + "/merge_requests"));
            }

            long secondPid = resumed.pid();
            resumed.destroyForcibly();
            assertThat(resumed.waitFor(10, TimeUnit.SECONDS)).isTrue();
            assertThat(resumed.exitValue()).isEqualTo(137);
            int requestsBeforeObservation = server.requests.size();
            Path observedLog = temporary.resolve("create-mr-local-observed.log");
            observed = launch("observe-local-create-mr-" + (contained ? "contained" : "unknown"),
                    db, server, windowId, repoId, marker, receipt, observedLog);
            assertThat(observed.waitFor(55, TimeUnit.SECONDS)).withFailMessage("NOT_SENT observer timed out: %s", tail(observedLog)).isTrue();
            assertThat(observed.exitValue()).withFailMessage("NOT_SENT observer failed: %s", tail(observedLog)).isZero();
            int scans = Integer.parseInt(Files.readString(marker.resolveSibling("create-mr-local-scans.txt")).trim());
            assertThat(scans).isGreaterThanOrEqualTo(3);
            assertThat(Files.readString(receipt).trim()).isEqualTo(runId);
            assertThat(tasks(jdbc, runId).get(2)).isEqualTo(completedTask);
            assertThat(jdbc.queryForMap("select * from git_operation where task_id=?", taskId)).isEqualTo(completedOperation);
            assertThat(mergeEvents(jdbc, operationId)).isEqualTo(completedEvents);
            assertThat(metadata(jdbc, runId)).isEqualTo(completedMetadata);
            assertThat(jdbc.queryForMap("select * from repo_execution_guard where remote_key=?", originalOperation.get("remote_key"))).isEqualTo(completedGuard);
            assertThat(jdbc.queryForObject("select execution_status from run where id=?", String.class, runId))
                    .isEqualTo(contained ? "DONE" : "NEEDS_REVIEW");
            assertThat(jdbc.queryForObject("select count(*) from git_operation where run_id=?", Integer.class, runId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from run", Integer.class)).isEqualTo(1);
            if (contained) {
                assertThat(tasks(jdbc, runId).getLast()).containsEntry("status", "COMPLETED");
                assertThat(server.requests.subList(requestsBeforeObservation, server.requests.size())).noneMatch(request ->
                        request.path().equals(sourcePath) || request.path().startsWith(root + "/merge_requests"));
            } else {
                assertThat(tasks(jdbc, runId)).isEqualTo(completedTasks);
                assertThat(server.requests).hasSize(requestsBeforeObservation);
            }
            assertThat(server.writeCount()).isZero();
            System.out.printf("PROCESS_CREATE_MR_NOT_SENT_RESULT observation=%s run=%s task=%s operation=%s attempt=%s "
                    + "firstPid=%d firstExit=%d secondPid=%d secondExit=%d observedPid=%d scans=%d post=0 guardReleased=true%n",
                    observation, runId, taskId, operationId, originalOperation.get("attempt_id"),
                    firstPid, first.exitValue(), secondPid, resumed.exitValue(), observed.pid(), scans);
        } finally { stopOwnedProcess(first); stopOwnedProcess(resumed); stopOwnedProcess(observed); server.stop(); }
    }

    @ParameterizedTest(name = "createMrIntentUncertain={0}")
    @ValueSource(strings = {"SEND_STARTED", "UNKNOWN"})
    @Timeout(180)
    void createMrIntentRestartNeverReplaysCommittedSend(String checkpoint) throws Exception {
        boolean unknown = checkpoint.equals("UNKNOWN");
        String frozenSha = "aBcD".repeat(10);
        GitLabHttpFixture server = new GitLabHttpFixture();
        Process first = null, observed = null;
        try (var db = new PostgreSQLContainer<>("postgres:18.1")
                .withDatabaseName("create_mr_uncertain_restart").withUsername("process_test").withPassword("isolated-process-db")) {
            db.start(); server.start(); routes(server, frozenSha);
            String root = "/api/v4/projects/" + PROJECT_ID;
            if (unknown) {
                var response = (com.fasterxml.jackson.databind.node.ObjectNode) new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(server.responses.get("POST " + root + "/merge_requests").body());
                server.reply("POST", root + "/merge_requests", 201, response.put("sha", "b".repeat(40)).toString());
            }
            String windowId = UUID.randomUUID().toString(), repoId = UUID.randomUUID().toString();
            Path marker = temporary.resolve("create-mr-uncertain-checkpoint.txt"), receipt = temporary.resolve("create-mr-uncertain-receipt.txt");
            Path firstLog = temporary.resolve("create-mr-uncertain-first.log");
            first = launch(unknown ? "first-unknown-create-mr" : "first-sent-create-mr",
                    db, server, windowId, repoId, marker, receipt, firstLog);
            awaitCheckpoint(first, marker, firstLog);
            JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword()));
            String runId = Files.readString(marker).trim();
            var originalTasks = tasks(jdbc, runId);
            String taskId = originalTasks.get(2).get("id").toString();
            var originalOperation = jdbc.queryForMap("select * from git_operation where task_id=?", taskId);
            String operationId = originalOperation.get("id").toString();
            var originalEvents = mergeEvents(jdbc, operationId);
            var originalMetadata = metadata(jdbc, runId);
            var originalGuard = jdbc.queryForMap("select * from repo_execution_guard where remote_key=?", originalOperation.get("remote_key"));
            var originalSteps = jdbc.queryForList("select s.* from run_step s join run_task t on s.task_id=t.id where t.run_id=? order by t.task_order", runId);
            assertThat(originalTasks).extracting(task -> task.get("status")).containsExactly("COMPLETED", "COMPLETED",
                    unknown ? "UNKNOWN" : "RUNNING", unknown ? "SKIPPED" : "PENDING");
            assertThat(originalOperation).containsEntry("state", checkpoint).containsEntry("action", "CREATE_MR");
            assertThat(originalEvents).extracting(event -> event.get("event_type")).containsExactlyElementsOf(unknown
                    ? List.of("INTENT_RECORDED", "SEND_STARTED", "UNKNOWN") : List.of("INTENT_RECORDED", "SEND_STARTED"));
            assertThat(originalGuard).containsEntry("operation_id", operationId);
            assertThat(originalGuard.get("reason")).isNotNull();
            if (!unknown) assertThat(originalGuard).containsEntry("reason", "REQUEST_IN_FLIGHT");
            assertThat(originalSteps).hasSize(unknown ? 3 : 2);
            assertThat(new com.fasterxml.jackson.databind.ObjectMapper().readTree(originalOperation.get("target_json").toString())
                    .path("sha").textValue()).isEqualTo(frozenSha);
            assertThat(originalMetadata.get("git.sourceSha")).isNotEqualTo("b".repeat(40));
            server.verify("POST", root + "/merge_requests", unknown ? 1 : 0);
            server.verify("PUT", root + "/merge_requests/7/merge", 0);
            long killedPid = first.pid();
            first.destroyForcibly();
            assertThat(first.waitFor(10, TimeUnit.SECONDS)).isTrue();
            assertThat(first.exitValue()).isEqualTo(137);
            int requestsBeforeObservation = server.requests.size();
            Path observedLog = temporary.resolve("create-mr-uncertain-observed.log");
            observed = launch(unknown ? "observe-unknown-create-mr" : "observe-sent-create-mr",
                    db, server, windowId, repoId, marker, receipt, observedLog);
            assertThat(observed.waitFor(55, TimeUnit.SECONDS)).withFailMessage("CREATE_MR send observer timed out: %s", tail(observedLog)).isTrue();
            assertThat(observed.exitValue()).withFailMessage("CREATE_MR send observer failed: %s", tail(observedLog)).isZero();
            int scans = Integer.parseInt(Files.readString(marker.resolveSibling("create-mr-send-scans.txt")).trim());
            assertThat(scans).isGreaterThanOrEqualTo(3);
            assertThat(Files.readString(receipt).trim()).isEqualTo(runId);
            assertThat(tasks(jdbc, runId)).isEqualTo(originalTasks);
            assertThat(jdbc.queryForMap("select * from git_operation where task_id=?", taskId)).isEqualTo(originalOperation);
            assertThat(mergeEvents(jdbc, operationId)).isEqualTo(originalEvents);
            assertThat(metadata(jdbc, runId)).isEqualTo(originalMetadata);
            assertThat(jdbc.queryForMap("select * from repo_execution_guard where remote_key=?", originalOperation.get("remote_key"))).isEqualTo(originalGuard);
            assertThat(jdbc.queryForList("select s.* from run_step s join run_task t on s.task_id=t.id where t.run_id=? order by t.task_order", runId)).isEqualTo(originalSteps);
            assertThat(server.requests).hasSize(requestsBeforeObservation);
            assertThat(server.writeCount()).isEqualTo(unknown ? 1 : 0);
            System.out.printf("PROCESS_CREATE_MR_SEND_NO_REPLAY checkpoint=%s run=%s task=%s operation=%s attempt=%s "
                    + "killedPid=%d exit=%d observedPid=%d scans=%d post=%d observationHttp=0 guardRetained=true%n",
                    checkpoint, runId, taskId, operationId, originalOperation.get("attempt_id"),
                    killedPid, first.exitValue(), observed.pid(), scans, unknown ? 1 : 0);
        } finally { stopOwnedProcess(first); stopOwnedProcess(observed); server.stop(); }
    }

    private static void createMrIntentAssertFrozenRequest(GitLabHttpFixture server, JdbcTemplate jdbc,
                                                         String operationId, String frozenSha) throws Exception {
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var body = json.createObjectNode().put("source_branch", SOURCE).put("target_branch", TARGET)
                .put("title", "Merge " + SOURCE + " into " + TARGET)
                .put("description", "\nReleaseHub-Operation: " + operationId).put("remove_source_branch", false);
        String canonical = body.toString();
        var expected = json.createObjectNode().put("method", "POST").put("suffix", "/merge_requests");
        expected.setAll(body); expected.put("sha", frozenSha);
        var operation = jdbc.queryForMap("select remote_key,request_hash,target_json from git_operation where id=?", operationId);
        assertThat(json.readTree(operation.get("target_json").toString())).isEqualTo(expected);
        var requests = server.requests.stream().filter(request -> request.method().equals("POST")
                && request.path().equals("/api/v4/projects/" + PROJECT_ID + "/merge_requests")).toList();
        assertThat(requests).singleElement().satisfies(request -> {
            assertThat(request.body()).isEqualTo(canonical);
            assertThat(request.query()).isNull();
            assertThat(request.internalHeaders()).isFalse();
        });
        String bodyHash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        String envelope = "POST|" + operation.get("remote_key") + "|/merge_requests||" + bodyHash + "|" + frozenSha;
        assertThat(operation.get("request_hash")).isEqualTo(java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(envelope.getBytes(StandardCharsets.UTF_8))));
    }

    private static Map<String, String> createMrIntentNotSentPatch(boolean contained, String frozenSha) {
        Map<String, String> patch = new java.util.LinkedHashMap<>();
        patch.put("git.requestId", null);
        patch.put("git.blockReason", contained ? null : "UNKNOWN");
        patch.put("git.projectId", PROJECT_ID);
        patch.put("git.sourceBranch", SOURCE);
        patch.put("git.targetBranch", TARGET);
        patch.put("git.mrIid", null);
        patch.put("git.mrUrl", null);
        patch.put("git.sourceSha", frozenSha);
        patch.put("git.mergeSha", null);
        patch.put("git.mrDisposition", contained ? "ALREADY_MERGED" : null);
        return patch;
    }

    private static String branchIntentResponse(String branch, String commit) {
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var value = json.createObjectNode().put("name", branch); value.putObject("commit").put("id", commit);
        return value.toString();
    }

    private static void branchIntentAssertFrozenRequest(GitLabHttpFixture server, JdbcTemplate jdbc, String operationId, String branch) throws Exception {
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        String canonical = json.createObjectNode().put("branch", branch).put("ref", SOURCE_SHA).toString();
        var requests = server.requests.stream().filter(request -> request.method().equals("POST")
                && request.path().equals("/api/v4/projects/" + PROJECT_ID + "/repository/branches")).toList();
        assertThat(requests).singleElement().satisfies(request -> {
            assertThat(request.body()).isEqualTo(canonical); assertThat(request.query()).isNull(); assertThat(request.internalHeaders()).isFalse();
        });
        var op = jdbc.queryForMap("select remote_key,request_hash,target_json from git_operation where id=?", operationId);
        String bodyHash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        String envelope = "POST|" + op.get("remote_key") + "|/repository/branches||" + bodyHash + "|";
        assertThat(op.get("request_hash")).isEqualTo(java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(envelope.getBytes(StandardCharsets.UTF_8))));
        assertThat(json.readTree(op.get("target_json").toString()).path("ref").textValue()).isEqualTo(SOURCE_SHA);
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
        routes(server, SOURCE_SHA);
    }

    private static void routes(GitLabHttpFixture server, String sourceSha) {
        String root = "/api/v4/projects/" + PROJECT_ID;
        server.reply("GET", "/api/v4/projects/acme%2Frestart", 200, "{\"id\":101}");
        for (String branch : List.of(SOURCE, TARGET)) {
            server.reply("GET", root + "/repository/branches/" + URLEncoder.encode(branch, StandardCharsets.UTF_8),
                    200, "{\"name\":\"" + branch + "\",\"commit\":{\"id\":\""
                            + (branch.equals(SOURCE) ? sourceSha : "b".repeat(40)) + "\"}}");
        }
        server.reply("GET", root + "/merge_requests", 200, "[]");
        server.reply("GET", root + "/repository/commits/" + sourceSha + "/refs", 200, "[]");
        String mr = "{\"iid\":7,\"project_id\":101,\"source_project_id\":101,\"target_project_id\":101,"
                + "\"source_branch\":\"" + SOURCE + "\",\"target_branch\":\"" + TARGET
                + "\",\"state\":\"opened\",\"sha\":\"" + sourceSha + "\",\"web_url\":\""
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
            String completedAction = environment.getProperty("releasehub.fixture.pause-after-completion", "");
            boolean pauseAfterProjection = environment.getProperty("releasehub.fixture.pause-after-projection", Boolean.class, false);
            boolean observeScans = environment.getProperty("releasehub.fixture.observe-scans", Boolean.class, false);
            String journalPoint = environment.getProperty("releasehub.fixture.pause-after-merge-journal", "");
            String creationJournalPoint = environment.getProperty("releasehub.fixture.pause-after-creation-journal", "");
            String creationAction = environment.getProperty("releasehub.fixture.creation-action", "CREATE_BRANCH");
            String completionReleaseFile = environment.getProperty("releasehub.fixture.completion-release-file", "");
            Path marker = Path.of(environment.getRequiredProperty("releasehub.fixture.checkpoint-file"));
            Path completionMarker = Path.of(environment.getProperty(
                    "releasehub.fixture.completion-checkpoint-file", marker.toString()));
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if ((!journalPoint.isBlank() || !creationJournalPoint.isBlank()) && bean instanceof GitOperationJournalPort originalJournal) {
                        return Proxy.newProxyInstance(GitOperationJournalPort.class.getClassLoader(),
                                new Class<?>[]{GitOperationJournalPort.class}, (proxy, method, arguments) -> {
                                    Object result;
                                    try { result = method.invoke(originalJournal, arguments); }
                                    catch (InvocationTargetException error) { throw error.getCause(); }
                                    String pausedRunId = null;
                                    if (method.getName().equals("begin") && arguments[0] instanceof GitOperationJournalPort.Command command
                                            && command.taskFence() != null && ("INTENT_RECORDED".equals(journalPoint) && "MERGE_MR".equals(command.action())
                                            || "INTENT_RECORDED".equals(creationJournalPoint) && creationAction.equals(command.action()))) {
                                        pausedRunId = command.runId();
                                    }
                                    if (("SEND_STARTED".equals(journalPoint) || "SEND_STARTED".equals(creationJournalPoint)) && method.getName().equals("sending")) {
                                        var operation = originalJournal.find((String) arguments[0]).orElseThrow().operation();
                                        if ("SEND_STARTED".equals(journalPoint) && "MERGE_MR".equals(operation.action())
                                                || "SEND_STARTED".equals(creationJournalPoint) && creationAction.equals(operation.action())) pausedRunId = operation.runId();
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
                    if (!(pauseAfterMr || pauseAfterClaim || !completionAction.isBlank() || !completedAction.isBlank() || pauseAfterProjection || observeScans)
                            || !(bean instanceof RunPort original)) return bean;
                    return Proxy.newProxyInstance(RunPort.class.getClassLoader(), new Class<?>[]{RunPort.class},
                            (proxy, method, arguments) -> {
                                if (method.getName().equals("completeTask")
                                        && arguments[0] instanceof RunPort.TaskClaim task
                                        && task.actionType().name().equals(completionAction)) {
                                    Files.writeString(completionMarker, task.runId());
                                    if (completionReleaseFile.isBlank()) {
                                        new CountDownLatch(1).await();
                                        throw new IllegalStateException("Applied action pause unexpectedly ended");
                                    }
                                    while (!Files.exists(Path.of(completionReleaseFile))) Thread.sleep(25);
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
                                if (method.getName().equals("completeTask")
                                        && arguments[0] instanceof RunPort.TaskClaim task
                                        && task.actionType().name().equals(completedAction)) {
                                    // Pause only after the real task/result transaction has committed.
                                    Files.writeString(completionMarker, task.runId());
                                    new CountDownLatch(1).await();
                                    throw new IllegalStateException("Committed task pause unexpectedly ended");
                                }
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
                                if (pauseAfterProjection && method.getName().equals("projectNextConfirmedTask")
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
            boolean rejectedMergeBeforeCrash = "first-rejected-merge".equals(args[0]);
            boolean rejectedMergeResume = args[0].startsWith("resume-rejected-merge-");
            boolean mergeProjectionResume = "resume-applied-merge".equals(args[0]);
            boolean intentMergeBeforeCrash = "first-intent-merge".equals(args[0]);
            boolean sentMergeBeforeCrash = "first-sent-merge".equals(args[0]);
            boolean intentThenAppliedCrash = "resume-intent-applied-merge".equals(args[0]);
            boolean sentMergeResume = "resume-sent-merge".equals(args[0]);
            boolean notSentResume = args[0].startsWith("resume-not-sent-");
            boolean notSentObservation = args[0].startsWith("observe-not-sent-");
            boolean branchIntentFirst = args[0].startsWith("first-intent-branch-");
            boolean branchSentFirst = args[0].equals("first-sent-branch-release");
            boolean branchIntentResume = args[0].startsWith("resume-intent-branch-");
            boolean branchIntentProjection = args[0].equals("project-intent-branch-feature");
            boolean branchSentObservation = args[0].equals("observe-sent-branch-release");
            boolean releaseBranchAction = args[0].contains("branch-release");
            String branchAction = releaseBranchAction ? "ENSURE_RELEASE" : "ENSURE_FEATURE";
            boolean createMrIntentFirst = args[0].equals("first-intent-create-mr");
            boolean createMrSentFirst = args[0].equals("first-sent-create-mr");
            boolean createMrUnknownFirst = args[0].equals("first-unknown-create-mr");
            boolean createMrIntentResume = args[0].equals("resume-intent-create-mr");
            boolean createMrProjection = args[0].equals("project-intent-create-mr");
            boolean createMrLocalResume = args[0].equals("resume-local-create-mr");
            boolean createMrLocalObservation = args[0].startsWith("observe-local-create-mr-");
            boolean createMrSentObservation = args[0].equals("observe-sent-create-mr");
            boolean createMrUnknownObservation = args[0].equals("observe-unknown-create-mr");
            boolean first = "first".equals(args[0]) || claimedBeforeCrash || appliedBranchBeforeCrash
                    || appliedMrBeforeCrash || appliedMergeBeforeCrash || rejectedMergeBeforeCrash || intentMergeBeforeCrash || sentMergeBeforeCrash
                    || branchIntentFirst || branchSentFirst || createMrIntentFirst || createMrSentFirst || createMrUnknownFirst;
            Path marker = Path.of(args[1]);
            Path receipt = Path.of(args[2]);
            try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
                    ReleaseHubApplication.class, CheckpointConfiguration.class)
                    .run("--spring.profiles.active=e2e", "--server.port=0", "--spring.jpa.open-in-view=false",
                            "--releasehub.run.worker.enabled=true", "--releasehub.run.worker.parallelism=1",
                            "--releasehub.run.worker.queue-capacity=0", "--releasehub.run.worker.scan-delay-ms=25",
                            "--releasehub.run.worker.claim-lease-ms=2000",
                            "--releasehub.fixture.pause-after-mr=" + (first && !claimedBeforeCrash && !appliedBranchBeforeCrash && !appliedMrBeforeCrash && !appliedMergeBeforeCrash && !rejectedMergeBeforeCrash && !intentMergeBeforeCrash && !sentMergeBeforeCrash && !branchIntentFirst && !branchSentFirst && !createMrIntentFirst && !createMrSentFirst && !createMrUnknownFirst),
                            "--releasehub.fixture.pause-after-claim=" + claimedBeforeCrash,
                            "--releasehub.fixture.pause-before-completion=" + (createMrIntentResume ? "ENSURE_MR" : branchIntentResume ? branchAction : appliedBranchBeforeCrash ? "ENSURE_FEATURE" : appliedMrBeforeCrash ? "ENSURE_MR" : appliedMergeBeforeCrash || rejectedMergeBeforeCrash || intentThenAppliedCrash ? "TRY_MERGE" : ""),
                            "--releasehub.fixture.pause-after-completion=" + (createMrLocalResume || createMrUnknownFirst ? "ENSURE_MR" : ""),
                            "--releasehub.fixture.pause-after-projection=" + (branchProjectionResume || mrProjectionResume || mergeProjectionResume || rejectedMergeResume || branchIntentProjection || createMrProjection),
                            "--releasehub.fixture.pause-after-merge-journal=" + (intentMergeBeforeCrash ? "INTENT_RECORDED" : sentMergeBeforeCrash ? "SEND_STARTED" : ""),
                            "--releasehub.fixture.pause-after-creation-journal=" + (branchIntentFirst || createMrIntentFirst ? "INTENT_RECORDED"
                                    : branchSentFirst || createMrSentFirst ? "SEND_STARTED" : ""),
                            "--releasehub.fixture.creation-action=" + (createMrIntentFirst || createMrSentFirst ? "CREATE_MR" : "CREATE_BRANCH"),
                            "--releasehub.fixture.observe-scans=" + (sentMergeResume || notSentResume || notSentObservation || branchSentObservation
                                    || createMrLocalObservation || createMrSentObservation || createMrUnknownObservation),
                            "--releasehub.fixture.completion-checkpoint-file=" + (createMrIntentResume ? marker.resolveSibling("resumed-create-mr-applied.txt")
                                    : createMrLocalResume ? marker.resolveSibling("create-mr-local-committed.txt")
                                    : branchIntentResume ? marker.resolveSibling("resumed-branch-applied.txt")
                                    : intentThenAppliedCrash ? marker.resolveSibling("resumed-merge-applied.txt") : marker),
                            "--releasehub.fixture.completion-release-file=" + (createMrIntentResume ? marker.resolveSibling("create-mr-completion-release.txt")
                                    : branchIntentResume && !args[0].endsWith("-crash")
                                    ? marker.resolveSibling("branch-completion-release.txt") : ""),
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
                if (rejectedMergeResume) {
                    finishRejectedMerge(runs, originalRunId, marker, receipt, args[0].endsWith("-no_commits"));
                    return;
                }
                if (createMrSentObservation || createMrUnknownObservation) {
                    observeSentCreation(context, runs, originalRunId, marker, receipt, ActionType.ENSURE_MR,
                            createMrUnknownObservation ? "UNKNOWN" : "RUNNING", "create-mr-send-scans.txt");
                    return;
                }
                if (createMrLocalObservation) {
                    observeNotSentCreateMr(context, runs, originalRunId, marker, receipt, args[0].endsWith("-contained"));
                    return;
                }
                if (branchSentObservation) {
                    observeSentCreation(context, runs, originalRunId, marker, receipt,
                            ActionType.ENSURE_RELEASE, "RUNNING", "branch-send-scans.txt");
                    return;
                }
                if (branchIntentResume || branchIntentProjection) {
                    finishBranchIntent(context, runs, service, originalRunId, windowId, repoId, receipt, releaseBranchAction);
                    return;
                }
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

        private static void finishRejectedMerge(RunPort runs, String runId, Path marker, Path receipt,
                                                boolean noCommits) throws Exception {
            RunExecutionStatus expectedRun = noCommits ? RunExecutionStatus.NEEDS_REVIEW : RunExecutionStatus.FAILED;
            String expectedTask = noCommits ? "UNKNOWN" : "FAILED";
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(35);
            while (runs.findById(runId).orElseThrow().getExecutionStatus() != expectedRun) {
                if (System.nanoTime() >= deadline) fail("Default worker did not project original rejection: " + runs.findTasks(runId));
                Thread.sleep(25);
            }
            assertThat(runs.findTasks(runId).stream().filter(task -> task.actionType() == ActionType.TRY_MERGE)
                    .findFirst().orElseThrow().status()).isEqualTo(expectedTask);
            while (!Files.exists(marker.resolveSibling("projection-release.txt"))) Thread.sleep(25);
            Files.writeString(receipt, runId);
        }

        private static void finishBranchIntent(ConfigurableApplicationContext context, RunPort runs, RunAppService service,
                                               String runId, String windowId, String repoId, Path receipt,
                                               boolean releaseAction) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(35);
            while (runs.findById(runId).orElseThrow().getExecutionStatus() != RunExecutionStatus.DONE) {
                if (System.nanoTime() >= deadline) fail("Default worker did not finish branch continuation: " + runs.findTasks(runId));
                Thread.sleep(25);
            }
            assertThat(context.getBean(GitOperationJournalPort.class).list(repoId, 0, 10))
                    .hasSize(releaseAction ? 1 : 3).allMatch(operation -> operation.state().equals("APPLIED") && !operation.blocked());
            var replay = service.startOrchestrate(windowId, List.of(repoId), List.of(ITERATION), true, ACTOR, INTENT);
            assertThat(replay.getId().value()).isEqualTo(runId);
            Files.writeString(receipt, runId);
        }

        private static void observeSentCreation(ConfigurableApplicationContext context, RunPort runs,
                                                String runId, Path marker, Path receipt, ActionType action,
                                                String expectedStatus, String scanFile) throws Exception {
            String taskId = runs.findTasks(runId).stream().filter(task -> task.actionType() == action)
                    .findFirst().orElseThrow().taskId();
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (expectedStatus.equals("RUNNING") && !Boolean.TRUE.equals(jdbc.queryForObject(
                    "select claim_expires_at<=clock_timestamp() from run_task where id=?", Boolean.class, taskId))) {
                if (System.nanoTime() >= deadline) fail("Original creation SEND lease did not expire");
                Thread.sleep(25);
            }
            int before = CheckpointConfiguration.COMPLETED_SCANS.get();
            while (CheckpointConfiguration.COMPLETED_SCANS.get() - before < 3) {
                if (System.nanoTime() >= deadline) fail("Default scanner did not complete three creation SEND scans");
                Thread.sleep(25);
            }
            assertThat(runs.findTasks(runId).stream().filter(task -> task.taskId().equals(taskId)).findFirst().orElseThrow().status()).isEqualTo(expectedStatus);
            Files.writeString(marker.resolveSibling(scanFile), Integer.toString(CheckpointConfiguration.COMPLETED_SCANS.get() - before));
            Files.writeString(receipt, runId);
        }

        private static void observeNotSentCreateMr(ConfigurableApplicationContext context, RunPort runs,
                                                    String runId, Path marker, Path receipt, boolean contained) throws Exception {
            String taskId = runs.findTasks(runId).stream().filter(task -> task.actionType() == ActionType.ENSURE_MR)
                    .findFirst().orElseThrow().taskId();
            RunExecutionStatus expectedRun = contained ? RunExecutionStatus.DONE : RunExecutionStatus.NEEDS_REVIEW;
            String expectedTask = contained ? "COMPLETED" : "UNKNOWN";
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(35);
            while (runs.findById(runId).orElseThrow().getExecutionStatus() != expectedRun) {
                if (System.nanoTime() >= deadline) fail("Default worker did not finish the remaining task: " + runs.findTasks(runId));
                Thread.sleep(25);
            }
            int before = CheckpointConfiguration.COMPLETED_SCANS.get();
            while (CheckpointConfiguration.COMPLETED_SCANS.get() - before < 3) {
                if (System.nanoTime() >= deadline) fail("Default scanner did not complete three CREATE_MR terminal scans");
                Thread.sleep(25);
            }
            assertThat(runs.findTasks(runId).stream().filter(task -> task.taskId().equals(taskId))
                    .findFirst().orElseThrow().status()).isEqualTo(expectedTask);
            assertThat(jdbc.queryForObject("select state from git_operation where task_id=?", String.class, taskId)).isEqualTo("NOT_SENT");
            assertThat(runs.findById(runId).orElseThrow().getExecutionStatus()).isEqualTo(expectedRun);
            Files.writeString(marker.resolveSibling("create-mr-local-scans.txt"),
                    Integer.toString(CheckpointConfiguration.COMPLETED_SCANS.get() - before));
            Files.writeString(receipt, runId);
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
