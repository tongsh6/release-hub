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

/** 独立 JVM + PostgreSQL/HTTP：原任务继续、无写意图接管及已确认创建结果投影；不重放未知写。 */
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

    @ParameterizedTest(name = "branchCreation={0}")
    @ValueSource(booleans = {true, false})
    @Timeout(180)
    void restartingAfterAppliedWriteProjectsWithoutAnotherHttpRequest(boolean branchCreation) throws Exception {
        GitLabHttpFixture server = new GitLabHttpFixture();
        Process first = null;
        Process restarted = null;
        int taskIndex = branchCreation ? 0 : 2;
        String action = branchCreation ? "ENSURE_FEATURE" : "ENSURE_MR";
        String caseName = branchCreation ? "branch" : "mr";
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
                    + "where o.run_id=? order by e.event_sequence", runId);
            assertThat(before).extracting(row -> row.get("status")).containsExactlyElementsOf(branchCreation
                    ? List.of("RUNNING", "PENDING", "PENDING", "PENDING")
                    : List.of("COMPLETED", "COMPLETED", "RUNNING", "PENDING"));
            assertThat(originalSteps).hasSize(taskIndex);
            assertThat(originalItemIds).hasSize(1);
            assertThat(operations).hasSize(1);
            assertThat(operations.getFirst()).containsEntry("state", "APPLIED")
                    .containsEntry("action", branchCreation ? "CREATE_BRANCH" : "CREATE_MR")
                    .containsEntry("task_id", originalTask.get("id"))
                    .containsEntry("claim_token", originalTask.get("claim_token"));
            assertThat(events).hasSize(3);
            assertThat(events.getLast()).containsEntry("event_type", "APPLIED");
            String remoteKey = operations.getFirst().get("remote_key").toString();
            var guard = jdbc.queryForMap("select * from repo_execution_guard where remote_key=?", remoteKey);
            assertThat(guard.get("operation_id")).isEqualTo(operations.getFirst().get("id"));
            assertThat(jdbc.queryForObject("select count(*) from run_step where task_id=?", Integer.class,
                    originalTask.get("id"))).isZero();
            if (!branchCreation) {
                assertThat(metadata(jdbc, runId)).doesNotContainKeys("git.mrIid", "git.mrUrl", "git.sourceSha");
            }
            server.verify("POST", root + (branchCreation ? "/repository/branches" : "/merge_requests"), 1);
            assertThat(server.writeCount()).isEqualTo(1);

            long killedPid = first.pid();
            first.destroyForcibly();
            assertThat(first.waitFor(10, TimeUnit.SECONDS)).isTrue();
            assertThat(first.exitValue()).isNotZero();
            if (branchCreation) {
                server.reply("GET", featurePath, 200,
                        "{\"name\":\"" + SOURCE + "\",\"commit\":{\"id\":\"" + SOURCE_SHA + "\"}}");
            }
            int requestsBeforeRestart = server.requests.size();
            Instant recoveryStarted = Instant.now();
            Path restartedLog = temporary.resolve("applied-restarted.log");
            restarted = launch("resume-applied-" + caseName, db, server, windowId, repoId, marker, receipt, restartedLog);
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
                    + "where o.run_id=? order by e.event_sequence", runId)).isEqualTo(events);
            var step = jdbc.queryForMap("select * from run_step where task_id=?", originalTask.get("id"));
            assertThat(step).containsEntry("action_type", action).containsEntry("result", "CREATED");
            Instant stepStart = jdbc.queryForObject("select start_at from run_step where task_id=?",
                    java.sql.Timestamp.class, originalTask.get("id")).toInstant();
            assertThat(stepStart).isAfterOrEqualTo(recoveryStarted);
            assertThat(metadata(jdbc, runId)).containsEntry("git.projectId", PROJECT_ID);
            if (!branchCreation) {
                assertThat(metadata(jdbc, runId)).containsEntry("git.sourceBranch", SOURCE)
                        .containsEntry("git.targetBranch", TARGET).containsEntry("git.sourceSha", SOURCE_SHA)
                        .containsEntry("git.mrIid", "7").containsEntry("git.mrDisposition", "CREATED")
                        .containsEntry("git.mrUrl", server.baseUrl() + "/acme/restart/-/merge_requests/7")
                        .doesNotContainKeys("git.mergeSha", "git.blockReason", "git.requestId");
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
                    + "projectionHttp=0 branchPost=%d mrPost=1 mergePut=1 preservedToken=true preservedEvents=3%n",
                    branchCreation ? "BRANCH" : "MR", runId, killedPid, first.exitValue(), restarted.pid(), branchCreation ? 1 : 0);
        } finally {
            stopOwnedProcess(first);
            stopOwnedProcess(restarted);
            server.stop();
        }
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
        @Bean
        static BeanPostProcessor checkpointPause(Environment environment) {
            boolean pauseAfterMr = environment.getProperty("releasehub.fixture.pause-after-mr", Boolean.class, false);
            boolean pauseAfterClaim = environment.getProperty("releasehub.fixture.pause-after-claim", Boolean.class, false);
            String completionAction = environment.getProperty("releasehub.fixture.pause-before-completion", "");
            boolean pauseAfterProjection = environment.getProperty("releasehub.fixture.pause-after-projection", Boolean.class, false);
            Path marker = Path.of(environment.getRequiredProperty("releasehub.fixture.checkpoint-file"));
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (!(pauseAfterMr || pauseAfterClaim || !completionAction.isBlank() || pauseAfterProjection)
                            || !(bean instanceof RunPort original)) return bean;
                    return Proxy.newProxyInstance(RunPort.class.getClassLoader(), new Class<?>[]{RunPort.class},
                            (proxy, method, arguments) -> {
                                if (method.getName().equals("completeTask")
                                        && arguments[0] instanceof RunPort.TaskClaim task
                                        && task.actionType().name().equals(completionAction)) {
                                    Files.writeString(marker, task.runId());
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
            boolean first = "first".equals(args[0]) || claimedBeforeCrash || appliedBranchBeforeCrash || appliedMrBeforeCrash;
            Path marker = Path.of(args[1]);
            Path receipt = Path.of(args[2]);
            try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
                    ReleaseHubApplication.class, CheckpointConfiguration.class)
                    .run("--spring.profiles.active=e2e", "--server.port=0", "--spring.jpa.open-in-view=false",
                            "--releasehub.run.worker.enabled=true", "--releasehub.run.worker.parallelism=1",
                            "--releasehub.run.worker.queue-capacity=0", "--releasehub.run.worker.scan-delay-ms=25",
                            "--releasehub.run.worker.claim-lease-ms=2000",
                            "--releasehub.fixture.pause-after-mr=" + (first && !claimedBeforeCrash && !appliedBranchBeforeCrash && !appliedMrBeforeCrash),
                            "--releasehub.fixture.pause-after-claim=" + claimedBeforeCrash,
                            "--releasehub.fixture.pause-before-completion=" + (appliedBranchBeforeCrash ? "ENSURE_FEATURE" : appliedMrBeforeCrash ? "ENSURE_MR" : ""),
                            "--releasehub.fixture.pause-after-projection=" + (branchProjectionResume || mrProjectionResume),
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
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(35);
                while (runs.findById(originalRunId).orElseThrow().getExecutionStatus() != RunExecutionStatus.DONE) {
                    if (System.nanoTime() >= deadline) throw new IllegalStateException(
                            "Scheduled worker did not finish original task: " + runs.findTasks(originalRunId));
                    Thread.sleep(50);
                }
                assertThat(context.getBean(GitOperationJournalPort.class).list(repoId, 0, 10))
                        .hasSize(branchProjectionResume ? 3 : 2).allMatch(operation -> operation.state().equals("APPLIED") && !operation.blocked());
                var run = service.startOrchestrate(windowId, List.of(repoId), List.of(ITERATION), true, ACTOR, INTENT);
                assertThat(run.getId().value()).isEqualTo(originalRunId);
                Files.writeString(receipt, run.getId().value());
            }
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
