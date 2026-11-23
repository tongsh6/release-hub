package io.releasehub.bootstrap.api;

import io.releasehub.application.gitoperation.*;
import io.releasehub.application.port.out.*;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.application.settings.SettingsPort;
import io.releasehub.application.run.RunPort;
import io.releasehub.application.run.plan.RunPlanAppService;
import io.releasehub.domain.run.ActionType;
import io.releasehub.domain.run.RunItemResult;
import io.releasehub.domain.run.RunStep;
import io.releasehub.application.version.*;
import io.releasehub.common.exception.BusinessException;
import io.releasehub.domain.repo.*;
import io.releasehub.domain.version.BuildTool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = "releasehub.run.worker.enabled=false") @ActiveProfiles("e2e")
class GitWriteSafetyIT {
    private static final PostgreSQLContainer<?> DB=new PostgreSQLContainer<>("postgres:18.1")
            .withDatabaseName("git_safety_test").withUsername("safety_test").withPassword("isolated-safety-db");
    private static final GitLabHttpFixture SERVER=new GitLabHttpFixture();
    private static final AtomicInteger PROJECT=new AtomicInteger(100);
    static {DB.start();SERVER.start();}
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",DB::getJdbcUrl);r.add("spring.datasource.username",DB::getUsername);r.add("spring.datasource.password",DB::getPassword);
    }
    @AfterAll static void stop(){SERVER.stop();DB.stop();}
    @Autowired CodeRepositoryPort repos;
    @Autowired GitBranchAdapterFactory factory;
    @Autowired GitLabFilePort files;
    @Autowired SettingsPort settings;
    @Autowired GitOperationJournalPort journal;
    @Autowired GitOperationAppService service;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ApplicationContext context;
    @Autowired java.util.List<VersionUpdaterPort> versionUpdaters;
    @Autowired RunPort taskRuns;
    @Autowired io.releasehub.application.run.RunAppService taskService;
    @Autowired RunPlanAppService taskPlans;
    @Autowired io.releasehub.application.releasewindow.ReleaseWindowPort taskWindows;
    @Autowired io.releasehub.application.iteration.IterationPort taskIterations;
    @Autowired io.releasehub.application.iteration.IterationRepoPort taskVersions;
    @Autowired io.releasehub.application.window.WindowIterationPort taskBindings;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    private CodeRepository repo;
    private String path,endpoint,canonicalEndpoint,sha;
    private int project;
    @BeforeEach void setup() {
        SERVER.resetAll();project=PROJECT.incrementAndGet();path="acme/safety-"+UUID.randomUUID();
        endpoint="/api/v4/projects/"+URLEncoder.encode(path,StandardCharsets.UTF_8);
        canonicalEndpoint="/api/v4/projects/"+project;sha=String.format("%040x",project);
        repo=createRepo(path);
        settings.saveGitLab(new SettingsPort.SettingsGitLab(SERVER.baseUrl(),"fixture-token"));
        identity(endpoint);
        SERVER.reply("GET","/api/v4/projects/"+project+"/repository/commits/main",200,"{\"id\":\""+sha+"\"}");
    }
    private CodeRepository createRepo(String repositoryPath) {
        var value=CodeRepository.create("安全验证",SERVER.baseUrl()+"/"+repositoryPath+".git","main","G001",RepoType.SERVICE,GitProvider.GITLAB,"fixture-token",false,Instant.now());
        repos.save(value);return value;
    }
    private void identity(String route) {SERVER.reply("GET",route,200,"{\"id\":"+project+"}");}
    private void branchSuccess() {SERVER.reply("POST",canonicalEndpoint+"/repository/branches",201,
            "{\"name\":\"release/evidence\",\"commit\":{\"id\":\""+sha+"\"}}");}
    private boolean write() {return factory.getAdapter(GitProvider.GITLAB).createBranch(repo.getCloneUrl(),"fixture-token","release/evidence","main");}
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"object", "commit"})
    void branchCreationCannotMaskWrongCommitId(String decoyLocation) throws Exception {
        var run = admittedBranchRun();
        String feature = run.getItems().getFirst().getMetadata().get("plan.featureBranch");
        var body = branchResponseWithExtraHash(feature, "b".repeat(40), decoyLocation, sha);
        SERVER.reply("POST", canonicalEndpoint + "/repository/branches", 201, body.toString());

        assertThat(taskService.executeNextTask(run.getId().value())).isTrue();

        assertUnconfirmedBranchTask(run.getId().value());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"object", "commit"})
    void branchCreationUsesCommitIdDespiteOtherHashes(String decoyLocation) throws Exception {
        var run = admittedBranchRun();
        String feature = run.getItems().getFirst().getMetadata().get("plan.featureBranch");
        var body = branchResponseWithExtraHash(feature, sha, decoyLocation, "b".repeat(40));
        SERVER.reply("POST", canonicalEndpoint + "/repository/branches", 201, body.toString());

        assertThat(taskService.executeNextTask(run.getId().value())).isTrue();

        assertConfirmedBranchTask(run.getId().value(), feature);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {200, 202})
    void branchCreationRequiresCreatedStatusEvenWithCompleteBody(int status) throws Exception {
        var run = admittedBranchRun();
        String feature = run.getItems().getFirst().getMetadata().get("plan.featureBranch");
        var body = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("name", feature);
        body.putObject("commit").put("id", sha);
        SERVER.reply("POST", canonicalEndpoint + "/repository/branches", status, body.toString());

        assertThat(taskService.executeNextTask(run.getId().value())).isTrue();

        assertUnconfirmedBranchTask(run.getId().value());
    }

    @Test
    void branchCreationPersistsCanonicalCommitProof() throws Exception {
        var run = admittedBranchRun();
        String feature = run.getItems().getFirst().getMetadata().get("plan.featureBranch");
        var body = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("name", feature);
        body.putObject("commit").put("id", sha);
        SERVER.reply("POST", canonicalEndpoint + "/repository/branches", 201, body.toString());

        assertThat(taskService.executeNextTask(run.getId().value())).isTrue();

        assertConfirmedBranchTask(run.getId().value(), feature);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {40, 64})
    void branchCreationMatchesFrozenShaIgnoringHexCase(int length) throws Exception {
        sha = "aBcD".repeat(length / 4);
        SERVER.reply("GET", canonicalEndpoint + "/repository/commits/main", 200, "{\"id\":\"" + sha + "\"}");
        var run = admittedBranchRun();
        String feature = run.getItems().getFirst().getMetadata().get("plan.featureBranch");
        var body = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("name", feature);
        body.putObject("commit").put("id", sha.toUpperCase(java.util.Locale.ROOT));
        SERVER.reply("POST", canonicalEndpoint + "/repository/branches", 201, body.toString());

        assertThat(taskService.executeNextTask(run.getId().value())).isTrue();

        assertConfirmedBranchTask(run.getId().value(), feature);
    }

    private com.fasterxml.jackson.databind.node.ObjectNode branchResponseWithExtraHash(
            String branch, String commitId, String decoyLocation, String decoy) {
        var body = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("name", branch);
        var commit = body.putObject("commit").put("id", commitId);
        if ("object".equals(decoyLocation)) body.putObject("object").put("id", decoy);
        else commit.put("sha", decoy);
        return body;
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "branchResume={0}/{1}")
    @org.junit.jupiter.params.provider.CsvSource({"ENSURE_FEATURE,moved", "ENSURE_RELEASE,moved",
            "ENSURE_FEATURE,deleted", "ENSURE_RELEASE,deleted", "ENSURE_FEATURE,mixed64", "ENSURE_RELEASE,mixed64"})
    void branchIntentResumeUsesFrozenRequest(ActionType action, String refChange) throws Exception {
        if (refChange.equals("mixed64")) sha = "aBcD".repeat(16);
        var run = branchIntentReadyRun(action);
        String runId = run.getId().value();
        String branch = branchIntentBranch(run, action);
        var workers = java.util.concurrent.Executors.newFixedThreadPool(2);
        try (var gate = new CreationIntentGate(action, true)) {
            try {
                var oldOwner = workers.submit(() -> taskService.executeNextTask(runId));
                assertThat(gate.entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                var command = gate.command.get();
                String operationId = gate.operationId.get();
                String taskId = command.taskFence().taskId();
                var original = mergeIntentIdentity(operationId);
                var intent = journal.find(operationId).orElseThrow().events().getFirst();
                var originalTask = jdbc.queryForMap("select * from run_task where id=?", taskId);
                var originalMetadata = mergeNotSentMetadata(originalTask.get("target_id").toString());
                int requestsBeforeResume = SERVER.requests.size();
                gate.expire();
                SERVER.reply("GET", canonicalEndpoint + "/repository/commits/main", refChange.equals("deleted") ? 404 : 200,
                        refChange.equals("deleted") ? "{}" : "{\"id\":\"" + "b".repeat(40) + "\"}");
                SERVER.reply("GET", canonicalEndpoint + "/repository/branches/main", refChange.equals("deleted") ? 404 : 200,
                        refChange.equals("deleted") ? "{}" : branchIntentResponse("main", "b".repeat(40)));
                var newOwner = workers.submit(() -> taskService.executeNextTask(runId));
                assertThat(gate.responseEntered.await(5, java.util.concurrent.TimeUnit.SECONDS))
                        .as("expired complete branch INTENT must resume the original HTTP request").isTrue();
                var current = gate.claims.getLast();
                assertThat(current.fence().claimToken()).isNotEqualTo(command.taskFence().claimToken());
                assertThat(current.metadata()).isEqualTo(gate.claims.getFirst().metadata());
                assertThat(mergeNotSentMetadata(current.itemId())).isEqualTo(originalMetadata);
                assertThat(journal.find(operationId).orElseThrow().operation().state()).isEqualTo("SEND_STARTED");
                assertThat(mergeIntentIdentity(operationId)).isEqualTo(original);
                gate.release.countDown();
                assertThatThrownBy(() -> oldOwner.get(5, java.util.concurrent.TimeUnit.SECONDS))
                        .isInstanceOf(java.util.concurrent.ExecutionException.class).hasCauseInstanceOf(BusinessException.class);
                assertThat(jdbc.queryForMap("select status,claim_token from run_task where id=?", taskId))
                        .containsEntry("status", "RUNNING").containsEntry("claim_token", current.fence().claimToken());
                assertThat(journal.find(operationId).orElseThrow().events()).hasSize(2);
                gate.releaseResponse.countDown();
                boolean resumed = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> newOwner.get(5, java.util.concurrent.TimeUnit.SECONDS));
                assertThat(resumed).isTrue();
                branchIntentAssertApplied(operationId, current.fence(), intent);
                assertThat(mergeIntentIdentity(operationId)).isEqualTo(original);
                assertThat(jdbc.queryForMap("select * from run_task where id=?", taskId))
                        .containsEntry("status", "COMPLETED").containsEntry("started_at", originalTask.get("started_at"))
                        .containsEntry("task_order", originalTask.get("task_order")).containsEntry("claim_token", current.fence().claimToken());
                var expectedMetadata = new LinkedHashMap<>(originalMetadata);
                expectedMetadata.put("git.projectId", String.valueOf(project));
                assertThat(mergeNotSentMetadata(current.itemId())).isEqualTo(expectedMetadata);
                branchIntentAssertRequest(operationId, branch, sha);
                assertThat(SERVER.requests.subList(requestsBeforeResume, SERVER.requests.size()))
                        .noneMatch(request -> request.path().equals(canonicalEndpoint + "/repository/commits/main")
                                || request.path().equals(canonicalEndpoint + "/repository/branches/main"));
                System.out.printf("HTTP_BRANCH_INTENT_RESUME action=%s ref=%s run=%s task=%s operation=%s attempt=%s "
                                + "oldTokenRevoked=true originalOwnerPreserved=true branchPost=1 originalRefPreserved=true%n",
                        action, refChange, runId, taskId, operationId, intent.attemptId());
            } finally {
                gate.releaseAll(); workers.shutdown();
                assertThat(workers.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = ActionType.class, names = {"ENSURE_FEATURE", "ENSURE_RELEASE"})
    void branchIntentFirstAttemptKeepsProjectCheckpoint(ActionType action) throws Exception {
        var run = branchIntentReadyRun(action);
        String runId = run.getId().value();
        var expected = new LinkedHashMap<>(run.getItems().getFirst().getMetadata());
        expected.put("git.projectId", String.valueOf(project));
        boolean completed = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> taskService.executeNextTask(runId));
        assertThat(completed).isTrue();
        var task = taskRuns.findTasks(runId).stream().filter(value -> value.actionType() == action).findFirst().orElseThrow();
        assertThat(task.status()).isEqualTo("COMPLETED");
        assertThat(mergeNotSentMetadata(task.itemId())).isEqualTo(expected);
        var operation = journal.listByRun(runId, 0, 20).getFirst();
        assertThat(operation.state()).isEqualTo("APPLIED");
        assertThat(operation.blocked()).isFalse();
        assertThat(jdbc.queryForObject("select result from run_step where task_id=?", String.class, task.taskId())).isEqualTo("CREATED");
        SERVER.verify("POST", canonicalEndpoint + "/repository/branches", 1);
        assertThat(SERVER.body("POST", canonicalEndpoint + "/repository/branches").path("ref").textValue()).isEqualTo(sha);
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "branchPreflight={0}/{1}")
    @org.junit.jupiter.params.provider.CsvSource({"ENSURE_FEATURE,exists", "ENSURE_RELEASE,exists",
            "ENSURE_FEATURE,forbidden", "ENSURE_RELEASE,forbidden", "ENSURE_FEATURE,unavailable",
            "ENSURE_RELEASE,unavailable", "ENSURE_RELEASE,feature_missing"})
    void branchIntentPreflightClosesWithoutWrite(ActionType action, String observation) throws Exception {
        branchIntentAssertLocalResult(action, observation);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = ActionType.class, names = {"ENSURE_FEATURE", "ENSURE_RELEASE"})
    void branchIntentRejectsReboundNamespaceBeforeBranchRead(ActionType action) throws Exception {
        branchIntentAssertLocalResult(action, "rebound");
    }

    private void branchIntentAssertLocalResult(ActionType action, String observation) throws Exception {
        var run = branchIntentReadyRun(action);
        String runId = run.getId().value();
        String branch = branchIntentBranch(run, action);
        var workers = java.util.concurrent.Executors.newSingleThreadExecutor();
        try (var gate = new CreationIntentGate(action, false)) {
            try {
                var oldOwner = workers.submit(() -> taskService.executeNextTask(runId));
                assertThat(gate.entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                var command = gate.command.get();
                String operationId = gate.operationId.get();
                var original = mergeIntentIdentity(operationId);
                var intent = journal.find(operationId).orElseThrow().events().getFirst();
                var originalMetadata = new LinkedHashMap<>(gate.claims.getFirst().metadata());
                String targetPath = canonicalEndpoint + "/repository/branches/" + URLEncoder.encode(branch, StandardCharsets.UTF_8);
                String reboundEndpoint = "/api/v4/projects/" + (project + 10000);
                gate.expire();
                switch (observation) {
                    case "exists" -> SERVER.reply("GET", targetPath, 200, branchIntentResponse(branch, "b".repeat(40)));
                    case "forbidden" -> SERVER.reply("GET", targetPath, 403, "{}");
                    case "unavailable" -> SERVER.reply("GET", targetPath, 503, "{}");
                    case "feature_missing" -> SERVER.reply("GET", canonicalEndpoint + "/repository/branches/"
                            + URLEncoder.encode(run.getItems().getFirst().getMetadata().get("plan.featureBranch"), StandardCharsets.UTF_8), 404, "{}");
                    case "rebound" -> {
                        SERVER.reply("GET", endpoint, 200, "{\"id\":" + (project + 10000) + "}");
                        for (String key : List.of("plan.featureBranch", "plan.releaseBranch")) {
                            String name = run.getItems().getFirst().getMetadata().get(key);
                            SERVER.reply("GET", reboundEndpoint + "/repository/branches/" + URLEncoder.encode(name, StandardCharsets.UTF_8),
                                    200, branchIntentResponse(name, "b".repeat(40)));
                        }
                    }
                    default -> throw new AssertionError(observation);
                }
                boolean completed = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> taskService.executeNextTask(runId));
                assertThat(completed).isTrue();
                var current = gate.claims.getLast();
                assertThat(current.fence().claimToken()).isNotEqualTo(command.taskFence().claimToken());
                assertThat(current.metadata()).isEqualTo(originalMetadata);
                RunItemResult result = observation.equals("exists") ? RunItemResult.ALREADY_EXISTS : RunItemResult.FAILED;
                Map<String, String> patch = new LinkedHashMap<>();
                patch.put("git.projectId", String.valueOf(project));
                if (observation.equals("rebound") && action == ActionType.ENSURE_RELEASE) patch.put("git.blockReason", "UNKNOWN");
                mergeNotSentAssertEvent(operationId, current.fence().taskId(), current.fence().claimToken(), result, patch, intent);
                assertThat(mergeIntentIdentity(operationId)).isEqualTo(original);
                var expected = new LinkedHashMap<>(originalMetadata); expected.putAll(patch);
                assertThat(mergeNotSentMetadata(current.itemId())).isEqualTo(expected);
                assertTaskStatus(current, result == RunItemResult.ALREADY_EXISTS ? "COMPLETED" : "FAILED");
                assertThat(jdbc.queryForObject("select result from run_step where task_id=?", String.class, current.fence().taskId()))
                        .isEqualTo(result.name());
                assertThat(taskRuns.findById(runId).orElseThrow().getExecutionStatus().name())
                        .isEqualTo(result == RunItemResult.ALREADY_EXISTS ? "RUNNING" : "FAILED");
                assertThat(SERVER.requests).noneMatch(request -> request.path().startsWith(reboundEndpoint + "/repository/branches"));
                SERVER.verify("POST", canonicalEndpoint + "/repository/branches", 0);
                assertThat(SERVER.writeCount()).isZero();
                var closedTask = jdbc.queryForMap("select * from run_task where id=?", current.fence().taskId());
                var closedOperation = jdbc.queryForMap("select * from git_operation where id=?", operationId);
                var closedEvents = journal.find(operationId).orElseThrow().events();
                gate.release.countDown();
                assertThatThrownBy(() -> oldOwner.get(5, java.util.concurrent.TimeUnit.SECONDS))
                        .isInstanceOf(java.util.concurrent.ExecutionException.class).hasCauseInstanceOf(BusinessException.class);
                assertThat(jdbc.queryForMap("select * from run_task where id=?", current.fence().taskId())).isEqualTo(closedTask);
                assertThat(jdbc.queryForMap("select * from git_operation where id=?", operationId)).isEqualTo(closedOperation);
                assertThat(journal.find(operationId).orElseThrow().events()).isEqualTo(closedEvents);
                assertThat(SERVER.writeCount()).isZero();
                System.out.printf("HTTP_BRANCH_INTENT_LOCAL action=%s observation=%s run=%s task=%s operation=%s result=%s "
                        + "branchPost=0 reboundBranchReads=0 originalProject=%d%n", action, observation, runId,
                        current.fence().taskId(), operationId, result, project);
            } finally {
                gate.releaseAll(); workers.shutdown();
                assertThat(workers.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "branchHttp={0}/{1}")
    @org.junit.jupiter.params.provider.CsvSource({"ENSURE_FEATURE,403", "ENSURE_RELEASE,403", "ENSURE_FEATURE,408", "ENSURE_RELEASE,408"})
    void branchIntentHttpOutcomeRetainsSendHistory(ActionType action, int status) throws Exception {
        var run = branchIntentReadyRun(action);
        String runId = run.getId().value();
        var workers = java.util.concurrent.Executors.newSingleThreadExecutor();
        try (var gate = new CreationIntentGate(action, false)) {
            try {
                var oldOwner = workers.submit(() -> taskService.executeNextTask(runId));
                assertThat(gate.entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                String operationId = gate.operationId.get();
                var original = mergeIntentIdentity(operationId);
                var intent = journal.find(operationId).orElseThrow().events().getFirst();
                gate.expire();
                SERVER.reply("GET", canonicalEndpoint + "/repository/commits/main", 200, "{\"id\":\"" + "b".repeat(40) + "\"}");
                SERVER.reply("POST", canonicalEndpoint + "/repository/branches", status, "{}");
                boolean completed = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> taskService.executeNextTask(runId));
                assertThat(completed).isTrue();
                var current = gate.claims.getLast();
                assertThat(current.fence().claimToken()).isNotEqualTo(gate.command.get().taskFence().claimToken());
                var detail = journal.find(operationId).orElseThrow();
                String outcome = status == 403 ? "REJECTED" : "UNKNOWN";
                assertThat(detail.operation().state()).isEqualTo(outcome);
                assertThat(detail.events()).extracting(GitOperationJournalPort.Event::type)
                        .containsExactly("INTENT_RECORDED", "SEND_STARTED", outcome);
                assertThat(detail.events().getFirst()).isEqualTo(intent);
                mergeIntentAssertSender(operationId, current.fence());
                assertThat(mergeIntentIdentity(operationId)).isEqualTo(original);
                assertTaskStatus(current, status == 403 ? "FAILED" : "UNKNOWN");
                assertThat(detail.operation().blocked()).isEqualTo(status == 408);
                branchIntentAssertRequest(operationId, branchIntentBranch(run, action), sha);
                var closed = jdbc.queryForMap("select * from git_operation where id=?", operationId);
                gate.release.countDown();
                assertThatThrownBy(() -> oldOwner.get(5, java.util.concurrent.TimeUnit.SECONDS))
                        .isInstanceOf(java.util.concurrent.ExecutionException.class).hasCauseInstanceOf(BusinessException.class);
                assertThat(jdbc.queryForMap("select * from git_operation where id=?", operationId)).isEqualTo(closed);
                SERVER.verify("POST", canonicalEndpoint + "/repository/branches", 1);
            } finally {
                gate.releaseAll(); workers.shutdown();
                assertThat(workers.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = ActionType.class, names = {"ENSURE_FEATURE", "ENSURE_RELEASE"})
    void branchIntentPendingSendRejectsLocalUnknown(ActionType action) throws Exception {
        var run = branchIntentReadyRun(action);
        var claim = taskRuns.claimNextTask(run.getId().value()).orElseThrow();
        assertThat(claim.actionType()).isEqualTo(action);
        var workers = java.util.concurrent.Executors.newSingleThreadExecutor();
        try (var gate = new CreationIntentGate(action, true)) {
            try {
                var sender = workers.submit(() -> {
                    try (var scope = GitOperationContext.withTask(claim)) {
                        return factory.getAdapter(GitProvider.GITLAB).createBranch(repo.getCloneUrl(), "fixture-token",
                                branchIntentBranch(run, action), "main");
                    }
                });
                assertThat(gate.entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                gate.release.countDown();
                assertThat(gate.responseEntered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                String operationId = gate.operationId.get();
                var task = jdbc.queryForMap("select * from run_task where id=?", claim.fence().taskId());
                var operation = jdbc.queryForMap("select * from git_operation where id=?", operationId);
                var events = journal.find(operationId).orElseThrow().events();
                var metadata = mergeNotSentMetadata(claim.itemId());
                var guard = jdbc.queryForMap("select * from repo_execution_guard where remote_key=?", gate.command.get().remoteKey());
                assertThat(events).extracting(GitOperationJournalPort.Event::type).containsExactly("INTENT_RECORDED", "SEND_STARTED");
                SERVER.verify("POST", canonicalEndpoint + "/repository/branches", 1);
                assertThatThrownBy(() -> taskRuns.completeTask(claim, taskStep(claim, RunItemResult.UNKNOWN),
                        Map.of("git.projectId", String.valueOf(project)))).isInstanceOf(BusinessException.class);
                assertThat(jdbc.queryForMap("select * from run_task where id=?", claim.fence().taskId())).isEqualTo(task);
                assertThat(jdbc.queryForMap("select * from git_operation where id=?", operationId)).isEqualTo(operation);
                assertThat(journal.find(operationId).orElseThrow().events()).isEqualTo(events);
                assertThat(mergeNotSentMetadata(claim.itemId())).isEqualTo(metadata);
                assertThat(jdbc.queryForMap("select * from repo_execution_guard where remote_key=?", gate.command.get().remoteKey())).isEqualTo(guard);
                assertThat(jdbc.queryForObject("select count(*) from run_step where task_id=?", Integer.class, claim.fence().taskId())).isZero();
                gate.releaseResponse.countDown();
                boolean created = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> sender.get(5, java.util.concurrent.TimeUnit.SECONDS));
                assertThat(created).isTrue();
                org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> taskRuns.completeTask(claim,
                        taskStep(claim, RunItemResult.CREATED), Map.of("git.projectId", String.valueOf(project))));
                branchIntentAssertApplied(operationId, claim.fence(), events.getFirst());
                assertTaskStatus(claim, "COMPLETED");
                SERVER.verify("POST", canonicalEndpoint + "/repository/branches", 1);
            } finally {
                gate.releaseAll(); workers.shutdown();
                assertThat(workers.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    @Test
    void branchIntentObservationKeepsExecutionFacts() throws Exception {
        var run = branchIntentReadyRun(ActionType.ENSURE_FEATURE);
        var workers = java.util.concurrent.Executors.newSingleThreadExecutor();
        try (var gate = new CreationIntentGate(ActionType.ENSURE_FEATURE, false)) {
            try {
                var owner = workers.submit(() -> taskService.executeNextTask(run.getId().value()));
                assertThat(gate.entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                String operationId = gate.operationId.get();
                var before = journal.find(operationId).orElseThrow();
                var original = mergeIntentIdentity(operationId);
                var task = jdbc.queryForMap("select * from run_task where id=?", gate.command.get().taskFence().taskId());
                var guard = jdbc.queryForMap("select * from repo_execution_guard where remote_key=?", gate.command.get().remoteKey());
                var observed = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() ->
                        service.reconcile(operationId, "branch-observer", UUID.randomUUID().toString(), before.operation().version()));
                assertThat(observed.status()).isEqualTo("NOT_FOUND");
                var after = journal.find(operationId).orElseThrow();
                assertThat(after.operation().version()).isGreaterThan(before.operation().version());
                assertThat(after.operation().state()).isEqualTo("INTENT_RECORDED");
                assertThat(after.events()).isEqualTo(before.events());
                assertThat(after.observations()).hasSize(1);
                assertThat(mergeIntentIdentity(operationId)).isEqualTo(original);
                assertThat(jdbc.queryForMap("select * from run_task where id=?", gate.command.get().taskFence().taskId())).isEqualTo(task);
                assertThat(jdbc.queryForMap("select * from repo_execution_guard where remote_key=?", gate.command.get().remoteKey())).isEqualTo(guard);
                SERVER.verify("POST", canonicalEndpoint + "/repository/branches", 0);
                gate.release.countDown();
                boolean completed = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> owner.get(5, java.util.concurrent.TimeUnit.SECONDS));
                assertThat(completed).isTrue();
                branchIntentAssertApplied(operationId, gate.command.get().taskFence(), before.events().getFirst());
                SERVER.verify("POST", canonicalEndpoint + "/repository/branches", 1);
            } finally {
                gate.releaseAll(); workers.shutdown();
                assertThat(workers.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    private io.releasehub.domain.run.Run branchIntentReadyRun(ActionType action) {
        SERVER.reply("GET", canonicalEndpoint + "/repository/commits/main", 200, "{\"id\":\"" + sha + "\"}");
        var run = admittedBranchRun();
        if (action == ActionType.ENSURE_RELEASE) {
            String feature = run.getItems().getFirst().getMetadata().get("plan.featureBranch");
            SERVER.reply("GET", canonicalEndpoint + "/repository/branches/" + URLEncoder.encode(feature, StandardCharsets.UTF_8),
                    200, branchIntentResponse(feature, sha));
            boolean prepared = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> taskService.executeNextTask(run.getId().value()));
            assertThat(prepared).isTrue();
        }
        String branch = branchIntentBranch(run, action);
        SERVER.reply("GET", canonicalEndpoint + "/repository/branches/" + URLEncoder.encode(branch, StandardCharsets.UTF_8), 404, "{}");
        SERVER.reply("POST", canonicalEndpoint + "/repository/branches", 201, branchIntentResponse(branch, sha));
        return taskRuns.findById(run.getId().value()).orElseThrow();
    }

    private String branchIntentBranch(io.releasehub.domain.run.Run run, ActionType action) {
        return run.getItems().getFirst().getMetadata().get(action == ActionType.ENSURE_FEATURE ? "plan.featureBranch" : "plan.releaseBranch");
    }

    private String branchIntentResponse(String branch, String commit) {
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var body = json.createObjectNode().put("name", branch);
        body.putObject("commit").put("id", commit);
        return body.toString();
    }

    private void branchIntentAssertApplied(String operationId, RunPort.TaskFence fence, GitOperationJournalPort.Event originalIntent) throws Exception {
        var detail = journal.find(operationId).orElseThrow();
        assertThat(detail.operation().state()).isEqualTo("APPLIED");
        assertThat(detail.operation().blocked()).isFalse();
        assertThat(detail.events()).extracting(GitOperationJournalPort.Event::type).containsExactly("INTENT_RECORDED", "SEND_STARTED", "APPLIED");
        assertThat(detail.events().getFirst()).isEqualTo(originalIntent);
        assertThat(detail.events()).allSatisfy(event -> assertThat(event.attemptId()).isEqualTo(originalIntent.attemptId()));
        mergeIntentAssertSender(operationId, fence);
    }

    private void branchIntentAssertRequest(String operationId, String branch, String frozenSha) throws Exception {
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        String canonical = json.createObjectNode().put("branch", branch).put("ref", frozenSha).toString();
        var requests = SERVER.requests.stream().filter(request -> request.method().equals("POST")
                && request.path().equals(canonicalEndpoint + "/repository/branches")).toList();
        assertThat(requests).singleElement().satisfies(request -> {
            assertThat(request.body()).isEqualTo(canonical);
            assertThat(request.query()).isNull();
            assertThat(request.internalHeaders()).isFalse();
        });
        var operation = jdbc.queryForMap("select remote_key,request_hash,target_json from git_operation where id=?", operationId);
        String bodyHash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        String envelope = "POST|" + operation.get("remote_key") + "|/repository/branches||" + bodyHash + "|";
        String expected = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(envelope.getBytes(StandardCharsets.UTF_8)));
        assertThat(operation.get("request_hash")).isEqualTo(expected);
        var target = json.readTree(operation.get("target_json").toString());
        assertThat(target.path("branch").textValue()).isEqualTo(branch);
        assertThat(target.path("ref").textValue()).isEqualTo(frozenSha);
    }

    /** Observe real claims and pause after the real Journal proxy commits; never replace its decisions. */
    private final class CreationIntentGate implements AutoCloseable {
        private final java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
        private final java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        private final java.util.concurrent.CountDownLatch responseEntered = new java.util.concurrent.CountDownLatch(1);
        private final java.util.concurrent.CountDownLatch releaseResponse = new java.util.concurrent.CountDownLatch(1);
        private final java.util.concurrent.atomic.AtomicReference<GitOperationJournalPort.Command> command = new java.util.concurrent.atomic.AtomicReference<>();
        private final java.util.concurrent.atomic.AtomicReference<String> operationId = new java.util.concurrent.atomic.AtomicReference<>();
        private final java.util.List<RunPort.TaskClaim> claims = new java.util.concurrent.CopyOnWriteArrayList<>();
        private final io.releasehub.infrastructure.git.GitWriteHttpInterceptor interceptor;
        private final GitOperationJournalPort originalJournal;
        private final RestTemplate http;
        private final List<org.springframework.http.client.ClientHttpRequestInterceptor> originalInterceptors;
        private final Object serviceTarget;
        private final RunPort originalRuns;

        private CreationIntentGate(ActionType action, boolean holdResponse) {
            interceptor = context.getBean(io.releasehub.infrastructure.git.GitWriteHttpInterceptor.class);
            originalJournal = (GitOperationJournalPort) ReflectionTestUtils.getField(interceptor, "journal");
            Object adapter = AopTestUtils.getUltimateTargetObject(factory.getAdapter(GitProvider.GITLAB));
            http = (RestTemplate) ReflectionTestUtils.getField(adapter, "restTemplate");
            originalInterceptors = List.copyOf(http.getInterceptors());
            serviceTarget = AopTestUtils.getUltimateTargetObject(taskService);
            originalRuns = (RunPort) ReflectionTestUtils.getField(serviceTarget, "runPort");
            var observedRuns = (RunPort) java.lang.reflect.Proxy.newProxyInstance(RunPort.class.getClassLoader(),
                    new Class<?>[]{RunPort.class}, (proxy, method, arguments) -> {
                        Object result;
                        try { result = method.invoke(originalRuns, arguments); }
                        catch (java.lang.reflect.InvocationTargetException error) { throw error.getCause(); }
                        if (method.getName().equals("claimNextTask") && result instanceof java.util.Optional<?> value
                                && value.orElse(null) instanceof RunPort.TaskClaim claim && claim.actionType() == action) claims.add(claim);
                        return result;
                    });
            var pausedJournal = (GitOperationJournalPort) java.lang.reflect.Proxy.newProxyInstance(GitOperationJournalPort.class.getClassLoader(),
                    new Class<?>[]{GitOperationJournalPort.class}, (proxy, method, arguments) -> {
                        Object result;
                        try { result = method.invoke(originalJournal, arguments); }
                        catch (java.lang.reflect.InvocationTargetException error) { throw error.getCause(); }
                        if (method.getName().equals("begin") && arguments[0] instanceof GitOperationJournalPort.Command value
                                && value.action().equals(action == ActionType.ENSURE_MR ? "CREATE_MR" : "CREATE_BRANCH")
                                && command.compareAndSet(null, value)) {
                            operationId.set(((GitOperationJournalPort.Operation) result).id());
                            entered.countDown();
                            if (!release.await(15, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Creation INTENT gate timed out");
                        }
                        return result;
                    });
            var interceptors = new java.util.ArrayList<>(originalInterceptors);
            if (holdResponse) interceptors.add((request, body, execution) -> {
                var response = execution.execute(request, body);
                if (request.getMethod() == org.springframework.http.HttpMethod.POST
                        && request.getURI().getRawPath().equals(canonicalEndpoint
                        + (action == ActionType.ENSURE_MR ? "/merge_requests" : "/repository/branches"))) {
                    holdFixtureResponse(response, responseEntered, releaseResponse);
                }
                return response;
            });
            ReflectionTestUtils.setField(serviceTarget, "runPort", observedRuns);
            ReflectionTestUtils.setField(interceptor, "journal", pausedJournal);
            http.setInterceptors(interceptors);
        }
        private void expire() {
            var fence = command.get().taskFence();
            assertThat(jdbc.update("update run_task set claim_expires_at=clock_timestamp()-interval '1 second' where id=? and status='RUNNING' and claim_token=?",
                    fence.taskId(), fence.claimToken())).isEqualTo(1);
        }
        private void releaseAll() { release.countDown(); releaseResponse.countDown(); }
        @Override public void close() {
            releaseAll(); http.setInterceptors(originalInterceptors);
            ReflectionTestUtils.setField(interceptor, "journal", originalJournal);
            ReflectionTestUtils.setField(serviceTarget, "runPort", originalRuns);
        }
    }

    private io.releasehub.domain.run.Run admittedBranchRun() {
        String windowId = UUID.randomUUID().toString();
        String iterationKey = "ITER-PROOF-" + UUID.randomUUID();
        String feature = "feature/" + iterationKey;
        String release = "release/proof-" + windowId;
        Instant now = Instant.now();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            taskWindows.save(io.releasehub.domain.releasewindow.ReleaseWindow.rehydrate(
                    io.releasehub.domain.releasewindow.ReleaseWindowId.of(windowId), "RW-" + windowId,
                    "分支证明验证", "", now, "G001", io.releasehub.domain.releasewindow.ReleaseWindowStatus.DRAFT,
                    now, now, false, null));
            taskIterations.save(io.releasehub.domain.iteration.Iteration.rehydrate(
                    io.releasehub.domain.iteration.IterationKey.of(iterationKey), "分支证明迭代", "", null, "G001",
                    Set.of(repo.getId()), io.releasehub.domain.iteration.IterationStatus.ACTIVE, now, now));
            taskVersions.saveWithVersion(iterationKey, repo.getId().value(), "1.0.0", "1.1.0-SNAPSHOT", "1.1.0",
                    feature, "SYSTEM", now, io.releasehub.domain.iteration.BranchCreationMode.NAMED);
            taskBindings.attach(io.releasehub.domain.releasewindow.ReleaseWindowId.of(windowId),
                    io.releasehub.domain.iteration.IterationKey.of(iterationKey), now);
            taskBindings.updateReleaseBranch(windowId, iterationKey, release, now);
        });
        var admitted = taskPlans.admit(windowId, List.of(repo.getId().value()), List.of(iterationKey),
                true, "branch-proof-operator", UUID.randomUUID().toString());
        SERVER.reply("GET", canonicalEndpoint + "/repository/branches/"
                + URLEncoder.encode(feature, StandardCharsets.UTF_8), 404, "{}");
        SERVER.reply("GET", canonicalEndpoint + "/repository/branches/main", 200,
                "{\"name\":\"main\",\"commit\":{\"id\":\"" + sha + "\"}}");
        return taskRuns.findById(admitted.runId()).orElseThrow();
    }

    private void assertUnconfirmedBranchTask(String runId) {
        var tasks = taskRuns.findTasks(runId);
        var run = taskRuns.findById(runId).orElseThrow();
        var operations = journal.listByRun(runId, 0, 20);
        assertThat(tasks).hasSize(4);
        assertThat(run.getItems()).hasSize(1);
        assertThat(run.getItems().getFirst().getSteps()).hasSize(1);
        assertThat(operations).hasSize(1);
        SERVER.verify("POST", canonicalEndpoint + "/repository/branches", 1);
        assertThat(SERVER.writeCount()).isEqualTo(1);
        assertThat(SERVER.body("POST", canonicalEndpoint + "/repository/branches").path("ref").asText()).isEqualTo(sha);
        var operation = operations.getFirst();
        org.assertj.core.api.SoftAssertions.assertSoftly(soft -> {
            soft.assertThat(tasks.getFirst().status()).as("branch task must not succeed").isEqualTo("UNKNOWN");
            soft.assertThat(run.getItems().getFirst().getSteps().getFirst().result()).isEqualTo(RunItemResult.UNKNOWN);
            soft.assertThat(run.getExecutionStatus().name()).isEqualTo("NEEDS_REVIEW");
            soft.assertThat(operation.state()).as("unconfirmed response must not be APPLIED").isEqualTo("UNKNOWN");
            soft.assertThat(operation.blocked()).as("repository guard must remain held").isTrue();
            soft.assertThat(tasks.subList(1, tasks.size())).allMatch(task -> "SKIPPED".equals(task.status()));
        });
        assertThat(journal.find(operation.id()).orElseThrow().events()).noneMatch(event -> "APPLIED".equals(event.type()));
    }

    private void assertConfirmedBranchTask(String runId, String feature) throws Exception {
        var tasks = taskRuns.findTasks(runId);
        var run = taskRuns.findById(runId).orElseThrow();
        var operations = journal.listByRun(runId, 0, 20);
        assertThat(tasks).hasSize(4);
        assertThat(tasks.getFirst().status()).isEqualTo("COMPLETED");
        assertThat(tasks.subList(1, tasks.size())).allMatch(task -> "PENDING".equals(task.status()));
        assertThat(run.getItems().getFirst().getSteps()).hasSize(1);
        assertThat(run.getItems().getFirst().getSteps().getFirst().result()).isEqualTo(RunItemResult.CREATED);
        assertThat(run.getItems().getFirst().getMetadata()).containsEntry("git.projectId", String.valueOf(project));
        assertThat(operations).hasSize(1);
        var operation = operations.getFirst();
        assertThat(operation.state()).isEqualTo("APPLIED");
        assertThat(operation.blocked()).isFalse();
        SERVER.verify("POST", canonicalEndpoint + "/repository/branches", 1);
        assertThat(SERVER.writeCount()).isEqualTo(1);
        var detail = journal.find(operation.id()).orElseThrow();
        var applied = detail.events().stream().filter(event -> "APPLIED".equals(event.type())).toList();
        assertThat(applied).hasSize(1);
        assertThat(applied.getFirst().sequence()).isEqualTo(2);
        var proof = new com.fasterxml.jackson.databind.ObjectMapper().readTree(applied.getFirst().evidenceJson());
        assertThat(proof.path("httpStatus").asInt()).isEqualTo(201);
        assertThat(proof.path("name").isTextual()).isTrue();
        assertThat(proof.path("name").asText()).isEqualTo(feature);
        assertThat(proof.path("commit").path("id").isTextual()).as("persist the canonical response field").isTrue();
        assertThat(proof.path("commit").path("id").asText()).isEqualToIgnoringCase(sha);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {40, 64})
    void createMrRequestTaskSendsCanonicalCommandThroughOriginalCheckpoint(int shaLength) throws Exception {
        sha = "aBcD".repeat(shaLength / 4);
        var run = prepareMrCreationRun();
        String runId = run.getId().value();
        var plan = run.getItems().getFirst().getMetadata();
        String source = plan.get("plan.featureBranch");
        String target = plan.get("plan.releaseBranch");
        String title = "Merge " + source + " into " + target;
        String mergePath = canonicalEndpoint + "/merge_requests/7/merge";
        String mergedSha = "e".repeat(shaLength);
        SERVER.reply("POST", canonicalEndpoint + "/merge_requests", 201, mrCreationResponse(run, "checking").toString());
        SERVER.reply("GET", canonicalEndpoint + "/merge_requests/7", 200, mrCreationResponse(run, "mergeable").toString());
        SERVER.reply("PUT", mergePath, 200, mrCreationResponse(run, "mergeable")
                .put("state", "merged").put("merge_commit_sha", mergedSha).toString());

        assertThat(taskService.executeNextTask(runId)).isTrue();
        assertConfirmedMrCreation(runId);
        var creation = journal.listByRun(runId, 0, 20).getFirst();
        String mrTaskId = taskRuns.findTasks(runId).get(2).taskId();
        assertThat(jdbc.queryForObject("select task_id from git_operation where id=?", String.class, creation.id()))
                .isEqualTo(mrTaskId);
        assertThat(jdbc.queryForObject("select claim_token from git_operation where id=?", String.class, creation.id()))
                .isNotBlank();
        assertThat(creation.runId()).isEqualTo(runId);

        assertThat(taskService.executeNextTask(runId)).isTrue();
        var completed = taskRuns.findById(runId).orElseThrow();
        assertThat(completed.getExecutionStatus().name()).isEqualTo("DONE");
        assertThat(taskRuns.findTasks(runId).stream().map(RunPort.TaskView::status))
                .containsExactly("COMPLETED", "COMPLETED", "COMPLETED", "COMPLETED");
        assertThat(completed.getItems().getFirst().getSteps().get(2).result()).isEqualTo(RunItemResult.CREATED);
        assertThat(completed.getItems().getFirst().getSteps().get(3).result()).isEqualTo(RunItemResult.MERGED);
        assertThat(completed.getItems().getFirst().getMetadata())
                .containsEntry("git.sourceSha", sha).containsEntry("git.mrIid", "7").containsEntry("git.mergeSha", mergedSha);
        assertThat(journal.listByRun(runId, 0, 20)).hasSize(2)
                .allMatch(operation -> operation.state().equals("APPLIED") && !operation.blocked());
        SERVER.verify("POST", canonicalEndpoint + "/merge_requests", 1);
        SERVER.verify("PUT", mergePath, 1);
        assertThat(SERVER.writeCount()).isEqualTo(2);
        assertThat(SERVER.body("PUT", mergePath).path("sha").textValue()).isEqualTo(sha);
        assertNoNamespaceWrites();
        createMrRequestAssertCompleteProof(creation, source, target, title);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {40, 64})
    void createMrRequestNonTaskMergeSendsCanonicalCommandWithoutTaskMetadata(int shaLength) throws Exception {
        sha = "aBcD".repeat(shaLength / 4);
        readyForNewMerge();
        String title = "Merge feature into main";
        String mergedSha = "e".repeat(shaLength);
        SERVER.reply("POST", canonicalEndpoint + "/merge_requests", 201, mergeRequest("opened"));
        var merged = (com.fasterxml.jackson.databind.node.ObjectNode) new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(mergeRequest("merged"));
        SERVER.reply("PUT", canonicalEndpoint + "/merge_requests/7/merge", 200,
                merged.put("merge_commit_sha", mergedSha).toString());

        // This is the same non-Task composite entry called by executeCleanup -> executeMerge.
        var result = factory.getAdapter(GitProvider.GITLAB).mergeBranch(
                repo.getCloneUrl(), "fixture-token", "feature", "main", title);

        assertThat(result.status()).isEqualTo(io.releasehub.domain.run.MergeStatus.SUCCESS);
        assertThat(result.mrEvidence().iid()).isEqualTo("7");
        assertThat(result.mrEvidence().sourceSha()).isEqualTo(sha);
        assertThat(result.mrEvidence().mergeSha()).isEqualTo(mergedSha);
        var operations = journal.list(repo.getId().value(), 0, 20);
        assertThat(operations).hasSize(2).allMatch(operation -> operation.state().equals("APPLIED") && !operation.blocked());
        var creation = operations.stream().filter(operation -> operation.action().equals("CREATE_MR")).findFirst().orElseThrow();
        createMrRequestAssertNoTaskMetadata(creation);
        SERVER.verify("POST", canonicalEndpoint + "/merge_requests", 1);
        SERVER.verify("PUT", canonicalEndpoint + "/merge_requests/7/merge", 1);
        assertThat(SERVER.writeCount()).isEqualTo(2);
        assertThat(SERVER.body("PUT", canonicalEndpoint + "/merge_requests/7/merge").path("sha").textValue()).isEqualTo(sha);
        assertNoNamespaceWrites();
        createMrRequestAssertCompleteProof(creation, "feature", "main", title);
    }

    @Test
    void createMrRequestTaskKeepsAdvancedSourceUnknownWithoutCreatedCheckpointOrTry() {
        var run = prepareMrCreationRun();
        String runId = run.getId().value();
        SERVER.reply("POST", canonicalEndpoint + "/merge_requests", 201,
                mrCreationResponse(run, "mergeable").put("sha", "b".repeat(40)).toString());

        assertThat(taskService.executeNextTask(runId)).isTrue();

        assertUnconfirmedMrCreation(runId);
        var operation = journal.listByRun(runId, 0, 20).getFirst();
        var detail = journal.find(operation.id()).orElseThrow();
        assertThat(operation.state()).isEqualTo("UNKNOWN");
        assertThat(operation.blocked()).isTrue();
        assertThat(detail.events()).extracting(GitOperationJournalPort.Event::type)
                .containsExactly("INTENT_RECORDED", "SEND_STARTED", "UNKNOWN");
        assertThat(taskRuns.findById(runId).orElseThrow().getItems().getFirst().getSteps())
                .filteredOn(step -> step.actionType() == ActionType.ENSURE_MR)
                .extracting(RunStep::result).containsExactly(RunItemResult.UNKNOWN);
        int requests = SERVER.requests.size();
        assertThat(taskService.executeNextTask(runId)).isFalse();
        assertThat(SERVER.requests).hasSize(requests);
        SERVER.verify("GET", canonicalEndpoint + "/merge_requests/7", 0);
        SERVER.verify("POST", canonicalEndpoint + "/merge_requests", 1);
        SERVER.verify("PUT", canonicalEndpoint + "/merge_requests/7/merge", 0);
    }

    @Test
    void createMrRequestNonTaskKeepsAdvancedSourceUnknownWithoutTryOrReplay() throws Exception {
        readyForNewMerge();
        var response = (com.fasterxml.jackson.databind.node.ObjectNode) new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(mergeRequest("opened"));
        SERVER.reply("POST", canonicalEndpoint + "/merge_requests", 201, response.put("sha", "b".repeat(40)).toString());

        var result = factory.getAdapter(GitProvider.GITLAB).mergeBranch(
                repo.getCloneUrl(), "fixture-token", "feature", "main", "Merge feature into main");

        assertThat(result.status()).isEqualTo(io.releasehub.domain.run.MergeStatus.UNKNOWN);
        var operations = journal.list(repo.getId().value(), 0, 20);
        assertThat(operations).hasSize(1);
        var operation = operations.getFirst();
        createMrRequestAssertNoTaskMetadata(operation);
        assertThat(operation.state()).isEqualTo("UNKNOWN");
        assertThat(operation.blocked()).isTrue();
        var events = journal.find(operation.id()).orElseThrow().events();
        assertThat(events).extracting(GitOperationJournalPort.Event::type)
                .containsExactly("INTENT_RECORDED", "SEND_STARTED", "UNKNOWN");
        SERVER.verify("GET", canonicalEndpoint + "/merge_requests/7", 0);
        SERVER.verify("POST", canonicalEndpoint + "/merge_requests", 1);
        SERVER.verify("PUT", canonicalEndpoint + "/merge_requests/7/merge", 0);
        assertNextWriteBlocked(1);
        assertThat(journal.find(operation.id()).orElseThrow().events()).isEqualTo(events);
        assertNoNamespaceWrites();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"missingDescription", "emptyQuery", "foreignMarker"})
    void createMrRequestRejectsMalformedHttpCommandBeforeAnySend(String defect) {
        var body = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode()
                .put("source_branch", "feature").put("target_branch", "main").put("title", "Merge feature into main")
                .put("description", "").put("remove_source_branch", false);
        if (defect.equals("missingDescription")) body.remove("description");
        if (defect.equals("foreignMarker")) body.put("description", "ReleaseHub-Operation: unrelated-operation");
        String query = defect.equals("emptyQuery") ? "?" : "";
        var uri = java.net.URI.create(SERVER.baseUrl() + endpoint + "/merge_requests" + query);
        if (defect.equals("emptyQuery")) assertThat(uri.getRawQuery()).isEmpty();
        var headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        headers.set("PRIVATE-TOKEN", "fixture-token");
        headers.set("X-ReleaseHub-Expected-Project-Id", String.valueOf(project));
        headers.set("X-ReleaseHub-Expected-Commit-Sha", sha);
        Object target = AopTestUtils.getUltimateTargetObject(factory.getAdapter(GitProvider.GITLAB));
        var http = (RestTemplate) ReflectionTestUtils.getField(target, "restTemplate");
        assertThat(http).isNotNull();
        SERVER.reply("POST", canonicalEndpoint + "/merge_requests", 201, mergeRequest("opened"));

        Throwable rejected = catchThrowable(() -> http.exchange(uri, org.springframework.http.HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(body.toString(), headers), String.class));

        var operations = journal.list(repo.getId().value(), 0, 20);
        org.assertj.core.api.SoftAssertions.assertSoftly(soft -> {
            soft.assertThat(rejected).as("invalid command must be rejected before Journal sending").isInstanceOf(BusinessException.class);
            soft.assertThat(operations).as("invalid input must not become an admitted intent").isEmpty();
            soft.assertThat(operations.stream().flatMap(operation -> journal.find(operation.id()).orElseThrow().events().stream())
                    .filter(event -> event.type().equals("SEND_STARTED")).count()).as("durable SEND count").isZero();
            soft.assertThat(SERVER.requests.stream().filter(request -> request.method().equals("POST")
                    && request.path().equals(canonicalEndpoint + "/merge_requests")).count()).as("actual MR POST count").isZero();
            soft.assertThat(SERVER.writeCount()).as("all actual HTTP writes").isZero();
        });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {201, 206})
    void projectIdentityStatusFailureStopsCurrentTaskBeforeNumericReads(int status) {
        var before = prepareMrCreationRun();
        String runId = before.getId().value();
        var originalItem = before.getItems().getFirst();
        var prefixTasks = taskRuns.findTasks(runId).subList(0, 2);
        String taskId = taskRuns.findTasks(runId).stream()
                .filter(task -> task.actionType() == ActionType.ENSURE_MR).findFirst().orElseThrow().taskId();
        String mrPath = canonicalEndpoint + "/merge_requests";
        String opened = mrCreationResponse(before, "mergeable").toString();
        SERVER.reply("POST", mrPath, 201, opened);
        SERVER.reply("GET", mrPath + "/7", 200, opened);
        SERVER.reply("GET", endpoint, status, "{\"id\":" + project + "}");
        int requestStart = SERVER.requests.size();

        assertThat(taskService.executeNextTask(runId)).isTrue();

        var failed = taskRuns.findById(runId).orElseThrow();
        var item = failed.getItems().getFirst();
        var tasks = taskRuns.findTasks(runId);
        assertThat(tasks.stream().map(RunPort.TaskView::status))
                .containsExactly("COMPLETED", "COMPLETED", "FAILED", "SKIPPED");
        assertThat(tasks.subList(0, 2)).isEqualTo(prefixTasks);
        assertThat(failed.getExecutionStatus().name()).isEqualTo("FAILED");
        assertThat(failed.getFinishedAt()).isNotNull();
        assertThat(item.getSteps()).hasSize(3);
        assertThat(item.getSteps().subList(0, 2)).isEqualTo(originalItem.getSteps());
        assertThat(item.getSteps().stream().filter(step -> step.actionType() == ActionType.ENSURE_MR))
                .singleElement().satisfies(step -> assertThat(step.result()).isEqualTo(RunItemResult.FAILED));
        assertThat(item.getMetadata()).containsAllEntriesOf(originalItem.getMetadata())
                .containsEntry("git.projectId", String.valueOf(project))
                .doesNotContainKeys("git.mrIid", "git.mrUrl", "git.mrDisposition");
        assertThat(jdbc.queryForObject("select count(*) from git_operation where task_id=?", Integer.class, taskId)).isZero();
        assertThat(journal.listByRun(runId, 0, 20)).isEmpty();
        var requests = List.copyOf(SERVER.requests.subList(requestStart, SERVER.requests.size()));
        assertThat(requests).extracting(GitLabHttpFixture.Request::method).containsExactly("GET");
        assertThat(requests).extracting(GitLabHttpFixture.Request::path).containsExactly(endpoint);
        SERVER.verify("POST", canonicalEndpoint + "/repository/branches", 0);
        SERVER.verify("POST", mrPath, 0);
        SERVER.verify("PUT", mrPath + "/7/merge", 0);
        assertThat(SERVER.writeCount()).isZero();
        assertNoNamespaceWrites();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "206,numeric,INCOMPLETE", "200,string,MISMATCH",
            "403,numeric,FORBIDDEN", "404,numeric,NOT_FOUND"
    })
    void projectIdentityObservationKeepsUnknownExecutionFacts(int status, String idType, String expectedStatus) throws Exception {
        var run = prepareMrMergeRun(false);
        String runId = run.getId().value();
        String itemId = run.getItems().getFirst().getId().value();
        String mrPath = canonicalEndpoint + "/merge_requests/7";
        var response = mrMergeResponse(run);
        response.remove("merge_commit_sha");
        SERVER.reply("PUT", mrPath + "/merge", 200, response.toString());
        assertThat(taskService.executeNextTask(runId)).isTrue();
        assertUnconfirmedMrMerge(run, false);
        var operation = journal.listByRun(runId, 0, 20).stream()
                .filter(value -> value.action().equals("MERGE_MR")).findFirst().orElseThrow();
        var before = journal.find(operation.id()).orElseThrow();
        var originalIdentity = mergeIntentIdentity(operation.id());
        var originalRun = jdbc.queryForMap("select * from run where id=?", runId);
        var originalItem = jdbc.queryForMap("select * from run_item where id=?", itemId);
        var originalTasks = jdbc.queryForList("select * from run_task where run_id=? order by task_order", runId);
        var originalSteps = jdbc.queryForList("select * from run_step where run_item_id=? order by task_id", itemId);
        var originalMetadata = mergeNotSentMetadata(itemId);
        var originalGuard = jdbc.queryForMap("select * from repo_execution_guard where remote_key=?", operation.remoteKey());
        var otherOperations = journal.listByRun(runId, 0, 20).stream()
                .filter(value -> !value.id().equals(operation.id())).toList();
        assertThat(before.events()).extracting(GitOperationJournalPort.Event::type)
                .containsExactly("INTENT_RECORDED", "SEND_STARTED", "UNKNOWN");
        assertThat(before.observations()).isEmpty();

        var identityBody = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("id", project);
        if (idType.equals("string")) identityBody.put("id", String.valueOf(project));
        SERVER.reply("GET", endpoint, status, identityBody.toString());
        SERVER.reply("GET", mrPath, 200, mrMergeResponse(run).toString());
        int readStart = SERVER.requests.size();
        String key = UUID.randomUUID().toString();

        var observed = service.reconcile(operation.id(), "identity-observer", key, operation.version());

        assertThat(observed.status()).isEqualTo(expectedStatus);
        assertThat(observed.expectedVersion()).isEqualTo(operation.version());
        assertThat(observed.evidenceJson()).isEqualTo("{}");
        var after = journal.find(operation.id()).orElseThrow();
        assertThat(after.operation().version()).isGreaterThan(operation.version());
        assertThat(after.operation().updatedAt()).isAfterOrEqualTo(before.operation().updatedAt());
        assertThat(after.operation()).usingRecursiveComparison().ignoringFields("version", "updatedAt")
                .isEqualTo(before.operation());
        assertThat(after.events()).isEqualTo(before.events());
        assertThat(after.observations()).containsExactly(observed);
        assertThat(mergeIntentIdentity(operation.id())).isEqualTo(originalIdentity);
        assertThat(journal.listByRun(runId, 0, 20).stream().filter(value -> !value.id().equals(operation.id())))
                .containsExactlyElementsOf(otherOperations);
        assertThat(jdbc.queryForMap("select * from run where id=?", runId)).isEqualTo(originalRun);
        assertThat(jdbc.queryForMap("select * from run_item where id=?", itemId)).isEqualTo(originalItem);
        assertThat(jdbc.queryForList("select * from run_task where run_id=? order by task_order", runId)).isEqualTo(originalTasks);
        assertThat(jdbc.queryForList("select * from run_step where run_item_id=? order by task_id", itemId)).isEqualTo(originalSteps);
        assertThat(mergeNotSentMetadata(itemId)).isEqualTo(originalMetadata);
        assertThat(jdbc.queryForMap("select * from repo_execution_guard where remote_key=?", operation.remoteKey())).isEqualTo(originalGuard);
        var requests = List.copyOf(SERVER.requests.subList(readStart, SERVER.requests.size()));
        assertThat(requests).extracting(GitLabHttpFixture.Request::method).containsExactly("GET");
        assertThat(requests).extracting(GitLabHttpFixture.Request::path).containsExactly(endpoint);
        SERVER.verify("POST", canonicalEndpoint + "/repository/branches", 0);
        SERVER.verify("POST", canonicalEndpoint + "/merge_requests", 1);
        SERVER.verify("PUT", mrPath + "/merge", 1);
        assertThat(SERVER.writeCount()).isEqualTo(2);
        assertNoNamespaceWrites();
    }

    @Test
    void noOperationFailureRetriesEnsureUsingTheUnchangedPlan() throws Exception {
        var before = prepareMrCreationRun();
        String runId = before.getId().value();
        String itemId = before.getItems().getFirst().getId().value();
        String source = before.getItems().getFirst().getMetadata().get("plan.featureBranch");
        String target = before.getItems().getFirst().getMetadata().get("plan.releaseBranch");
        String originalSha = sha;
        String sourcePath = canonicalEndpoint + "/repository/branches/" + URLEncoder.encode(source, StandardCharsets.UTF_8);
        String mrPath = canonicalEndpoint + "/merge_requests";
        SERVER.reply("GET", mrPath, 206, "[]");
        SERVER.reply("POST", mrPath, 201, mrCreationResponse(before, "mergeable").toString());

        assertThat(taskService.executeNextTask(runId)).isTrue();

        var failed = taskRuns.findById(runId).orElseThrow();
        var failedItem = failed.getItems().getFirst();
        String failedTaskId = taskRuns.findTasks(runId).stream()
                .filter(task -> task.actionType() == ActionType.ENSURE_MR).findFirst().orElseThrow().taskId();
        assertThat(jdbc.queryForObject("select count(*) from git_operation where task_id=?", Integer.class, failedTaskId)).isZero();
        assertThat(journal.listByRun(runId, 0, 20)).isEmpty();
        SERVER.verify("POST", mrPath, 0);
        SERVER.verify("PUT", mrPath + "/7/merge", 0);
        assertThat(SERVER.writeCount()).isZero();
        assertThat(failedItem.getMetadata()).containsEntry("git.projectId", String.valueOf(project))
                .containsEntry("git.sourceSha", originalSha).containsEntry("git.sourceBranch", source)
                .containsEntry("git.targetBranch", target).containsEntry("git.blockReason", "UNKNOWN")
                .doesNotContainKeys("git.mrIid", "git.mrUrl", "git.mrDisposition");
        assertThat(failedItem.getSteps().stream().filter(step -> step.actionType() == ActionType.ENSURE_MR))
                .singleElement().satisfies(step -> {
                    assertThat(step.message()).isEqualTo("MR_LOOKUP_UNCONFIRMED");
                    assertThat(step.result()).isEqualTo(RunItemResult.FAILED);
                });
        assertThat(taskRuns.findTasks(runId).stream().map(RunPort.TaskView::status))
                .containsExactly("COMPLETED", "COMPLETED", "FAILED", "SKIPPED");
        assertThat(failed.getExecutionStatus().name()).isEqualTo("FAILED");
        var oldRun = jdbc.queryForMap("select * from run where id=?", runId);
        var oldItem = jdbc.queryForMap("select * from run_item where id=?", itemId);
        var oldTasks = jdbc.queryForList("select * from run_task where run_id=? order by task_order", runId);
        var oldSteps = jdbc.queryForList("select * from run_step where run_item_id=? order by task_id", itemId);
        var oldMetadata = mergeNotSentMetadata(itemId);

        // No CREATE_MR intent accepted A; the new Run must read the current revision of the same planned branch.
        sha = "b".repeat(40);
        String currentSha = sha;
        assertThat(currentSha).isNotEqualTo(originalSha);
        SERVER.reply("GET", sourcePath, 200, branchIntentResponse(source, currentSha));
        SERVER.reply("GET", mrPath, 200, "[]");
        SERVER.reply("GET", canonicalEndpoint + "/repository/commits/" + currentSha + "/refs", 200, "[]");
        var admission = taskPlans.admitRetry(failed, List.of(itemId), failed.getOperator(), UUID.randomUUID().toString());
        assertThat(admission.runId()).isNotEqualTo(runId);
        var retry = taskRuns.findById(admission.runId()).orElseThrow();
        assertThat(taskRuns.findTasks(admission.runId()).stream().map(RunPort.TaskView::status))
                .containsExactly("INHERITED", "INHERITED", "PENDING", "PENDING");
        assertThat(retry.getItems().getFirst().getSteps()).isEmpty();
        assertThat(retry.getItems().getFirst().getMetadata())
                .containsEntry("retry.sourceRunId", runId).containsEntry("retry.sourceItemId", itemId)
                .containsEntry("plan.featureBranch", source).containsEntry("plan.releaseBranch", target)
                .containsEntry("git.projectId", String.valueOf(project))
                .doesNotContainKeys("git.sourceSha", "git.mrIid", "git.mrUrl", "git.mrDisposition", "git.blockReason");
        SERVER.reply("POST", mrPath, 201, mrCreationResponse(retry, "mergeable").toString());
        SERVER.reply("GET", mrPath + "/7", 200, mrCreationResponse(retry, "mergeable").toString());
        SERVER.reply("PUT", mrPath + "/7/merge", 200, mrMergeResponse(retry)
                .put("merge_commit_sha", "c".repeat(40)).toString());
        int requestsBeforeRetry = SERVER.requests.size();

        assertThat(taskService.executeNextTask(admission.runId())).isTrue();
        assertThat(taskService.executeNextTask(admission.runId())).isTrue();
        assertThat(taskService.executeNextTask(admission.runId())).isFalse();

        var completed = taskRuns.findById(admission.runId()).orElseThrow();
        assertThat(completed.getExecutionStatus().name()).isEqualTo("DONE");
        assertThat(taskRuns.findTasks(admission.runId()).stream().map(RunPort.TaskView::status))
                .containsExactly("INHERITED", "INHERITED", "COMPLETED", "COMPLETED");
        assertThat(completed.getItems().getFirst().getSteps()).extracting(RunStep::actionType)
                .containsExactly(ActionType.ENSURE_MR, ActionType.TRY_MERGE);
        assertThat(completed.getItems().getFirst().getSteps()).extracting(RunStep::result)
                .containsExactly(RunItemResult.CREATED, RunItemResult.MERGED);
        assertThat(completed.getItems().getFirst().getMetadata())
                .containsEntry("git.projectId", String.valueOf(project)).containsEntry("git.sourceSha", currentSha)
                .containsEntry("git.sourceBranch", source).containsEntry("git.targetBranch", target)
                .containsEntry("git.mrIid", "7").doesNotContainKey("git.blockReason");
        var newOperations = journal.listByRun(admission.runId(), 0, 20);
        assertThat(newOperations).hasSize(2).allMatch(operation -> operation.state().equals("APPLIED") && !operation.blocked());
        assertThat(newOperations).extracting(GitOperationJournalPort.Operation::action)
                .containsExactlyInAnyOrder("CREATE_MR", "MERGE_MR");
        for (var operation : newOperations) {
            assertThat(new com.fasterxml.jackson.databind.ObjectMapper().readTree(operation.targetJson()).path("sha").textValue())
                    .isEqualTo(currentSha);
            assertThat(journal.find(operation.id()).orElseThrow().events()).extracting(GitOperationJournalPort.Event::type)
                    .containsExactly("INTENT_RECORDED", "SEND_STARTED", "APPLIED");
        }
        var retryRequests = List.copyOf(SERVER.requests.subList(requestsBeforeRetry, SERVER.requests.size()));
        assertThat(retryRequests.stream().filter(request -> request.method().equals("GET") && request.path().equals(sourcePath)))
                .hasSize(1);
        assertThat(retryRequests).noneMatch(request -> request.path().equals(canonicalEndpoint
                + "/repository/commits/" + originalSha + "/refs"));
        SERVER.verify("POST", canonicalEndpoint + "/repository/branches", 0);
        SERVER.verify("POST", mrPath, 1);
        SERVER.verify("PUT", mrPath + "/7/merge", 1);
        assertThat(SERVER.body("PUT", mrPath + "/7/merge").path("sha").textValue()).isEqualTo(currentSha);
        assertThat(SERVER.writeCount()).isEqualTo(2);
        assertNoNamespaceWrites();
        assertThat(jdbc.queryForMap("select * from run where id=?", runId)).isEqualTo(oldRun);
        assertThat(jdbc.queryForMap("select * from run_item where id=?", itemId)).isEqualTo(oldItem);
        assertThat(jdbc.queryForList("select * from run_task where run_id=? order by task_order", runId)).isEqualTo(oldTasks);
        assertThat(jdbc.queryForList("select * from run_step where run_item_id=? order by task_id", itemId)).isEqualTo(oldSteps);
        assertThat(mergeNotSentMetadata(itemId)).isEqualTo(oldMetadata);
        assertThat(journal.listByRun(runId, 0, 20)).isEmpty();
    }

    @Test
    void noOperationFailureRetriesTryWithTheOriginalCheckpoint() throws Exception {
        var before = prepareMrMergeRun(false);
        String runId = before.getId().value();
        String itemId = before.getItems().getFirst().getId().value();
        String source = before.getItems().getFirst().getMetadata().get("plan.featureBranch");
        String target = before.getItems().getFirst().getMetadata().get("plan.releaseBranch");
        String originalSha = sha;
        String sourcePath = canonicalEndpoint + "/repository/branches/" + URLEncoder.encode(source, StandardCharsets.UTF_8);
        String mrPath = canonicalEndpoint + "/merge_requests";
        var prefixOperation = journal.listByRun(runId, 0, 20).getFirst();
        assertThat(prefixOperation.action()).isEqualTo("CREATE_MR");
        assertThat(prefixOperation.state()).isEqualTo("APPLIED");
        assertThat(prefixOperation.blocked()).isFalse();
        var prefixRow = jdbc.queryForMap("select * from git_operation where id=?", prefixOperation.id());
        var prefixEvents = journal.find(prefixOperation.id()).orElseThrow().events();
        SERVER.reply("GET", mrPath + "/7", 201, mrCreationResponse(before, "mergeable").toString());
        SERVER.reply("PUT", mrPath + "/7/merge", 200, mrMergeResponse(before).toString());

        assertThat(taskService.executeNextTask(runId)).isTrue();

        var failed = taskRuns.findById(runId).orElseThrow();
        var failedItem = failed.getItems().getFirst();
        String failedTaskId = taskRuns.findTasks(runId).getLast().taskId();
        assertThat(jdbc.queryForObject("select count(*) from git_operation where task_id=?", Integer.class, failedTaskId)).isZero();
        assertThat(journal.listByRun(runId, 0, 20)).hasSize(1);
        assertThat(jdbc.queryForMap("select * from git_operation where id=?", prefixOperation.id())).isEqualTo(prefixRow);
        assertThat(journal.find(prefixOperation.id()).orElseThrow().events()).isEqualTo(prefixEvents);
        SERVER.verify("POST", mrPath, 1);
        SERVER.verify("PUT", mrPath + "/7/merge", 0);
        assertThat(failedItem.getMetadata()).containsAllEntriesOf(before.getItems().getFirst().getMetadata())
                .containsEntry("git.blockReason", "UNKNOWN").containsEntry("git.sourceSha", originalSha);
        assertThat(failedItem.getSteps().stream().filter(step -> step.actionType() == ActionType.TRY_MERGE))
                .singleElement().satisfies(step -> {
                    assertThat(step.message()).isEqualTo("MR_READ_UNCONFIRMED");
                    assertThat(step.result()).isEqualTo(RunItemResult.FAILED);
                });
        assertThat(taskRuns.findTasks(runId).stream().map(RunPort.TaskView::status))
                .containsExactly("COMPLETED", "COMPLETED", "COMPLETED", "FAILED");
        assertThat(failed.getExecutionStatus().name()).isEqualTo("FAILED");
        var oldRun = jdbc.queryForMap("select * from run where id=?", runId);
        var oldItem = jdbc.queryForMap("select * from run_item where id=?", itemId);
        var oldTasks = jdbc.queryForList("select * from run_task where run_id=? order by task_order", runId);
        var oldSteps = jdbc.queryForList("select * from run_step where run_item_id=? order by task_id", itemId);
        var oldMetadata = mergeNotSentMetadata(itemId);

        String advancedSha = "b".repeat(40);
        assertThat(advancedSha).isNotEqualTo(originalSha);
        SERVER.reply("GET", sourcePath, 200, branchIntentResponse(source, advancedSha));
        SERVER.reply("GET", mrPath + "/7", 200, mrCreationResponse(before, "mergeable").toString());
        SERVER.reply("PUT", mrPath + "/7/merge", 200, mrMergeResponse(before)
                .put("merge_commit_sha", "c".repeat(40)).toString());
        var admission = taskPlans.admitRetry(failed, List.of(itemId), failed.getOperator(), UUID.randomUUID().toString());
        assertThat(admission.runId()).isNotEqualTo(runId);
        var retry = taskRuns.findById(admission.runId()).orElseThrow();
        assertThat(taskRuns.findTasks(admission.runId()).stream().map(RunPort.TaskView::status))
                .containsExactly("INHERITED", "INHERITED", "INHERITED", "PENDING");
        assertThat(retry.getItems().getFirst().getSteps()).isEmpty();
        assertThat(retry.getItems().getFirst().getMetadata())
                .containsEntry("retry.sourceRunId", runId).containsEntry("retry.sourceItemId", itemId)
                .containsEntry("git.projectId", String.valueOf(project)).containsEntry("git.mrIid", "7")
                .containsEntry("git.mrUrl", before.getItems().getFirst().getMetadata().get("git.mrUrl"))
                .containsEntry("git.sourceSha", originalSha).containsEntry("git.sourceBranch", source)
                .containsEntry("git.targetBranch", target).containsEntry("git.mrDisposition", "CREATED")
                .doesNotContainKey("git.blockReason");
        int requestsBeforeRetry = SERVER.requests.size();

        assertThat(taskService.executeNextTask(admission.runId())).isTrue();
        assertThat(taskService.executeNextTask(admission.runId())).isFalse();

        var completed = taskRuns.findById(admission.runId()).orElseThrow();
        assertThat(completed.getExecutionStatus().name()).isEqualTo("DONE");
        assertThat(taskRuns.findTasks(admission.runId()).stream().map(RunPort.TaskView::status))
                .containsExactly("INHERITED", "INHERITED", "INHERITED", "COMPLETED");
        assertThat(completed.getItems().getFirst().getSteps()).singleElement().satisfies(step -> {
            assertThat(step.actionType()).isEqualTo(ActionType.TRY_MERGE);
            assertThat(step.result()).isEqualTo(RunItemResult.MERGED);
        });
        assertThat(completed.getItems().getFirst().getMetadata())
                .containsEntry("git.projectId", String.valueOf(project)).containsEntry("git.mrIid", "7")
                .containsEntry("git.sourceSha", originalSha).containsEntry("git.sourceBranch", source)
                .containsEntry("git.targetBranch", target).doesNotContainKey("git.blockReason");
        var newOperations = journal.listByRun(admission.runId(), 0, 20);
        assertThat(newOperations).hasSize(1);
        var newOperation = newOperations.getFirst();
        assertThat(newOperation.action()).isEqualTo("MERGE_MR");
        assertThat(newOperation.state()).isEqualTo("APPLIED");
        assertThat(newOperation.blocked()).isFalse();
        assertThat(new com.fasterxml.jackson.databind.ObjectMapper().readTree(newOperation.targetJson()).path("sha").textValue())
                .isEqualTo(originalSha);
        assertThat(journal.find(newOperation.id()).orElseThrow().events()).extracting(GitOperationJournalPort.Event::type)
                .containsExactly("INTENT_RECORDED", "SEND_STARTED", "APPLIED");
        var retryRequests = List.copyOf(SERVER.requests.subList(requestsBeforeRetry, SERVER.requests.size()));
        assertThat(retryRequests).noneMatch(request -> request.path().equals(sourcePath) || request.path().equals(mrPath));
        SERVER.verify("POST", canonicalEndpoint + "/repository/branches", 0);
        SERVER.verify("POST", mrPath, 1);
        SERVER.verify("PUT", mrPath + "/7/merge", 1);
        assertThat(SERVER.body("PUT", mrPath + "/7/merge").path("sha").textValue()).isEqualTo(originalSha);
        assertThat(SERVER.writeCount()).isEqualTo(2);
        assertNoNamespaceWrites();
        assertThat(jdbc.queryForMap("select * from run where id=?", runId)).isEqualTo(oldRun);
        assertThat(jdbc.queryForMap("select * from run_item where id=?", itemId)).isEqualTo(oldItem);
        assertThat(jdbc.queryForList("select * from run_task where run_id=? order by task_order", runId)).isEqualTo(oldTasks);
        assertThat(jdbc.queryForList("select * from run_step where run_item_id=? order by task_id", itemId)).isEqualTo(oldSteps);
        assertThat(mergeNotSentMetadata(itemId)).isEqualTo(oldMetadata);
        assertThat(jdbc.queryForMap("select * from git_operation where id=?", prefixOperation.id())).isEqualTo(prefixRow);
        assertThat(journal.find(prefixOperation.id()).orElseThrow().events()).isEqualTo(prefixEvents);
        assertThat(journal.listByRun(runId, 0, 20)).hasSize(1);
    }

    @Test
    void noOperationFailureKeepsUnknownWritesBlocked() throws Exception {
        var before = prepareMrMergeRun(true);
        String runId = before.getId().value();
        String itemId = before.getItems().getFirst().getId().value();
        String mrPath = canonicalEndpoint + "/merge_requests";
        var response = mrMergeResponse(before);
        response.remove("merge_commit_sha");
        SERVER.reply("PUT", mrPath + "/7/merge", 200, response.toString());

        assertThat(taskService.executeNextTask(runId)).isTrue();

        var unresolved = taskRuns.findById(runId).orElseThrow();
        assertThat(unresolved.getExecutionStatus().name()).isEqualTo("NEEDS_REVIEW");
        assertThat(taskRuns.findTasks(runId).stream().map(RunPort.TaskView::status))
                .containsExactly("COMPLETED", "COMPLETED", "COMPLETED", "UNKNOWN");
        assertThat(unresolved.getItems().getFirst().getSteps().stream()
                .filter(step -> step.actionType() == ActionType.TRY_MERGE))
                .singleElement().satisfies(step -> assertThat(step.result()).isEqualTo(RunItemResult.UNKNOWN));
        assertThat(unresolved.getItems().getFirst().getMetadata())
                .containsAllEntriesOf(before.getItems().getFirst().getMetadata()).containsEntry("git.blockReason", "UNKNOWN");
        var operations = journal.listByRun(runId, 0, 20);
        assertThat(operations).hasSize(1);
        var operation = operations.getFirst();
        assertThat(operation.action()).isEqualTo("MERGE_MR");
        assertThat(operation.state()).isEqualTo("UNKNOWN");
        assertThat(operation.blocked()).isTrue();
        var events = journal.find(operation.id()).orElseThrow().events();
        assertThat(events).extracting(GitOperationJournalPort.Event::type)
                .containsExactly("INTENT_RECORDED", "SEND_STARTED", "UNKNOWN");
        assertThat(events).extracting(GitOperationJournalPort.Event::sequence).containsExactly(0, 1, 2);
        var proof = new com.fasterxml.jackson.databind.ObjectMapper().readTree(events.getLast().evidenceJson());
        assertThat(proof.path("httpStatus").isIntegralNumber()).isTrue();
        assertThat(proof.path("httpStatus").asInt()).isEqualTo(200);
        var oldRun = jdbc.queryForMap("select * from run where id=?", runId);
        var oldItem = jdbc.queryForMap("select * from run_item where id=?", itemId);
        var oldTasks = jdbc.queryForList("select * from run_task where run_id=? order by task_order", runId);
        var oldSteps = jdbc.queryForList("select * from run_step where run_item_id=? order by task_id", itemId);
        var oldMetadata = mergeNotSentMetadata(itemId);
        var oldOperation = jdbc.queryForMap("select * from git_operation where id=?", operation.id());
        var oldGuard = jdbc.queryForMap("select * from repo_execution_guard where remote_key=?", operation.remoteKey());
        int runsBeforeRetry = jdbc.queryForObject("select count(*) from run", Integer.class);
        int requestsBeforeRetry = SERVER.requests.size();

        assertThatThrownBy(() -> taskPlans.admitRetry(unresolved, List.of(itemId), unresolved.getOperator(), UUID.randomUUID().toString()))
                .isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("select count(*) from run", Integer.class)).isEqualTo(runsBeforeRetry);
        assertThat(taskService.executeNextTask(runId)).isFalse();
        assertThat(SERVER.requests).hasSize(requestsBeforeRetry);
        assertThat(jdbc.queryForMap("select * from run where id=?", runId)).isEqualTo(oldRun);
        assertThat(jdbc.queryForMap("select * from run_item where id=?", itemId)).isEqualTo(oldItem);
        assertThat(jdbc.queryForList("select * from run_task where run_id=? order by task_order", runId)).isEqualTo(oldTasks);
        assertThat(jdbc.queryForList("select * from run_step where run_item_id=? order by task_id", itemId)).isEqualTo(oldSteps);
        assertThat(mergeNotSentMetadata(itemId)).isEqualTo(oldMetadata);
        assertThat(jdbc.queryForMap("select * from git_operation where id=?", operation.id())).isEqualTo(oldOperation);
        assertThat(journal.find(operation.id()).orElseThrow().events()).isEqualTo(events);
        assertThat(jdbc.queryForMap("select * from repo_execution_guard where remote_key=?", operation.remoteKey())).isEqualTo(oldGuard);
        SERVER.verify("POST", canonicalEndpoint + "/repository/branches", 0);
        SERVER.verify("POST", mrPath, 0);
        SERVER.verify("PUT", mrPath + "/7/merge", 1);
        assertThat(SERVER.writeCount()).isEqualTo(1);
        assertNoNamespaceWrites();
    }

    @Test
    void mrReadStatusFreshPartialListCannotAuthorizeCreation() {
        var before = prepareMrCreationRun();
        String runId = before.getId().value();
        String source = before.getItems().getFirst().getMetadata().get("plan.featureBranch");
        String target = before.getItems().getFirst().getMetadata().get("plan.releaseBranch");
        String mrPath = canonicalEndpoint + "/merge_requests";
        SERVER.reply("GET", mrPath, 206, "[]");
        // A wrongful create must succeed, so UNKNOWN cannot come from a missing write fixture.
        SERVER.reply("POST", mrPath, 201, mrCreationResponse(before, "mergeable").toString());
        int requestsBefore = SERVER.requests.size();

        assertThat(taskService.executeNextTask(runId)).isTrue();

        var completed = taskRuns.findById(runId).orElseThrow();
        var item = completed.getItems().getFirst();
        assertThat(taskRuns.findTasks(runId).stream().map(RunPort.TaskView::status))
                .containsExactly("COMPLETED", "COMPLETED", "FAILED", "SKIPPED");
        assertThat(completed.getExecutionStatus().name()).isEqualTo("FAILED");
        assertThat(item.getSteps()).hasSize(3);
        assertThat(item.getSteps().stream().filter(step -> step.actionType() == ActionType.ENSURE_MR))
                .singleElement().satisfies(step -> {
                    assertThat(step.result()).isEqualTo(RunItemResult.FAILED);
                    assertThat(step.message()).isEqualTo("MR_LOOKUP_UNCONFIRMED");
                });
        assertThat(item.getMetadata()).containsAllEntriesOf(before.getItems().getFirst().getMetadata())
                .containsEntry("git.projectId", String.valueOf(project))
                .containsEntry("git.sourceBranch", source).containsEntry("git.targetBranch", target)
                .containsEntry("git.sourceSha", sha).containsEntry("git.blockReason", "UNKNOWN")
                .doesNotContainKeys("git.mrIid", "git.mrUrl", "git.mrDisposition", "git.mergeSha", "git.requestId");
        assertThat(journal.listByRun(runId, 0, 20)).isEmpty();
        var requests = List.copyOf(SERVER.requests.subList(requestsBefore, SERVER.requests.size()));
        assertThat(requests.stream().filter(request -> request.path().equals(mrPath)))
                .singleElement().satisfies(request -> {
                    assertThat(request.method()).isEqualTo("GET");
                    assertThat(request.query()).isEqualTo("scope=all&state=opened&source_branch="
                            + URLEncoder.encode(source, StandardCharsets.UTF_8) + "&target_branch="
                            + URLEncoder.encode(target, StandardCharsets.UTF_8) + "&per_page=100&page=1");
                });
        SERVER.verify("POST", mrPath, 0);
        SERVER.verify("PUT", mrPath + "/7/merge", 0);
        assertThat(SERVER.writeCount()).isZero();
    }

    @Test
    void mrReadStatusPreparedContainmentCannotConfirmAlreadyMerged() throws Exception {
        sha = "aBcD".repeat(10);
        var before = prepareMrCreationRun();
        String runId = before.getId().value();
        String source = before.getItems().getFirst().getMetadata().get("plan.featureBranch");
        String target = before.getItems().getFirst().getMetadata().get("plan.releaseBranch");
        String sourcePath = canonicalEndpoint + "/repository/branches/" + URLEncoder.encode(source, StandardCharsets.UTF_8);
        String refsPath = canonicalEndpoint + "/repository/commits/" + sha + "/refs";
        String mrPath = canonicalEndpoint + "/merge_requests";
        SERVER.reply("POST", mrPath, 201, mrCreationResponse(before, "mergeable").toString());
        var workers = java.util.concurrent.Executors.newSingleThreadExecutor();
        try (var gate = new CreationIntentGate(ActionType.ENSURE_MR, false)) {
            try {
                var oldOwner = workers.submit(() -> taskService.executeNextTask(runId));
                assertThat(gate.entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                var command = gate.command.get();
                String operationId = gate.operationId.get();
                var original = mergeIntentIdentity(operationId);
                var originalEvents = journal.find(operationId).orElseThrow().events();
                assertThat(originalEvents).extracting(GitOperationJournalPort.Event::type)
                        .containsExactly("INTENT_RECORDED");
                var intent = originalEvents.getFirst();
                var originalMetadata = new LinkedHashMap<>(gate.claims.getFirst().metadata());
                gate.expire();
                SERVER.reply("GET", sourcePath, 200, branchIntentResponse(source, "b".repeat(40)));
                SERVER.reply("GET", refsPath, 201, new com.fasterxml.jackson.databind.ObjectMapper()
                        .valueToTree(List.of(Map.of("type", "branch", "name", target))).toString());
                int requestsBeforeResume = SERVER.requests.size();

                assertThat(taskService.executeNextTask(runId)).isTrue();

                var current = gate.claims.getLast();
                assertThat(current.fence().claimToken()).isNotEqualTo(command.taskFence().claimToken());
                assertThat(current.metadata()).isEqualTo(originalMetadata);
                assertThat(current.unsentOperation()).isNotNull();
                assertThat(current.unsentOperation().id()).isEqualTo(operationId);
                assertThat(current.unsentOperation().targetJson()).isEqualTo(command.targetJson());
                assertThat(mergeIntentIdentity(operationId)).isEqualTo(original);
                Map<String, String> patch = new LinkedHashMap<>();
                patch.put("git.requestId", null);
                patch.put("git.blockReason", "UNKNOWN");
                patch.put("git.projectId", String.valueOf(project));
                patch.put("git.sourceBranch", source);
                patch.put("git.targetBranch", target);
                patch.put("git.mrIid", null);
                patch.put("git.mrUrl", null);
                patch.put("git.sourceSha", sha);
                patch.put("git.mergeSha", null);
                patch.put("git.mrDisposition", null);
                mergeNotSentAssertEvent(operationId, current.fence().taskId(), current.fence().claimToken(),
                        RunItemResult.UNKNOWN, patch, intent);
                var metadata = new LinkedHashMap<>(originalMetadata);
                patch.forEach((key, value) -> { if (value == null) metadata.remove(key); else metadata.put(key, value); });
                assertThat(mergeNotSentMetadata(current.itemId())).isEqualTo(metadata);
                assertThat(taskRuns.findTasks(runId).stream().map(RunPort.TaskView::status))
                        .containsExactly("COMPLETED", "COMPLETED", "UNKNOWN", "SKIPPED");
                var completed = taskRuns.findById(runId).orElseThrow();
                assertThat(completed.getExecutionStatus().name()).isEqualTo("NEEDS_REVIEW");
                assertThat(completed.getItems().getFirst().getSteps()).hasSize(3);
                assertThat(completed.getItems().getFirst().getSteps().stream()
                        .filter(step -> step.actionType() == ActionType.ENSURE_MR))
                        .singleElement().satisfies(step -> {
                            assertThat(step.result()).isEqualTo(RunItemResult.UNKNOWN);
                            assertThat(step.message()).isEqualTo("TARGET_CONTAINMENT_UNCONFIRMED");
                        });
                assertThat(journal.listByRun(runId, 0, 20)).hasSize(1);
                var requests = List.copyOf(SERVER.requests.subList(requestsBeforeResume, SERVER.requests.size()));
                assertThat(requests.stream().filter(request -> request.path().equals(refsPath)))
                        .singleElement().satisfies(request -> {
                            assertThat(request.method()).isEqualTo("GET");
                            assertThat(request.query()).isEqualTo("type=branch&per_page=100&page=1");
                        });
                assertThat(requests).noneMatch(request -> request.path().equals(sourcePath)
                        || request.path().equals(mrPath) || request.path().startsWith(mrPath + "/"));
                assertThat(SERVER.writeCount()).isZero();

                var closedRun = jdbc.queryForMap("select * from run where id=?", runId);
                var closedItem = jdbc.queryForMap("select * from run_item where id=?", current.itemId());
                var closedTasks = jdbc.queryForList("select * from run_task where run_id=? order by task_order", runId);
                var closedSteps = jdbc.queryForList("select * from run_step where run_item_id=? order by task_id", current.itemId());
                var closedMetadata = mergeNotSentMetadata(current.itemId());
                var closedOperation = jdbc.queryForMap("select * from git_operation where id=?", operationId);
                var closedEvents = journal.find(operationId).orElseThrow().events();
                var releasedGuard = jdbc.queryForMap("select * from repo_execution_guard where remote_key=?", command.remoteKey());
                assertThat(releasedGuard).containsEntry("session_id", null).containsEntry("operation_id", null)
                        .containsEntry("reason", null);
                gate.release.countDown();
                assertThatThrownBy(() -> oldOwner.get(5, java.util.concurrent.TimeUnit.SECONDS))
                        .isInstanceOf(java.util.concurrent.ExecutionException.class).hasCauseInstanceOf(BusinessException.class);
                assertThat(jdbc.queryForMap("select * from run where id=?", runId)).isEqualTo(closedRun);
                assertThat(jdbc.queryForMap("select * from run_item where id=?", current.itemId())).isEqualTo(closedItem);
                assertThat(jdbc.queryForList("select * from run_task where run_id=? order by task_order", runId)).isEqualTo(closedTasks);
                assertThat(jdbc.queryForList("select * from run_step where run_item_id=? order by task_id", current.itemId())).isEqualTo(closedSteps);
                assertThat(mergeNotSentMetadata(current.itemId())).isEqualTo(closedMetadata);
                assertThat(jdbc.queryForMap("select * from git_operation where id=?", operationId)).isEqualTo(closedOperation);
                assertThat(journal.find(operationId).orElseThrow().events()).isEqualTo(closedEvents);
                assertThat(jdbc.queryForMap("select * from repo_execution_guard where remote_key=?", command.remoteKey()))
                        .isEqualTo(releasedGuard);
                assertThat(SERVER.writeCount()).isZero();
                int requestsBeforeNext = SERVER.requests.size();
                assertThat(taskService.executeNextTask(runId)).isFalse();
                assertThat(SERVER.requests).hasSize(requestsBeforeNext);
            } finally {
                gate.releaseAll(); workers.shutdown();
                assertThat(workers.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    @Test
    void mrReadStatusTryDetailCannotAuthorizeMerge() {
        var before = prepareMrMergeRun(true);
        String runId = before.getId().value();
        String mrPath = canonicalEndpoint + "/merge_requests/7";
        SERVER.reply("GET", mrPath, 201, mrCreationResponse(before, "mergeable").toString());
        // If the invalid read authorizes a PUT, its complete response exposes a real false success.
        SERVER.reply("PUT", mrPath + "/merge", 200, mrMergeResponse(before).toString());
        int requestsBefore = SERVER.requests.size();

        assertThat(taskService.executeNextTask(runId)).isTrue();

        var completed = taskRuns.findById(runId).orElseThrow();
        var item = completed.getItems().getFirst();
        assertThat(taskRuns.findTasks(runId).stream().map(RunPort.TaskView::status))
                .containsExactly("COMPLETED", "COMPLETED", "COMPLETED", "FAILED");
        assertThat(completed.getExecutionStatus().name()).isEqualTo("FAILED");
        assertThat(item.getSteps()).hasSize(4);
        assertThat(item.getSteps().stream().filter(step -> step.actionType() == ActionType.TRY_MERGE))
                .singleElement().satisfies(step -> {
                    assertThat(step.result()).isEqualTo(RunItemResult.FAILED);
                    assertThat(step.message()).isEqualTo("MR_READ_UNCONFIRMED");
                });
        var metadata = new LinkedHashMap<>(before.getItems().getFirst().getMetadata());
        metadata.remove("git.requestId");
        metadata.put("git.blockReason", "UNKNOWN");
        assertThat(item.getMetadata()).isEqualTo(metadata).containsEntry("git.mrDisposition", "MR_REUSED");
        assertThat(journal.listByRun(runId, 0, 20)).isEmpty();
        var requests = List.copyOf(SERVER.requests.subList(requestsBefore, SERVER.requests.size()));
        assertThat(requests.stream().filter(request -> request.path().equals(mrPath)))
                .singleElement().satisfies(request -> assertThat(request.method()).isEqualTo("GET"));
        assertThat(requests).allMatch(request -> request.method().equals("GET"));
        SERVER.verify("POST", canonicalEndpoint + "/merge_requests", 0);
        SERVER.verify("PUT", mrPath + "/merge", 0);
        assertThat(SERVER.writeCount()).isZero();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {40, 64})
    void createMrIntentResumesOriginalPostAndRecordsCurrentSender(int shaLength) throws Exception {
        sha = "aBcD".repeat(shaLength / 4);
        var run = prepareMrCreationRun();
        String runId = run.getId().value();
        String source = run.getItems().getFirst().getMetadata().get("plan.featureBranch");
        String target = run.getItems().getFirst().getMetadata().get("plan.releaseBranch");
        SERVER.reply("POST", canonicalEndpoint + "/merge_requests", 201, mrCreationResponse(run, "mergeable").toString());
        var workers = java.util.concurrent.Executors.newFixedThreadPool(2);
        try (var gate = new CreationIntentGate(ActionType.ENSURE_MR, true)) {
            try {
                var oldOwner = workers.submit(() -> taskService.executeNextTask(runId));
                assertThat(gate.entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                var command = gate.command.get();
                String operationId = gate.operationId.get();
                var original = mergeIntentIdentity(operationId);
                var intent = journal.find(operationId).orElseThrow().events().getFirst();
                var metadata = new LinkedHashMap<>(gate.claims.getFirst().metadata());
                gate.expire();

                var newOwner = workers.submit(() -> taskService.executeNextTask(runId));
                assertThat(gate.responseEntered.await(5, java.util.concurrent.TimeUnit.SECONDS))
                        .as("complete CREATE_MR intent must resume the original POST").isTrue();
                var current = gate.claims.getLast();
                assertThat(current.fence().claimToken()).isNotEqualTo(command.taskFence().claimToken());
                assertThat(current.metadata()).isEqualTo(metadata);
                assertThat(mergeNotSentMetadata(current.itemId())).isEqualTo(metadata);
                assertThat(journal.find(operationId).orElseThrow().operation().state()).isEqualTo("SEND_STARTED");
                assertThat(mergeIntentIdentity(operationId)).isEqualTo(original);
                mergeIntentAssertSender(operationId, current.fence());

                assertThatThrownBy(() -> taskRuns.completeTask(current, taskStep(current, RunItemResult.UNKNOWN),
                        Map.of("git.blockReason", "UNKNOWN"))).isInstanceOf(BusinessException.class);
                assertTaskStatus(current, "RUNNING");
                assertThat(journal.find(operationId).orElseThrow().events()).hasSize(2);
                gate.release.countDown();
                assertThatThrownBy(() -> oldOwner.get(5, java.util.concurrent.TimeUnit.SECONDS))
                        .isInstanceOf(java.util.concurrent.ExecutionException.class).hasCauseInstanceOf(BusinessException.class);
                assertTaskStatus(current, "RUNNING");
                gate.releaseResponse.countDown();

                boolean resumed = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> newOwner.get(5, java.util.concurrent.TimeUnit.SECONDS));
                assertThat(resumed).isTrue();
                branchIntentAssertApplied(operationId, current.fence(), intent);
                assertThat(mergeIntentIdentity(operationId)).isEqualTo(original);
                assertConfirmedMrCreation(runId);
                createMrRequestAssertCompleteProof(journal.find(operationId).orElseThrow().operation(),
                        source, target, "Merge " + source + " into " + target);
                assertThat(jdbc.queryForObject("select count(*) from git_operation where task_id=?",
                        Integer.class, current.fence().taskId())).isEqualTo(1);
                System.out.printf("HTTP_CREATE_MR_INTENT_RESUME shaLength=%d run=%s task=%s operation=%s attempt=%s "
                                + "post=1 originalBytes=true currentSender=true oldOwnerRevoked=true%n",
                        shaLength, runId, current.fence().taskId(), operationId, intent.attemptId());
            } finally {
                gate.releaseAll(); workers.shutdown();
                assertThat(workers.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "createMrIntentPreflight={0}")
    @org.junit.jupiter.params.provider.ValueSource(strings = {"contained_advanced", "contained_deleted", "reused_advanced",
            "source_advanced", "source_deleted", "source_forbidden", "detail_advanced", "detail_fork", "detail_missing",
            "rebound_initial", "rebound_before_post"})
    void createMrIntentPreflightClosesOriginalIntentWithoutPost(String observation) throws Exception {
        sha = "aBcD".repeat(10);
        var run = prepareMrCreationRun();
        String runId = run.getId().value();
        String source = run.getItems().getFirst().getMetadata().get("plan.featureBranch");
        String target = run.getItems().getFirst().getMetadata().get("plan.releaseBranch");
        String sourcePath = canonicalEndpoint + "/repository/branches/" + URLEncoder.encode(source, StandardCharsets.UTF_8);
        String refsPath = canonicalEndpoint + "/repository/commits/" + sha + "/refs";
        String mrPath = canonicalEndpoint + "/merge_requests";
        String reboundEndpoint = "/api/v4/projects/" + (project + 10000);
        SERVER.reply("POST", mrPath, 201, mrCreationResponse(run, "mergeable").toString());
        var workers = java.util.concurrent.Executors.newSingleThreadExecutor();
        try (var gate = new CreationIntentGate(ActionType.ENSURE_MR, false)) {
            try {
                var oldOwner = workers.submit(() -> taskService.executeNextTask(runId));
                assertThat(gate.entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                var command = gate.command.get();
                String operationId = gate.operationId.get();
                var original = mergeIntentIdentity(operationId);
                var intent = journal.find(operationId).orElseThrow().events().getFirst();
                var originalMetadata = new LinkedHashMap<>(gate.claims.getFirst().metadata());
                var originalTask = jdbc.queryForMap("select * from run_task where id=?", command.taskFence().taskId());
                int requestsBeforeResume = SERVER.requests.size();
                gate.expire();

                switch (observation) {
                    case "contained_advanced", "contained_deleted" -> {
                        SERVER.reply("GET", refsPath, 200, new com.fasterxml.jackson.databind.ObjectMapper()
                                .valueToTree(List.of(Map.of("type", "branch", "name", target))).toString());
                        SERVER.reply("GET", sourcePath, observation.equals("contained_deleted") ? 404 : 200,
                                observation.equals("contained_deleted") ? "{}" : branchIntentResponse(source, "b".repeat(40)));
                        SERVER.reply("GET", mrPath, 503, "{}");
                    }
                    case "reused_advanced" -> {
                        SERVER.reply("GET", sourcePath, 200, branchIntentResponse(source, "b".repeat(40)));
                        SERVER.reply("GET", mrPath, 200, "[" + mrCreationResponse(run, "mergeable") + "]");
                        SERVER.reply("GET", mrPath + "/7", 200, mrCreationResponse(run, "mergeable")
                                .put("sha", sha.toUpperCase(java.util.Locale.ROOT)).toString());
                    }
                    case "source_advanced" -> SERVER.reply("GET", sourcePath, 200, branchIntentResponse(source, "b".repeat(40)));
                    case "source_deleted" -> SERVER.reply("GET", sourcePath, 404, "{}");
                    case "source_forbidden" -> SERVER.reply("GET", sourcePath, 403, "{\"message\":\"Forbidden\"}");
                    case "detail_advanced", "detail_fork", "detail_missing" -> {
                        SERVER.reply("GET", mrPath, 200, "[" + mrCreationResponse(run, "mergeable") + "]");
                        var detail = mrCreationResponse(run, "mergeable");
                        if (observation.equals("detail_advanced")) detail.put("sha", "b".repeat(40));
                        if (observation.equals("detail_fork")) detail.put("source_project_id", project + 10000);
                        if (observation.equals("detail_missing")) detail.remove("target_project_id");
                        SERVER.reply("GET", mrPath + "/7", 200, detail.toString());
                    }
                    case "rebound_initial" -> SERVER.reply("GET", endpoint, 200, "{\"id\":" + (project + 10000) + "}");
                    case "rebound_before_post" -> {
                        var interceptors = new java.util.ArrayList<>(gate.http.getInterceptors());
                        interceptors.add((request, body, execution) -> {
                            var response = execution.execute(request, body);
                            if (request.getMethod() == org.springframework.http.HttpMethod.GET
                                    && request.getURI().getRawPath().equals(sourcePath)) {
                                SERVER.reply("GET", endpoint, 200, "{\"id\":" + (project + 10000) + "}");
                            }
                            return response;
                        });
                        gate.http.setInterceptors(interceptors);
                    }
                    default -> throw new AssertionError(observation);
                }

                boolean resumed = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> taskService.executeNextTask(runId));
                assertThat(resumed).isTrue();
                var current = gate.claims.getLast();
                assertThat(current.fence().claimToken()).isNotEqualTo(command.taskFence().claimToken());
                assertThat(current.metadata()).isEqualTo(originalMetadata);
                RunItemResult result = observation.startsWith("contained_") ? RunItemResult.ALREADY_MERGED
                        : observation.equals("reused_advanced") ? RunItemResult.MR_REUSED
                        : observation.equals("source_forbidden") ? RunItemResult.MERGE_BLOCKED : RunItemResult.UNKNOWN;
                var patch = createMrIntentLocalPatch(observation, source, target);
                mergeNotSentAssertEvent(operationId, current.fence().taskId(), current.fence().claimToken(), result, patch, intent);
                assertThat(mergeIntentIdentity(operationId)).isEqualTo(original);
                var metadata = new LinkedHashMap<>(originalMetadata);
                patch.forEach((key, value) -> { if (value == null) metadata.remove(key); else metadata.put(key, value); });
                assertThat(mergeNotSentMetadata(current.itemId())).isEqualTo(metadata);
                boolean complete = result == RunItemResult.ALREADY_MERGED || result == RunItemResult.MR_REUSED;
                assertTaskStatus(current, complete ? "COMPLETED" : result == RunItemResult.UNKNOWN ? "UNKNOWN" : "FAILED");
                assertThat(jdbc.queryForMap("select started_at,task_order from run_task where id=?", current.fence().taskId()))
                        .containsEntry("started_at", originalTask.get("started_at")).containsEntry("task_order", originalTask.get("task_order"));
                assertThat(jdbc.queryForList("select result from run_step where task_id=?", current.fence().taskId()))
                        .singleElement().satisfies(row -> assertThat(row).containsEntry("result", result.name()));
                assertThat(taskRuns.findTasks(runId).getLast().status()).isEqualTo(complete ? "PENDING" : "SKIPPED");
                assertThat(taskRuns.findById(runId).orElseThrow().getExecutionStatus().name())
                        .isEqualTo(complete ? "RUNNING" : result == RunItemResult.UNKNOWN ? "NEEDS_REVIEW" : "FAILED");
                assertThat(journal.listByRun(runId, 0, 20)).hasSize(1);
                assertThat(SERVER.writeCount()).isZero();
                var resumedRequests = List.copyOf(SERVER.requests.subList(requestsBeforeResume, SERVER.requests.size()));
                assertThat(resumedRequests).noneMatch(request -> request.path().startsWith(reboundEndpoint + "/"));
                if (observation.startsWith("contained_")) {
                    assertThat(resumedRequests).noneMatch(request -> request.path().equals(sourcePath)
                            || request.path().equals(mrPath) || request.path().startsWith(mrPath + "/"));
                } else if (observation.equals("reused_advanced") || observation.startsWith("detail_")) {
                    assertThat(resumedRequests).noneMatch(request -> request.path().equals(sourcePath));
                    assertThat(resumedRequests.stream().filter(request -> request.path().equals(mrPath + "/7")).count()).isEqualTo(1);
                } else if (observation.equals("rebound_initial")) {
                    assertThat(resumedRequests).noneMatch(request -> request.path().startsWith(canonicalEndpoint + "/"));
                }

                var closedTask = jdbc.queryForMap("select * from run_task where id=?", current.fence().taskId());
                var closedOperation = jdbc.queryForMap("select * from git_operation where id=?", operationId);
                var closedEvents = journal.find(operationId).orElseThrow().events();
                gate.release.countDown();
                assertThatThrownBy(() -> oldOwner.get(5, java.util.concurrent.TimeUnit.SECONDS))
                        .isInstanceOf(java.util.concurrent.ExecutionException.class).hasCauseInstanceOf(BusinessException.class);
                assertThat(jdbc.queryForMap("select * from run_task where id=?", current.fence().taskId())).isEqualTo(closedTask);
                assertThat(jdbc.queryForMap("select * from git_operation where id=?", operationId)).isEqualTo(closedOperation);
                assertThat(journal.find(operationId).orElseThrow().events()).isEqualTo(closedEvents);
                assertThat(SERVER.writeCount()).isZero();

                if (result == RunItemResult.UNKNOWN) {
                    int requestsBeforeNext = SERVER.requests.size();
                    assertThat(taskService.executeNextTask(runId)).isFalse();
                    assertThat(SERVER.requests).hasSize(requestsBeforeNext);
                    int runsBeforeRetry = jdbc.queryForObject("select count(*) from run", Integer.class);
                    assertThatThrownBy(() -> taskPlans.admitRetry(taskRuns.findById(runId).orElseThrow(),
                            List.of(current.itemId()), "fixture-task-operator", UUID.randomUUID().toString()))
                            .isInstanceOf(BusinessException.class);
                    assertThat(jdbc.queryForObject("select count(*) from run", Integer.class)).isEqualTo(runsBeforeRetry);
                } else if (complete) {
                    if (observation.equals("contained_deleted")) SERVER.reply("GET", refsPath, 200, "[]");
                    if (observation.equals("reused_advanced")) {
                        SERVER.reply("PUT", mrPath + "/7/merge", 200, mrCreationResponse(run, "mergeable")
                                .put("state", "merged").put("merge_commit_sha", "c".repeat(40)).toString());
                    }
                    boolean tried = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> taskService.executeNextTask(runId));
                    assertThat(tried).isTrue();
                    var afterTry = taskRuns.findById(runId).orElseThrow();
                    assertThat(afterTry.getItems().getFirst().getSteps().getLast().result())
                            .isEqualTo(observation.equals("contained_deleted") ? RunItemResult.FAILED
                                    : observation.equals("reused_advanced") ? RunItemResult.MERGED : RunItemResult.ALREADY_MERGED);
                    assertThat(afterTry.getExecutionStatus().name())
                            .isEqualTo(observation.equals("contained_deleted") ? "FAILED" : "DONE");
                    SERVER.verify("POST", mrPath, 0);
                    SERVER.verify("PUT", mrPath + "/7/merge", observation.equals("reused_advanced") ? 1 : 0);
                    if (observation.equals("reused_advanced")) {
                        assertThat(SERVER.body("PUT", mrPath + "/7/merge").path("sha").textValue()).isEqualTo(sha);
                    }
                    assertThat(journal.find(operationId).orElseThrow().events()).isEqualTo(closedEvents);
                }
                System.out.printf("HTTP_CREATE_MR_INTENT_LOCAL observation=%s run=%s task=%s operation=%s result=%s "
                                + "createPost=0 guardReleased=true originalIdentity=true%n",
                        observation, runId, current.fence().taskId(), operationId, result);
            } finally {
                gate.releaseAll(); workers.shutdown();
                assertThat(workers.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    @Test
    void createMrIntentKeepsUnknownAfterGetAAndPostResponseB() throws Exception {
        sha = "aBcD".repeat(10);
        var run = prepareMrCreationRun();
        String runId = run.getId().value();
        SERVER.reply("POST", canonicalEndpoint + "/merge_requests", 201, mrCreationResponse(run, "mergeable").toString());
        var workers = java.util.concurrent.Executors.newSingleThreadExecutor();
        try (var gate = new CreationIntentGate(ActionType.ENSURE_MR, false)) {
            try {
                var oldOwner = workers.submit(() -> taskService.executeNextTask(runId));
                assertThat(gate.entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                String operationId = gate.operationId.get();
                var original = mergeIntentIdentity(operationId);
                var intent = journal.find(operationId).orElseThrow().events().getFirst();
                gate.expire();
                SERVER.reply("POST", canonicalEndpoint + "/merge_requests", 201, mrCreationResponse(run, "mergeable")
                        .put("sha", "b".repeat(40)).toString());

                boolean resumed = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> taskService.executeNextTask(runId));
                assertThat(resumed).isTrue();
                var current = gate.claims.getLast();
                assertThat(current.fence().claimToken()).isNotEqualTo(gate.command.get().taskFence().claimToken());
                assertUnconfirmedMrCreation(runId);
                var detail = journal.find(operationId).orElseThrow();
                assertThat(detail.events()).extracting(GitOperationJournalPort.Event::type)
                        .containsExactly("INTENT_RECORDED", "SEND_STARTED", "UNKNOWN");
                assertThat(detail.events().getFirst()).isEqualTo(intent);
                mergeIntentAssertSender(operationId, current.fence());
                assertThat(mergeIntentIdentity(operationId)).isEqualTo(original);
                assertThat(new com.fasterxml.jackson.databind.ObjectMapper().readTree(detail.operation().targetJson())
                        .path("sha").textValue()).isEqualTo(sha);
                assertThat(SERVER.body("POST", canonicalEndpoint + "/merge_requests").has("sha")).isFalse();
                assertThat(taskService.executeNextTask(runId)).isFalse();
                assertThat(taskRuns.findById(runId).orElseThrow().getItems().getFirst().getSteps())
                        .noneMatch(step -> step.actionType() == ActionType.TRY_MERGE || step.result() == RunItemResult.CREATED);
                var unknownEvents = detail.events();
                gate.release.countDown();
                assertThatThrownBy(() -> oldOwner.get(5, java.util.concurrent.TimeUnit.SECONDS))
                        .isInstanceOf(java.util.concurrent.ExecutionException.class).hasCauseInstanceOf(BusinessException.class);
                assertThat(journal.find(operationId).orElseThrow().events()).isEqualTo(unknownEvents);
                assertThat(journal.find(operationId).orElseThrow().operation().blocked()).isTrue();
                assertThat(SERVER.writeCount()).isEqualTo(1);
                System.out.printf("HTTP_CREATE_MR_INTENT_POST_RACE run=%s task=%s operation=%s post=1 "
                        + "responseB=UNKNOWN originalA=true try=0 guardRetained=true%n", runId, current.fence().taskId(), operationId);
            } finally {
                gate.releaseAll(); workers.shutdown();
                assertThat(workers.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    private Map<String, String> createMrIntentLocalPatch(String observation, String source, String target) {
        if (observation.startsWith("rebound_")) return Map.of("git.blockReason", "UNKNOWN");
        boolean contained = observation.startsWith("contained_");
        boolean reused = observation.equals("reused_advanced");
        Map<String, String> patch = new LinkedHashMap<>();
        patch.put("git.requestId", null);
        patch.put("git.blockReason", contained || reused ? null : observation.equals("source_forbidden") ? "PERMISSION_DENIED" : "UNKNOWN");
        patch.put("git.projectId", String.valueOf(project));
        patch.put("git.sourceBranch", source);
        patch.put("git.targetBranch", target);
        patch.put("git.mrIid", reused ? "7" : null);
        patch.put("git.mrUrl", reused ? SERVER.baseUrl() + "/" + path + "/-/merge_requests/7" : null);
        patch.put("git.sourceSha", sha);
        patch.put("git.mergeSha", null);
        patch.put("git.mrDisposition", contained ? "ALREADY_MERGED" : reused ? "MR_REUSED" : null);
        return patch;
    }

    private void createMrRequestAssertNoTaskMetadata(GitOperationJournalPort.Operation operation) {
        assertThat(operation.runId()).isNull();
        assertThat(jdbc.queryForObject("select task_id from git_operation where id=?", String.class, operation.id())).isNull();
        assertThat(jdbc.queryForObject("select claim_token from git_operation where id=?", String.class, operation.id())).isNull();
    }

    private void createMrRequestAssertCompleteProof(GitOperationJournalPort.Operation operation,
            String source, String target, String title) throws Exception {
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        String marker = "ReleaseHub-Operation: " + operation.id();
        var body = json.createObjectNode().put("source_branch", source).put("target_branch", target).put("title", title)
                .put("description", "\n" + marker).put("remove_source_branch", false);
        String canonical = body.toString();
        var expectedTarget = json.createObjectNode().put("method", "POST").put("suffix", "/merge_requests");
        expectedTarget.setAll(body);
        expectedTarget.put("sha", sha);
        var recorded = jdbc.queryForMap("select remote_key,request_hash,target_json from git_operation where id=?", operation.id());
        var recordedTarget = json.readTree(recorded.get("target_json").toString());
        var operationTarget = json.readTree(operation.targetJson());
        // Independent byte-level oracle: do not use any production canonicalization or hash helper.
        String remoteKey = createMrRequestSha256("GITLAB\n" + SERVER.baseUrl() + "\n" + project);
        String expectedHash = createMrRequestSha256("POST|" + remoteKey + "|/merge_requests||"
                + createMrRequestSha256(canonical) + "|" + sha);
        var requests = SERVER.requests.stream().filter(request -> request.method().equals("POST")
                && request.path().equals(canonicalEndpoint + "/merge_requests")).toList();
        assertThat(requests).hasSize(1);
        var actual = requests.getFirst();
        var actualBody = json.readTree(actual.body());
        var detail = journal.find(operation.id()).orElseThrow();
        assertThat(detail.events()).extracting(GitOperationJournalPort.Event::type)
                .containsExactly("INTENT_RECORDED", "SEND_STARTED", "APPLIED");
        assertThat(detail.events()).extracting(GitOperationJournalPort.Event::sequence).containsExactly(0, 1, 2);
        assertThat(detail.events()).allSatisfy(event -> assertThat(event.attemptId())
                .isEqualTo(detail.events().getFirst().attemptId()));
        var responseProof = json.readTree(detail.events().getLast().evidenceJson());
        org.assertj.core.api.SoftAssertions.assertSoftly(soft -> {
            soft.assertThat(operation.id()).isNotBlank().doesNotContain("\r", "\n");
            soft.assertThat(operation.action()).isEqualTo("CREATE_MR");
            soft.assertThat(operation.repoId()).isEqualTo(repo.getId().value());
            soft.assertThat(operation.provider()).isEqualTo("GITLAB");
            soft.assertThat(operation.baseUrl()).isEqualTo(SERVER.baseUrl());
            soft.assertThat(operation.projectId()).isEqualTo(String.valueOf(project));
            soft.assertThat(operation.remoteKey()).isEqualTo(remoteKey);
            soft.assertThat(recorded.get("remote_key")).isEqualTo(remoteKey);
            soft.assertThat(recordedTarget).as("complete typed eight-field accepted command").isEqualTo(expectedTarget);
            soft.assertThat(operationTarget).isEqualTo(expectedTarget);
            soft.assertThat(recorded.get("request_hash")).as("canonical UTF-8 request fingerprint").isEqualTo(expectedHash);
            soft.assertThat(actual.body()).as("actual HTTP bytes use the canonical five-field request").isEqualTo(canonical);
            soft.assertThat(actualBody).isEqualTo(body);
            soft.assertThat(actual.query()).isNull();
            soft.assertThat(actual.internalHeaders()).isFalse();
            String description = actualBody.path("description").asText();
            soft.assertThat(description).isEqualTo("\n" + marker);
            soft.assertThat(description.lines().filter(marker::equals).count()).isEqualTo(1);
            soft.assertThat(description.indexOf("ReleaseHub-Operation:")).isEqualTo(description.lastIndexOf("ReleaseHub-Operation:"));
            soft.assertThat(responseProof.path("httpStatus").intValue()).isEqualTo(201);
            soft.assertThat(responseProof.path("project_id").intValue()).isEqualTo(project);
            soft.assertThat(responseProof.path("source_project_id").intValue()).isEqualTo(project);
            soft.assertThat(responseProof.path("target_project_id").intValue()).isEqualTo(project);
            soft.assertThat(responseProof.path("source_branch").textValue()).isEqualTo(source);
            soft.assertThat(responseProof.path("target_branch").textValue()).isEqualTo(target);
            soft.assertThat(responseProof.path("sha").textValue()).isEqualTo(sha);
            soft.assertThat(responseProof.path("iid").intValue()).isEqualTo(7);
        });
    }

    private String createMrRequestSha256(String value) throws java.security.NoSuchAlgorithmException {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {200, 202})
    void mrCreationRequiresCreatedStatus(int status) {
        var run = prepareMrCreationRun();
        SERVER.reply("POST", canonicalEndpoint + "/merge_requests", status, mrCreationResponse(run, "mergeable").toString());

        assertThat(taskService.executeNextTask(run.getId().value())).isTrue();

        assertUnconfirmedMrCreation(run.getId().value());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"source_project_id", "target_project_id"})
    void mrCreationRejectsDifferentSourceOrTargetProject(String field) {
        var run = prepareMrCreationRun();
        var response = mrCreationResponse(run, "mergeable").put(field, project + 1);
        SERVER.reply("POST", canonicalEndpoint + "/merge_requests", 201, response.toString());

        assertThat(taskService.executeNextTask(run.getId().value())).isTrue();

        assertUnconfirmedMrCreation(run.getId().value());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"project_id", "source_project_id", "target_project_id",
            "source_branch", "target_branch", "sha", "web_url", "iid"})
    void mrCreationRequiresCanonicalResponseFields(String field) {
        var run = prepareMrCreationRun();
        var response = mrCreationResponse(run, "mergeable");
        response.put("id", 7);
        response.remove(field);
        SERVER.reply("POST", canonicalEndpoint + "/merge_requests", 201, response.toString());

        assertThat(taskService.executeNextTask(run.getId().value())).isTrue();

        assertUnconfirmedMrCreation(run.getId().value());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"project_id", "source_project_id", "target_project_id", "iid", "sha"})
    void mrCreationRequiresResponseFieldTypes(String field) {
        if ("sha".equals(field)) sha = "1".repeat(40);
        var run = prepareMrCreationRun();
        var response = mrCreationResponse(run, "mergeable");
        if ("sha".equals(field)) response.put(field, new java.math.BigInteger(sha));
        else response.put(field, response.path(field).asText());
        SERVER.reply("POST", canonicalEndpoint + "/merge_requests", 201, response.toString());

        assertThat(taskService.executeNextTask(run.getId().value())).isTrue();

        assertUnconfirmedMrCreation(run.getId().value());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"project_id", "source_branch", "target_branch", "sha", "state", "web_url"})
    void mrCreationRejectsContradictingIdentity(String field) {
        var run = prepareMrCreationRun();
        var response = mrCreationResponse(run, "mergeable");
        if ("project_id".equals(field)) response.put(field, project + 1);
        else response.put(field, switch (field) {
            case "source_branch" -> "feature/OTHER";
            case "target_branch" -> "release/OTHER";
            case "sha" -> "b".repeat(40);
            case "state" -> "closed";
            case "web_url" -> "/relative/merge_requests/7";
            default -> throw new AssertionError(field);
        });
        SERVER.reply("POST", canonicalEndpoint + "/merge_requests", 201, response.toString());

        assertThat(taskService.executeNextTask(run.getId().value())).isTrue();

        assertUnconfirmedMrCreation(run.getId().value());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"checking", "not_approved", "ci_still_running"})
    void mrCreationDoesNotWaitForMergeReadiness(String readiness) {
        var run = prepareMrCreationRun();
        SERVER.reply("POST", canonicalEndpoint + "/merge_requests", 201, mrCreationResponse(run, readiness).toString());

        assertThat(taskService.executeNextTask(run.getId().value())).isTrue();

        assertConfirmedMrCreation(run.getId().value());
    }

    @Test void mrCreationPersistsTypedCanonicalProofForOriginalAttempt() throws Exception {
        var run = prepareMrCreationRun();
        var response = mrCreationResponse(run, "checking").put("id", 701)
                .put("description", "private-response-fixture").put("merge_commit_sha", "b".repeat(40));
        SERVER.reply("POST", canonicalEndpoint + "/merge_requests", 201, response.toString());

        assertThat(taskService.executeNextTask(run.getId().value())).isTrue();

        assertConfirmedMrCreation(run.getId().value());
        var operation = journal.listByRun(run.getId().value(), 0, 20).getFirst();
        var events = journal.find(operation.id()).orElseThrow().events();
        assertThat(events).hasSize(3);
        String attempt = jdbc.queryForObject("select attempt_id from git_operation where id=?", String.class, operation.id());
        assertThat(events.stream().map(GitOperationJournalPort.Event::attemptId)).containsOnly(attempt);
        var applied = events.stream().filter(event -> "APPLIED".equals(event.type())).toList();
        assertThat(applied).hasSize(1);
        assertThat(applied.getFirst().sequence()).isEqualTo(2);
        var proof = new com.fasterxml.jackson.databind.ObjectMapper().readTree(applied.getFirst().evidenceJson());
        var fields = new java.util.HashSet<String>();
        proof.fieldNames().forEachRemaining(fields::add);
        org.assertj.core.api.SoftAssertions.assertSoftly(soft -> {
            soft.assertThat(fields).containsExactlyInAnyOrder("httpStatus", "project_id", "source_project_id",
                    "target_project_id", "iid", "source_branch", "target_branch", "sha", "state", "web_url");
            soft.assertThat(proof.path("httpStatus").isIntegralNumber()).isTrue();
            soft.assertThat(proof.path("httpStatus").asInt()).isEqualTo(201);
            for (String field : List.of("project_id", "source_project_id", "target_project_id", "iid")) {
                soft.assertThat(proof.path(field).isIntegralNumber()).as(field + " must retain its JSON integer type").isTrue();
                soft.assertThat(proof.get(field)).as(field + " must come from the response").isEqualTo(response.get(field));
            }
            for (String field : List.of("source_branch", "target_branch", "sha", "state", "web_url")) {
                soft.assertThat(proof.path(field).isTextual()).as(field + " must retain its JSON string type").isTrue();
                soft.assertThat(proof.get(field)).as(field + " must come from the response").isEqualTo(response.get(field));
            }
        });
    }

    @Test void mrCreationNeverQueriesOrAttributesAnExtraMergeCommit() {
        var run = prepareMrCreationRun();
        String target = run.getItems().getFirst().getMetadata().get("plan.releaseBranch");
        String unrelated = "b".repeat(40);
        SERVER.reply("POST", canonicalEndpoint + "/merge_requests", 201,
                mrCreationResponse(run, "checking").put("merge_commit_sha", unrelated).toString());
        assertThat(taskService.executeNextTask(run.getId().value())).isTrue();
        var checkpoint = taskRuns.findById(run.getId().value()).orElseThrow().getItems().getFirst().getMetadata();
        SERVER.reply("GET", canonicalEndpoint + "/repository/commits/" + sha + "/refs", 200,
                new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(
                        List.of(Map.of("type", "branch", "name", target))).toString());
        SERVER.reply("GET", canonicalEndpoint + "/repository/commits/" + unrelated + "/refs", 503, "{}");

        assertThat(taskService.executeNextTask(run.getId().value())).isTrue();

        var completed = taskRuns.findById(run.getId().value()).orElseThrow();
        var item = completed.getItems().getFirst();
        org.assertj.core.api.SoftAssertions.assertSoftly(soft -> {
            soft.assertThat(checkpoint).doesNotContainKey("git.mergeSha");
            soft.assertThat(completed.getExecutionStatus().name()).isEqualTo("DONE");
            soft.assertThat(item.getSteps()).hasSize(4);
            soft.assertThat(item.getSteps().get(2).result()).isEqualTo(RunItemResult.CREATED);
            soft.assertThat(item.getSteps().get(3).result()).isEqualTo(RunItemResult.ALREADY_MERGED);
            soft.assertThat(item.getMetadata()).doesNotContainKey("git.mergeSha");
        });
        SERVER.verify("GET", canonicalEndpoint + "/repository/commits/" + unrelated + "/refs", 0);
        SERVER.verify("POST", canonicalEndpoint + "/merge_requests", 1);
        SERVER.verify("PUT", canonicalEndpoint + "/merge_requests/7/merge", 0);
        assertThat(SERVER.writeCount()).isEqualTo(1);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void mrCreationProjectsHistoricalCheckpointBeforeCheckingChangedRemoteState(boolean targetContainsOriginalSource) {
        var run = prepareMrCreationRun();
        String runId = run.getId().value();
        var claim = taskRuns.claimNextTask(runId).orElseThrow();
        assertThat(claim.actionType()).isEqualTo(ActionType.ENSURE_MR);
        String source = claim.metadata().get("plan.featureBranch");
        String target = claim.metadata().get("plan.releaseBranch");
        String sourcePath = canonicalEndpoint + "/repository/branches/" + URLEncoder.encode(source, StandardCharsets.UTF_8);
        SERVER.reply("POST", canonicalEndpoint + "/merge_requests", 201, mrCreationResponse(run, "checking").toString());
        GitWriteResult<GitBranchPort.MrEvidence> created;
        try (var scope = GitOperationContext.withTask(claim)) {
            created = factory.getAdapter(GitProvider.GITLAB).ensureMergeRequest(
                    repo.getCloneUrl(), "fixture-token", source, target, "historical checkpoint fixture");
        }
        assertThat(created.outcome()).isEqualTo(GitWriteResult.Outcome.APPLIED);
        assertTaskStatus(claim, "RUNNING");
        assertThat(taskRuns.findById(runId).orElseThrow().getItems().getFirst().getSteps()).hasSize(2);
        var operation = journal.listByRun(runId, 0, 20).getFirst();
        assertThat(operation.state()).isEqualTo("APPLIED");
        assertThat(operation.blocked()).isTrue();
        var events = journal.find(operation.id()).orElseThrow().events();
        expireTaskClaim(claim);
        var originalTask = jdbc.queryForMap("select * from run_task where id=?", claim.fence().taskId());

        String advancedSource = "c".repeat(40);
        SERVER.reply("GET", sourcePath, 200, new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(
                Map.of("name", source, "commit", Map.of("id", advancedSource))).toString());
        SERVER.reply("GET", canonicalEndpoint + "/merge_requests/7", 200,
                mrCreationResponse(run, "mergeable").put("state", "closed").put("sha", advancedSource).toString());
        String originalRefsPath = canonicalEndpoint + "/repository/commits/" + sha + "/refs";
        SERVER.reply("GET", originalRefsPath, 200, targetContainsOriginalSource
                ? new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(
                        List.of(Map.of("type", "branch", "name", target))).toString() : "[]");
        int beforeProjection = SERVER.requests.size();

        assertThat(taskService.executeNextTask(runId)).isTrue();

        assertThat(SERVER.requests).hasSize(beforeProjection);
        assertConfirmedMrCreation(runId);
        var projectedTask = jdbc.queryForMap("select * from run_task where id=?", claim.fence().taskId());
        for (String field : List.of("id", "task_type", "claim_token", "started_at", "claim_expires_at")) {
            assertThat(projectedTask.get(field)).as("preserve original task " + field).isEqualTo(originalTask.get(field));
        }
        assertThat(journal.find(operation.id()).orElseThrow().events()).isEqualTo(events);
        assertThat(taskRuns.findById(runId).orElseThrow().getItems().getFirst().getMetadata())
                .containsEntry("git.sourceSha", sha).doesNotContainKey("git.mergeSha");
        int beforeTry = SERVER.requests.size();

        assertThat(taskService.executeNextTask(runId)).isTrue();

        var completed = taskRuns.findById(runId).orElseThrow();
        var item = completed.getItems().getFirst();
        assertThat(completed.getExecutionStatus().name()).isEqualTo(targetContainsOriginalSource ? "DONE" : "FAILED");
        assertThat(taskRuns.findTasks(runId).stream().map(RunPort.TaskView::status))
                .containsExactly("COMPLETED", "COMPLETED", "COMPLETED", targetContainsOriginalSource ? "COMPLETED" : "FAILED");
        assertThat(item.getSteps()).hasSize(4);
        assertThat(item.getSteps().get(2).result()).isEqualTo(RunItemResult.CREATED);
        assertThat(item.getSteps().get(3).result()).isEqualTo(
                targetContainsOriginalSource ? RunItemResult.ALREADY_MERGED : RunItemResult.FAILED);
        assertThat(item.getMetadata()).containsEntry("git.projectId", String.valueOf(project))
                .containsEntry("git.sourceBranch", source).containsEntry("git.targetBranch", target)
                .containsEntry("git.sourceSha", sha).containsEntry("git.mrIid", "7")
                .containsEntry("git.mrUrl", created.evidence().url())
                .containsEntry("git.mrDisposition", targetContainsOriginalSource ? "ALREADY_MERGED" : "CREATED")
                .doesNotContainKey("git.mergeSha");
        assertThat(journal.listByRun(runId, 0, 20)).hasSize(1)
                .allMatch(value -> value.state().equals("APPLIED") && !value.blocked());
        assertThat(journal.find(operation.id()).orElseThrow().events()).isEqualTo(events);
        var tryRequests = SERVER.requests.subList(beforeTry, SERVER.requests.size());
        assertThat(tryRequests).noneMatch(request -> GitLabHttpFixture.isWrite(request.method())
                || request.path().equals(sourcePath) || request.path().equals(canonicalEndpoint + "/merge_requests"));
        assertThat(tryRequests).filteredOn(request -> request.method().equals("GET") && request.path().equals(originalRefsPath))
                .hasSize(1);
        SERVER.verify("GET", canonicalEndpoint + "/merge_requests/7", targetContainsOriginalSource ? 0 : 1);
        SERVER.verify("GET", canonicalEndpoint + "/repository/commits/" + advancedSource + "/refs", 0);
        SERVER.verify("POST", canonicalEndpoint + "/merge_requests", 1);
        SERVER.verify("PUT", canonicalEndpoint + "/merge_requests/7/merge", 0);
        assertThat(SERVER.writeCount()).isEqualTo(1);
        assertNoNamespaceWrites();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {40, 64})
    void mrCreationMatchesFrozenShaIgnoringHexCase(int length) throws Exception {
        sha = "aBcD".repeat(length / 4);
        var run = prepareMrCreationRun();
        String observed = sha.toLowerCase(java.util.Locale.ROOT);
        SERVER.reply("POST", canonicalEndpoint + "/merge_requests", 201,
                mrCreationResponse(run, "checking").put("sha", observed).toString());

        assertThat(taskService.executeNextTask(run.getId().value())).isTrue();

        assertThat(taskRuns.findTasks(run.getId().value()).stream().map(RunPort.TaskView::status))
                .containsExactly("COMPLETED", "COMPLETED", "COMPLETED", "PENDING");
        var item = taskRuns.findById(run.getId().value()).orElseThrow().getItems().getFirst();
        assertThat(item.getSteps().get(2).result()).isEqualTo(RunItemResult.CREATED);
        assertThat(item.getMetadata()).containsEntry("git.sourceSha", observed).doesNotContainKey("git.mergeSha");
        var operation = journal.listByRun(run.getId().value(), 0, 20).getFirst();
        assertThat(operation.state()).isEqualTo("APPLIED");
        assertThat(operation.blocked()).isFalse();
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        assertThat(json.readTree(operation.targetJson()).path("sha").asText()).isEqualTo(sha);
        var proof = json.readTree(journal.find(operation.id()).orElseThrow().events().getLast().evidenceJson());
        assertThat(proof.path("sha").asText()).isEqualTo(observed);
        SERVER.verify("POST", canonicalEndpoint + "/merge_requests", 1);
        SERVER.verify("PUT", canonicalEndpoint + "/merge_requests/7/merge", 0);
        assertThat(SERVER.writeCount()).isEqualTo(1);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"40,false", "64,false", "40,true", "64,true"})
    void mrCreationConsumesEquivalentShaThroughOriginalTry(int length, boolean projectHistoricalResult) throws Exception {
        sha = "a".repeat(length);
        var run = prepareMrCreationRun();
        String runId = run.getId().value();
        String responseSha = sha.toUpperCase(java.util.Locale.ROOT);
        String mergedSha = "b".repeat(length);
        String mergePath = canonicalEndpoint + "/merge_requests/7/merge";
        SERVER.reply("POST", canonicalEndpoint + "/merge_requests", 201,
                mrCreationResponse(run, "checking").put("sha", responseSha).toString());
        SERVER.reply("GET", canonicalEndpoint + "/repository/commits/" + responseSha + "/refs", 200, "[]");
        SERVER.reply("GET", canonicalEndpoint + "/merge_requests/7", 200, mrCreationResponse(run, "mergeable").toString());
        SERVER.reply("PUT", mergePath, 200,
                mrCreationResponse(run, "mergeable").put("state", "merged").put("merge_commit_sha", mergedSha).toString());

        if (projectHistoricalResult) {
            var claim = taskRuns.claimNextTask(runId).orElseThrow();
            assertThat(claim.actionType()).isEqualTo(ActionType.ENSURE_MR);
            try (var scope = GitOperationContext.withTask(claim)) {
                var created = factory.getAdapter(GitProvider.GITLAB).ensureMergeRequest(repo.getCloneUrl(), "fixture-token",
                        claim.metadata().get("plan.featureBranch"), claim.metadata().get("plan.releaseBranch"), "case identity fixture");
                assertThat(created.outcome()).isEqualTo(GitWriteResult.Outcome.APPLIED);
            }
            expireTaskClaim(claim);
            int beforeProjection = SERVER.requests.size();
            assertThat(taskService.executeNextTask(runId)).isTrue();
            assertThat(SERVER.requests).hasSize(beforeProjection);
        } else {
            assertThat(taskService.executeNextTask(runId)).isTrue();
        }
        assertThat(taskRuns.findTasks(runId).stream().map(RunPort.TaskView::status))
                .containsExactly("COMPLETED", "COMPLETED", "COMPLETED", "PENDING");
        assertThat(taskRuns.findById(runId).orElseThrow().getItems().getFirst().getMetadata())
                .containsEntry("git.sourceSha", responseSha).containsEntry("git.mrIid", "7");
        var creation = journal.listByRun(runId, 0, 20).getFirst();
        var createEvent = journal.find(creation.id()).orElseThrow().events().getLast();
        assertThat(new com.fasterxml.jackson.databind.ObjectMapper().readTree(createEvent.evidenceJson()).path("sha").asText())
                .isEqualTo(responseSha);

        assertThat(taskService.executeNextTask(runId)).isTrue();

        var completed = taskRuns.findById(runId).orElseThrow();
        var item = completed.getItems().getFirst();
        org.assertj.core.api.SoftAssertions.assertSoftly(soft -> {
            soft.assertThat(completed.getExecutionStatus().name()).isEqualTo("DONE");
            soft.assertThat(taskRuns.findTasks(runId).stream().map(RunPort.TaskView::status))
                    .containsExactly("COMPLETED", "COMPLETED", "COMPLETED", "COMPLETED");
            soft.assertThat(item.getSteps().get(3).result()).isEqualTo(RunItemResult.MERGED);
            soft.assertThat(item.getMetadata()).containsEntry("git.sourceSha", responseSha)
                    .containsEntry("git.mrIid", "7").containsEntry("git.mergeSha", mergedSha);
            soft.assertThat(journal.listByRun(runId, 0, 20)).hasSize(2)
                    .allMatch(operation -> operation.state().equals("APPLIED") && !operation.blocked());
            soft.assertThat(SERVER.writeCount()).isEqualTo(2);
            soft.assertThat(SERVER.requests.stream().filter(request -> request.method().equals("PUT")
                    && request.path().equals(mergePath))).hasSize(1);
        });
        assertThat(SERVER.body("PUT", mergePath).path("sha").asText()).isEqualTo(responseSha);
        SERVER.verify("POST", canonicalEndpoint + "/merge_requests", 1);
        SERVER.verify("GET", canonicalEndpoint + "/merge_requests/7", 1);
        assertNoNamespaceWrites();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {201, 202, 204})
    void mrMergeRequiresSuccessfulStatus(int status) {
        var run = prepareMrMergeRun(false);
        SERVER.reply("PUT", canonicalEndpoint + "/merge_requests/7/merge", status, mrMergeResponse(run).toString());

        assertThat(taskService.executeNextTask(run.getId().value())).isTrue();

        assertUnconfirmedMrMerge(run, false);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"project_id", "source_project_id", "target_project_id", "iid"})
    void mrMergeRejectsAContradictingProjectOrIid(String field) {
        var run = prepareMrMergeRun(false);
        var response = mrMergeResponse(run).put("id", 7).put(field, "iid".equals(field) ? 8 : project + 1);
        SERVER.reply("PUT", canonicalEndpoint + "/merge_requests/7/merge", 200, response.toString());

        assertThat(taskService.executeNextTask(run.getId().value())).isTrue();

        assertUnconfirmedMrMerge(run, false);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"iid", "source_project_id", "target_branch", "web_url", "sha", "state"})
    void mrMergeRequiresCanonicalIdentityFields(String field) {
        var run = prepareMrMergeRun(false);
        var response = mrMergeResponse(run).put("id", 7);
        response.remove(field);
        SERVER.reply("PUT", canonicalEndpoint + "/merge_requests/7/merge", 200, response.toString());

        assertThat(taskService.executeNextTask(run.getId().value())).isTrue();

        assertUnconfirmedMrMerge(run, false);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"project_id", "source_project_id", "target_project_id", "iid", "sha", "floating-source-project"})
    void mrMergeRequiresOriginalIdentityAndShaTypes(String field) {
        if ("sha".equals(field)) sha = "1".repeat(40);
        var run = prepareMrMergeRun(false);
        var response = mrMergeResponse(run);
        if ("sha".equals(field)) response.put(field, new java.math.BigInteger(sha));
        else if ("floating-source-project".equals(field)) response.put("source_project_id", (double) project);
        else response.put(field, response.path(field).asText());
        SERVER.reply("PUT", canonicalEndpoint + "/merge_requests/7/merge", 200, response.toString());

        assertThat(taskService.executeNextTask(run.getId().value())).isTrue();

        assertUnconfirmedMrMerge(run, false);
    }

    @Test void mrMergeRejectsAnotherSourceRevision() {
        var run = prepareMrMergeRun(false);
        var response = mrMergeResponse(run).put("sha", "c".repeat(40));
        SERVER.reply("PUT", canonicalEndpoint + "/merge_requests/7/merge", 200, response.toString());

        assertThat(taskService.executeNextTask(run.getId().value())).isTrue();

        assertUnconfirmedMrMerge(run, false);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"missing", "number", "boolean", "object", "empty", "string-null"})
    void mrMergeRequiresPresentNullableMergeCommitSha(String invalid) {
        var run = prepareMrMergeRun(false);
        var response = mrMergeResponse(run).put("squash_commit_sha", "c".repeat(40));
        response.putObject("commit").put("id", "d".repeat(40));
        response.putObject("object").put("sha", "e".repeat(40));
        switch (invalid) {
            case "missing" -> response.remove("merge_commit_sha");
            case "number" -> response.put("merge_commit_sha", new java.math.BigInteger("1".repeat(40)));
            case "boolean" -> response.put("merge_commit_sha", true);
            case "object" -> response.putObject("merge_commit_sha").put("id", "b".repeat(40));
            case "empty" -> response.put("merge_commit_sha", "");
            case "string-null" -> response.put("merge_commit_sha", "null");
            default -> throw new AssertionError(invalid);
        }
        SERVER.reply("PUT", canonicalEndpoint + "/merge_requests/7/merge", 200, response.toString());

        assertThat(taskService.executeNextTask(run.getId().value())).isTrue();

        assertUnconfirmedMrMerge(run, false);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"opened", "closed"})
    void mrMergeRejectsAnUnmergedRemoteState(String state) {
        var run = prepareMrMergeRun(false);
        var response = mrMergeResponse(run).put("state", state).put("merge_when_pipeline_succeeds", true);
        SERVER.reply("PUT", canonicalEndpoint + "/merge_requests/7/merge", 200, response.toString());

        assertThat(taskService.executeNextTask(run.getId().value())).isTrue();

        assertUnconfirmedMrMerge(run, false);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"source_branch", "target_branch"})
    void mrMergeBindsTheProviderResultToTheOriginalCheckpoint(String field) {
        var run = prepareMrMergeRun(false);
        var response = mrMergeResponse(run);
        response.put(field, response.path(field).asText() + "-other");
        SERVER.reply("PUT", canonicalEndpoint + "/merge_requests/7/merge", 200, response.toString());

        assertThat(taskService.executeNextTask(run.getId().value())).isTrue();

        // The provider confirms a remote merge; the original business checkpoint still rejects its branches.
        assertUnconfirmedMrMerge(run, true);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void mrMergePersistsTypedCanonicalProofAndNullableResult(boolean reused, boolean noMergeCommit) throws Exception {
        var run = prepareMrMergeRun(reused);
        String runId = run.getId().value();
        String observedSha = sha.toUpperCase(java.util.Locale.ROOT);
        String mergeSha = "b".repeat(40);
        var response = mrMergeResponse(run).put("sha", observedSha).put("detailed_merge_status", "not_approved")
                .put("id", 7001).put("description", "private-merge-response").put("squash_commit_sha", "c".repeat(40));
        response.putObject("head_pipeline").put("status", "failed");
        response.putObject("commit").put("id", "d".repeat(40));
        response.putObject("object").put("sha", "e".repeat(40));
        if (noMergeCommit) response.putNull("merge_commit_sha");
        SERVER.reply("PUT", canonicalEndpoint + "/merge_requests/7/merge", 200, response.toString());

        assertThat(taskService.executeNextTask(runId)).isTrue();

        var completed = taskRuns.findById(runId).orElseThrow();
        var item = completed.getItems().getFirst();
        assertThat(taskRuns.findTasks(runId).stream().map(RunPort.TaskView::status))
                .containsExactly("COMPLETED", "COMPLETED", "COMPLETED", "COMPLETED");
        assertThat(completed.getExecutionStatus().name()).isEqualTo("DONE");
        assertThat(item.getSteps()).hasSize(4);
        assertThat(item.getSteps().get(3).result()).isEqualTo(RunItemResult.MERGED);
        assertThat(item.getMetadata()).containsAllEntriesOf(run.getItems().getFirst().getMetadata())
                .containsEntry("git.mrDisposition", reused ? "MR_REUSED" : "CREATED")
                .containsEntry("git.sourceSha", sha).doesNotContainKeys("git.blockReason", "git.requestId");
        if (noMergeCommit) assertThat(item.getMetadata()).doesNotContainKey("git.mergeSha");
        else assertThat(item.getMetadata()).containsEntry("git.mergeSha", mergeSha);
        var operations = journal.listByRun(runId, 0, 20);
        assertThat(operations).hasSize(reused ? 1 : 2)
                .allMatch(operation -> operation.state().equals("APPLIED") && !operation.blocked());
        var operation = operations.stream().filter(op -> op.action().equals("MERGE_MR")).findFirst().orElseThrow();
        var events = journal.find(operation.id()).orElseThrow().events();
        assertThat(events).hasSize(3);
        String attempt = jdbc.queryForObject("select attempt_id from git_operation where id=?", String.class, operation.id());
        assertThat(events.stream().map(GitOperationJournalPort.Event::attemptId)).containsOnly(attempt);
        var applied = events.stream().filter(event -> event.type().equals("APPLIED")).toList();
        assertThat(applied).hasSize(1);
        assertThat(applied.getFirst().sequence()).isEqualTo(2);
        var proof = new com.fasterxml.jackson.databind.ObjectMapper().readTree(applied.getFirst().evidenceJson());
        var fields = new java.util.HashSet<String>();
        proof.fieldNames().forEachRemaining(fields::add);
        org.assertj.core.api.SoftAssertions.assertSoftly(soft -> {
            soft.assertThat(fields).containsExactlyInAnyOrder("httpStatus", "project_id", "source_project_id",
                    "target_project_id", "iid", "source_branch", "target_branch", "sha", "state", "web_url", "merge_commit_sha");
            soft.assertThat(proof.path("httpStatus").isIntegralNumber()).isTrue();
            soft.assertThat(proof.path("httpStatus").asInt()).isEqualTo(200);
            for (String field : List.of("project_id", "source_project_id", "target_project_id", "iid")) {
                soft.assertThat(proof.path(field).isIntegralNumber()).as(field + " must remain a JSON integer").isTrue();
                soft.assertThat(proof.path(field).asText()).isEqualTo(response.path(field).asText());
            }
            for (String field : List.of("source_branch", "target_branch", "sha", "state", "web_url")) {
                soft.assertThat(proof.path(field).isTextual()).as(field + " must remain JSON text").isTrue();
                soft.assertThat(proof.path(field).asText()).isEqualTo(response.path(field).asText());
            }
            soft.assertThat(proof.has("merge_commit_sha")).as("explicit null is different from a missing field").isTrue();
            if (noMergeCommit) soft.assertThat(proof.path("merge_commit_sha").isNull()).isTrue();
            else {
                soft.assertThat(proof.path("merge_commit_sha").isTextual()).isTrue();
                soft.assertThat(proof.path("merge_commit_sha").asText()).isEqualTo(mergeSha);
            }
        });
        assertThat(applied.getFirst().evidenceJson()).doesNotContain("private-merge-response", "fixture-token");
        SERVER.verify("POST", canonicalEndpoint + "/merge_requests", reused ? 0 : 1);
        SERVER.verify("PUT", canonicalEndpoint + "/merge_requests/7/merge", 1);
        SERVER.verify("PUT", canonicalEndpoint + "/merge_requests/7", 0);
        assertThat(SERVER.body("PUT", canonicalEndpoint + "/merge_requests/7/merge").path("sha").asText()).isEqualTo(sha);
        assertThat(SERVER.writeCount()).isEqualTo(reused ? 1 : 2);
        assertNoNamespaceWrites();
    }

    // B14 expectations are product facts frozen before the recovery implementation, not derived from failedWrite.
    private enum B14RejectedMerge {
        PERMISSION(401, "Unauthorized", "PERMISSION_DENIED", "PERMISSION_DENIED",
                RunItemResult.MERGE_BLOCKED, "FAILED", "FAILED"),
        PROTECTED(403, "You cannot merge into this protected branch", "PROTECTED_BRANCH", "PROTECTED_BRANCH",
                RunItemResult.MERGE_BLOCKED, "FAILED", "FAILED"),
        APPROVAL(405, "Required approvals are missing", "APPROVAL_REQUIRED", "APPROVAL_REQUIRED",
                RunItemResult.MERGE_BLOCKED, "FAILED", "FAILED"),
        PIPELINE_PENDING(405, "Pipeline is still running", "PIPELINE_PENDING", "PIPELINE_PENDING",
                RunItemResult.MERGE_BLOCKED, "FAILED", "FAILED"),
        PIPELINE_FAILED(405, "Pipeline failed", "PIPELINE_FAILED", "PIPELINE_FAILED",
                RunItemResult.MERGE_BLOCKED, "FAILED", "FAILED"),
        CONFLICT(405, "Merge conflict", "CONFLICT", "CONFLICT",
                RunItemResult.MERGE_BLOCKED, "FAILED", "FAILED"),
        SHA_MISMATCH(409, "SHA does not match HEAD of source branch", "HTTP_409", "UNKNOWN",
                RunItemResult.MERGE_BLOCKED, "FAILED", "FAILED"),
        NO_COMMITS(422, "No commits between source and target", "NO_COMMITS", "UNKNOWN",
                RunItemResult.UNKNOWN, "UNKNOWN", "NEEDS_REVIEW");

        final int status;
        final String message;
        final String reason;
        final String blockReason;
        final RunItemResult result;
        final String taskStatus;
        final String runStatus;

        B14RejectedMerge(int status, String message, String reason, String blockReason,
                         RunItemResult result, String taskStatus, String runStatus) {
            this.status = status;
            this.message = message;
            this.reason = reason;
            this.blockReason = blockReason;
            this.result = result;
            this.taskStatus = taskStatus;
            this.runStatus = runStatus;
        }
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "normalRejectedMerge={0}")
    @org.junit.jupiter.params.provider.EnumSource(B14RejectedMerge.class)
    void b14NormalMergeRejectionsFollowIndependentBusinessOutcomes(B14RejectedMerge expected) throws Exception {
        var before = prepareMrMergeRun(false);
        b14ReplyRejectedMerge(expected);

        assertThat(taskService.executeNextTask(before.getId().value())).isTrue();

        b14AssertBusinessResult(before, expected);
        var operation = b14AssertOriginalRejection(before.getId().value(), expected);
        var events = journal.find(operation.id()).orElseThrow().events();
        int beforeRepeatedDrive = SERVER.requests.size();
        assertThat(taskService.executeNextTask(before.getId().value())).isFalse();
        assertThat(SERVER.requests).hasSize(beforeRepeatedDrive);
        assertThat(journal.find(operation.id()).orElseThrow().events()).isEqualTo(events);
        SERVER.verify("POST", canonicalEndpoint + "/merge_requests", 1);
        SERVER.verify("PUT", canonicalEndpoint + "/merge_requests/7/merge", 1);
        assertThat(SERVER.body("PUT", canonicalEndpoint + "/merge_requests/7/merge").path("sha").textValue()).isEqualTo(sha);
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "persistedRejectedMerge={0}")
    @org.junit.jupiter.params.provider.EnumSource(B14RejectedMerge.class)
    void b14PersistedMergeRejectionsRestoreOriginalResultsWithoutHttp(B14RejectedMerge expected) throws Exception {
        var before = prepareMrMergeRun(false);
        var claim = b14SendRejectedMergeWithoutTaskResult(before, expected);
        String runId = claim.runId();
        var operation = b14AssertOriginalRejection(runId, expected);
        var originalOperation = jdbc.queryForMap("select * from git_operation where id=?", operation.id());
        var originalEvents = journal.find(operation.id()).orElseThrow().events();
        var originalGuard = jdbc.queryForMap("select * from repo_execution_guard where remote_key=?", operation.remoteKey());
        assertThat(originalGuard).containsEntry("operation_id", operation.id()).containsEntry("reason", null);
        assertThat(taskRuns.findTasks(runId).stream().map(RunPort.TaskView::status))
                .containsExactly("COMPLETED", "COMPLETED", "COMPLETED", "RUNNING");
        assertThat(jdbc.queryForObject("select count(*) from run_step where task_id=?", Integer.class,
                claim.fence().taskId())).isZero();
        expireTaskClaim(claim);
        var originalTask = jdbc.queryForMap("select * from run_task where id=?", claim.fence().taskId());
        var originalOrder = jdbc.queryForMap("select planned_order,executed_order from run_item where id=?", claim.itemId());
        var originalSteps = jdbc.queryForList("select * from run_step where run_item_id=? order by action_type", claim.itemId());
        // Historical response must suffice even after the remote MR disappears.
        SERVER.reply("GET", canonicalEndpoint + "/merge_requests/7", 404, "{}");
        int beforeProjection = SERVER.requests.size();

        assertThat(taskService.executeNextTask(runId)).as("expired original REJECTED response must finish its task").isTrue();

        assertThat(SERVER.requests).as("projection must not issue any HTTP request").hasSize(beforeProjection);
        b14AssertBusinessResult(before, expected);
        var projectedTask = jdbc.queryForMap("select * from run_task where id=?", claim.fence().taskId());
        for (String field : List.of("id", "run_id", "target_id", "repo_id", "task_type", "task_order",
                "claim_token", "started_at", "claim_expires_at")) {
            assertThat(projectedTask.get(field)).as("original task " + field).isEqualTo(originalTask.get(field));
        }
        assertThat(projectedTask.get("finished_at")).isNotNull();
        assertThat(jdbc.queryForMap("select planned_order,executed_order from run_item where id=?", claim.itemId()))
                .isEqualTo(originalOrder);
        assertThat(jdbc.queryForList("select * from run_step where run_item_id=? and task_id<>? order by action_type",
                claim.itemId(), claim.fence().taskId())).isEqualTo(originalSteps);
        assertThat(jdbc.queryForMap("select * from git_operation where id=?", operation.id())).isEqualTo(originalOperation);
        assertThat(journal.find(operation.id()).orElseThrow().events()).isEqualTo(originalEvents);
        assertThat(jdbc.queryForMap("select * from repo_execution_guard where remote_key=?", operation.remoteKey()))
                .containsEntry("generation", originalGuard.get("generation"))
                .containsEntry("session_id", null).containsEntry("operation_id", null).containsEntry("reason", null);
        assertThat(taskService.executeNextTask(runId)).isFalse();
        assertThat(SERVER.requests).hasSize(beforeProjection);
        SERVER.verify("POST", canonicalEndpoint + "/merge_requests", 1);
        SERVER.verify("PUT", canonicalEndpoint + "/merge_requests/7/merge", 1);
        assertThat(SERVER.writeCount()).isEqualTo(2);
        System.out.printf("HTTP_REJECTED_MERGE_PROJECTION case=%s run=%s task=%s operation=%s attempt=%s "
                        + "result=%s projectionHttp=0 mergePut=1 preservedToken=true preservedEvents=3%n",
                expected, runId, claim.fence().taskId(), operation.id(), originalEvents.getFirst().attemptId(), expected.result);
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "rejectedMergeRetrySourceChanged={0}")
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void b14RejectedMergeRetryKeepsOriginalCheckpointAndRejectsChangedSource(boolean sourceChanged) throws Exception {
        var before = prepareMrMergeRun(false);
        var claim = b14SendRejectedMergeWithoutTaskResult(before, B14RejectedMerge.SHA_MISMATCH);
        var original = b14AssertOriginalRejection(claim.runId(), B14RejectedMerge.SHA_MISMATCH);
        var originalOperation = jdbc.queryForMap("select * from git_operation where id=?", original.id());
        var originalEvents = journal.find(original.id()).orElseThrow().events();
        expireTaskClaim(claim);
        int beforeProjection = SERVER.requests.size();
        assertThat(taskService.executeNextTask(claim.runId())).isTrue();
        assertThat(SERVER.requests).hasSize(beforeProjection);
        b14AssertBusinessResult(before, B14RejectedMerge.SHA_MISMATCH);

        String changedSha = "f".repeat(40);
        // The original IID detail supplies the SHA precondition for this retry.
        SERVER.reply("GET", canonicalEndpoint + "/merge_requests/7", 200,
                mrCreationResponse(before, "mergeable").put("sha", sourceChanged ? changedSha : sha).toString());
        SERVER.reply("PUT", canonicalEndpoint + "/merge_requests/7/merge", 200, mrMergeResponse(before).toString());
        var failed = taskRuns.findById(claim.runId()).orElseThrow();
        var admission = taskPlans.admitRetry(failed, List.of(claim.itemId()), failed.getOperator(), UUID.randomUUID().toString());
        var accepted = taskRuns.findById(admission.runId()).orElseThrow();
        var inherited = accepted.getItems().getFirst().getMetadata();
        for (String key : List.of("git.projectId", "git.mrIid", "git.mrUrl", "git.sourceSha",
                "git.sourceBranch", "git.targetBranch", "git.mrDisposition")) {
            assertThat(inherited).containsEntry(key, claim.metadata().get(key));
        }

        assertThat(taskService.executeNextTask(admission.runId())).isTrue();

        var retried = taskRuns.findById(admission.runId()).orElseThrow();
        assertThat(retried.getExecutionStatus().name()).isEqualTo(sourceChanged ? "FAILED" : "DONE");
        assertThat(retried.getItems().getFirst().getFinalResult())
                .isEqualTo(sourceChanged ? RunItemResult.FAILED : RunItemResult.MERGED);
        assertThat(retried.getItems().getFirst().getMetadata())
                .containsEntry("git.projectId", String.valueOf(project)).containsEntry("git.mrIid", "7")
                .containsEntry("git.sourceSha", sha);
        assertThat(journal.listByRun(admission.runId(), 0, 20)).hasSize(sourceChanged ? 0 : 1);
        assertThat(jdbc.queryForMap("select * from git_operation where id=?", original.id())).isEqualTo(originalOperation);
        assertThat(journal.find(original.id()).orElseThrow().events()).isEqualTo(originalEvents);
        SERVER.verify("POST", canonicalEndpoint + "/merge_requests", 1);
        SERVER.verify("PUT", canonicalEndpoint + "/merge_requests/7/merge", sourceChanged ? 1 : 2);
        assertThat(SERVER.requests.stream().filter(request -> request.method().equals("PUT")
                && request.path().equals(canonicalEndpoint + "/merge_requests/7/merge")))
                .allSatisfy(request -> assertThat(request.body()).contains(sha).doesNotContain(changedSha));
        assertThat(taskService.executeNextTask(admission.runId())).isFalse();
    }

    private void b14ReplyRejectedMerge(B14RejectedMerge expected) {
        SERVER.reply("PUT", canonicalEndpoint + "/merge_requests/7/merge", expected.status,
                new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode()
                        .put("message", expected.message).toString());
    }

    private RunPort.TaskClaim b14SendRejectedMergeWithoutTaskResult(io.releasehub.domain.run.Run before,
                                                                   B14RejectedMerge expected) {
        b14ReplyRejectedMerge(expected);
        var claim = taskRuns.claimNextTask(before.getId().value()).orElseThrow();
        assertThat(claim.actionType()).isEqualTo(ActionType.TRY_MERGE);
        String source = claim.metadata().get("plan.featureBranch");
        String target = claim.metadata().get("plan.releaseBranch");
        try (var scope = GitOperationContext.withTask(claim)) {
            // Invoke the real HTTP workflow; deliberately stop before the separate task result transaction.
            factory.getAdapter(GitProvider.GITLAB).tryMergeRequest(repo.getCloneUrl(), "fixture-token",
                    checkpoint(claim), "Merge " + source + " into " + target);
        }
        assertTaskStatus(claim, "RUNNING");
        return claim;
    }

    private GitOperationJournalPort.Operation b14AssertOriginalRejection(String runId, B14RejectedMerge expected) throws Exception {
        var operations = journal.listByRun(runId, 0, 20);
        assertThat(operations).hasSize(2);
        var operation = operations.stream().filter(value -> value.action().equals("MERGE_MR")).findFirst().orElseThrow();
        assertThat(operation.state()).isEqualTo("REJECTED");
        assertThat(operation.reason()).isEqualTo(expected.reason);
        var events = journal.find(operation.id()).orElseThrow().events();
        assertThat(events).extracting(GitOperationJournalPort.Event::type)
                .containsExactly("INTENT_RECORDED", "SEND_STARTED", "REJECTED");
        assertThat(events).extracting(GitOperationJournalPort.Event::sequence).containsExactly(0, 1, 2);
        assertThat(events).extracting(GitOperationJournalPort.Event::attemptId).containsOnly(events.getFirst().attemptId());
        assertThat(events.getLast().reason()).isEqualTo(expected.reason);
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        assertThat(json.readTree(events.getLast().evidenceJson()))
                .isEqualTo(json.createObjectNode().put("httpStatus", expected.status).put("rejectionReason", expected.reason));
        return operation;
    }

    private void b14AssertBusinessResult(io.releasehub.domain.run.Run before, B14RejectedMerge expected) {
        String runId = before.getId().value();
        var run = taskRuns.findById(runId).orElseThrow();
        var item = run.getItems().getFirst();
        assertThat(taskRuns.findTasks(runId).stream().map(RunPort.TaskView::status))
                .containsExactly("COMPLETED", "COMPLETED", "COMPLETED", expected.taskStatus);
        assertThat(run.getExecutionStatus().name()).isEqualTo(expected.runStatus);
        if (expected.result == RunItemResult.UNKNOWN) assertThat(run.getFinishedAt()).isNull();
        else assertThat(run.getFinishedAt()).isNotNull();
        assertThat(item.getFinalResult()).isEqualTo(expected.result);
        assertThat(item.getSteps()).hasSize(4);
        assertThat(item.getSteps().getLast().actionType()).isEqualTo(ActionType.TRY_MERGE);
        assertThat(item.getSteps().getLast().result()).isEqualTo(expected.result);
        assertThat(item.getSteps().getLast().message()).isNotBlank();
        var metadata = new LinkedHashMap<>(before.getItems().getFirst().getMetadata());
        metadata.put("git.blockReason", expected.blockReason);
        metadata.remove("git.requestId");
        assertThat(item.getMetadata()).isEqualTo(metadata);
        assertThat(jdbc.queryForObject("select last_merge_at from window_iteration where window_id=? and iteration_key=?",
                java.sql.Timestamp.class, metadata.get("plan.windowId"), item.getIterationKey().value())).isNull();
        var operation = journal.listByRun(runId, 0, 20).stream()
                .filter(value -> value.action().equals("MERGE_MR")).findFirst().orElseThrow();
        assertThat(jdbc.queryForMap("select * from repo_execution_guard where remote_key=?", operation.remoteKey()))
                .containsEntry("session_id", null).containsEntry("operation_id", null).containsEntry("reason", null);
        if (expected.result == RunItemResult.UNKNOWN) b14AssertUnknownMergeAdmissionGates(run);
        assertNoNamespaceWrites();
    }

    private void b14AssertUnknownMergeAdmissionGates(io.releasehub.domain.run.Run unresolved) {
        var item = unresolved.getItems().getFirst();
        int beforeRuns = jdbc.queryForObject("select count(*) from run", Integer.class);
        int beforeOperations = jdbc.queryForObject("select count(*) from git_operation", Integer.class);
        int beforeRequests = SERVER.requests.size();
        assertThatThrownBy(() -> taskPlans.admitRetry(unresolved, List.of(item.getId().value()),
                unresolved.getOperator(), UUID.randomUUID().toString()))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getErrorCode())
                        .isEqualTo(io.releasehub.common.exception.ErrorCode.RUN_PLAN_NOT_EXECUTABLE));
        assertThatThrownBy(() -> taskPlans.admit(item.getMetadata().get("plan.windowId"),
                List.of(item.getRepo().value()), List.of(item.getIterationKey().value()), true,
                unresolved.getOperator(), UUID.randomUUID().toString()))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getErrorCode())
                        .isEqualTo(io.releasehub.common.exception.ErrorCode.RUN_PLAN_NOT_EXECUTABLE));
        assertThat(jdbc.queryForObject("select count(*) from run", Integer.class)).isEqualTo(beforeRuns);
        assertThat(jdbc.queryForObject("select count(*) from git_operation", Integer.class)).isEqualTo(beforeOperations);
        assertThat(SERVER.requests).hasSize(beforeRequests);
    }

    private io.releasehub.domain.run.Run prepareMrMergeRun(boolean reused) {
        var run = prepareMrCreationRun();
        String opened = mrCreationResponse(run, "mergeable").toString();
        if (reused) SERVER.reply("GET", canonicalEndpoint + "/merge_requests", 200, "[" + opened + "]");
        else SERVER.reply("POST", canonicalEndpoint + "/merge_requests", 201, opened);
        SERVER.reply("GET", canonicalEndpoint + "/merge_requests/7", 200, opened);
        assertThat(taskService.executeNextTask(run.getId().value())).isTrue();
        assertThat(taskRuns.findTasks(run.getId().value()).stream().map(RunPort.TaskView::status))
                .containsExactly("COMPLETED", "COMPLETED", "COMPLETED", "PENDING");
        return taskRuns.findById(run.getId().value()).orElseThrow();
    }

    private com.fasterxml.jackson.databind.node.ObjectNode mrMergeResponse(io.releasehub.domain.run.Run run) {
        return mrCreationResponse(run, "mergeable").put("state", "merged").put("merge_commit_sha", "b".repeat(40));
    }

    private void assertUnconfirmedMrMerge(io.releasehub.domain.run.Run before, boolean providerConfirmed) {
        String runId = before.getId().value();
        var tasks = taskRuns.findTasks(runId);
        var run = taskRuns.findById(runId).orElseThrow();
        var item = run.getItems().getFirst();
        var operations = journal.listByRun(runId, 0, 20);
        assertThat(tasks).hasSize(4);
        assertThat(item.getSteps()).hasSize(4);
        assertThat(operations).hasSize(2);
        var operation = operations.stream().filter(op -> op.action().equals("MERGE_MR")).findFirst().orElseThrow();
        org.assertj.core.api.SoftAssertions.assertSoftly(soft -> {
            soft.assertThat(tasks.stream().map(RunPort.TaskView::status))
                    .containsExactly("COMPLETED", "COMPLETED", "COMPLETED", "UNKNOWN");
            soft.assertThat(item.getSteps().get(3).result()).isEqualTo(RunItemResult.UNKNOWN);
            soft.assertThat(run.getExecutionStatus().name()).isEqualTo("NEEDS_REVIEW");
            soft.assertThat(item.getMetadata()).containsAllEntriesOf(before.getItems().getFirst().getMetadata());
            soft.assertThat(operation.state()).isEqualTo(providerConfirmed ? "NEEDS_REVIEW" : "UNKNOWN");
            soft.assertThat(operation.blocked()).as("an unconfirmed business result keeps the repository guard").isTrue();
        });
        var events = journal.find(operation.id()).orElseThrow().events();
        if (providerConfirmed) {
            assertThat(events).anyMatch(event -> event.type().equals("APPLIED"))
                    .anyMatch(event -> event.type().equals("LOCAL_RESULT_UNCONFIRMED"));
        } else {
            assertThat(events).noneMatch(event -> event.type().equals("APPLIED"))
                    .anyMatch(event -> event.type().equals("UNKNOWN"));
        }
        SERVER.verify("POST", canonicalEndpoint + "/merge_requests", 1);
        SERVER.verify("PUT", canonicalEndpoint + "/merge_requests/7/merge", 1);
        SERVER.verify("PUT", canonicalEndpoint + "/merge_requests/7", 0);
        assertThat(SERVER.writeCount()).isEqualTo(2);
        assertNoNamespaceWrites();
    }

    private io.releasehub.domain.run.Run prepareMrCreationRun() {
        var run = admittedBranchRun();
        var plan = run.getItems().getFirst().getMetadata();
        for (String key : List.of("plan.featureBranch", "plan.releaseBranch")) {
            String branch = plan.get(key);
            SERVER.reply("GET", canonicalEndpoint + "/repository/branches/"
                    + URLEncoder.encode(branch, StandardCharsets.UTF_8), 200,
                    new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(
                            Map.of("name", branch, "commit", Map.of("id", sha))).toString());
            assertThat(taskService.executeNextTask(run.getId().value())).isTrue();
        }
        assertThat(taskRuns.findTasks(run.getId().value()).stream().map(RunPort.TaskView::status))
                .containsExactly("COMPLETED", "COMPLETED", "PENDING", "PENDING");
        SERVER.reply("GET", canonicalEndpoint + "/merge_requests", 200, "[]");
        SERVER.reply("GET", canonicalEndpoint + "/repository/commits/" + sha + "/refs", 200, "[]");
        return taskRuns.findById(run.getId().value()).orElseThrow();
    }

    private com.fasterxml.jackson.databind.node.ObjectNode mrCreationResponse(io.releasehub.domain.run.Run run, String readiness) {
        var plan = run.getItems().getFirst().getMetadata();
        return new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode()
                .put("project_id", project).put("source_project_id", project).put("target_project_id", project).put("iid", 7)
                .put("source_branch", plan.get("plan.featureBranch")).put("target_branch", plan.get("plan.releaseBranch"))
                .put("sha", sha).put("state", "opened").put("web_url", SERVER.baseUrl() + "/" + path + "/-/merge_requests/7")
                .put("detailed_merge_status", readiness);
    }

    private void assertUnconfirmedMrCreation(String runId) {
        var tasks = taskRuns.findTasks(runId);
        var run = taskRuns.findById(runId).orElseThrow();
        var item = run.getItems().getFirst();
        var operations = journal.listByRun(runId, 0, 20);
        assertThat(tasks).hasSize(4);
        assertThat(item.getSteps()).hasSize(3);
        assertThat(operations).hasSize(1);
        var operation = operations.getFirst();
        org.assertj.core.api.SoftAssertions.assertSoftly(soft -> {
            soft.assertThat(tasks.get(2).status()).as("creation must not complete the MR task").isEqualTo("UNKNOWN");
            soft.assertThat(item.getSteps().get(2).result()).isEqualTo(RunItemResult.UNKNOWN);
            soft.assertThat(run.getExecutionStatus().name()).isEqualTo("NEEDS_REVIEW");
            soft.assertThat(operation.state()).isIn("UNKNOWN", "NEEDS_REVIEW");
            soft.assertThat(operation.blocked()).as("unconfirmed creation keeps the repository guard").isTrue();
            soft.assertThat(tasks.get(3).status()).isEqualTo("SKIPPED");
        });
        SERVER.verify("POST", canonicalEndpoint + "/merge_requests", 1);
        SERVER.verify("PUT", canonicalEndpoint + "/merge_requests/7/merge", 0);
        assertThat(SERVER.writeCount()).isEqualTo(1);
        assertNoNamespaceWrites();
    }

    private void assertConfirmedMrCreation(String runId) {
        var tasks = taskRuns.findTasks(runId);
        var run = taskRuns.findById(runId).orElseThrow();
        var item = run.getItems().getFirst();
        assertThat(tasks.stream().map(RunPort.TaskView::status))
                .containsExactly("COMPLETED", "COMPLETED", "COMPLETED", "PENDING");
        assertThat(item.getSteps()).hasSize(3);
        assertThat(item.getSteps().get(2).result()).isEqualTo(RunItemResult.CREATED);
        assertThat(item.getMetadata()).containsEntry("git.projectId", String.valueOf(project))
                .containsEntry("git.mrIid", "7").containsEntry("git.sourceSha", sha)
                .containsEntry("git.sourceBranch", item.getMetadata().get("plan.featureBranch"))
                .containsEntry("git.targetBranch", item.getMetadata().get("plan.releaseBranch"))
                .containsEntry("git.mrUrl", SERVER.baseUrl() + "/" + path + "/-/merge_requests/7")
                .containsEntry("git.mrDisposition", "CREATED");
        var operations = journal.listByRun(runId, 0, 20);
        assertThat(operations).hasSize(1);
        assertThat(operations.getFirst().state()).isEqualTo("APPLIED");
        assertThat(operations.getFirst().blocked()).isFalse();
        SERVER.verify("POST", canonicalEndpoint + "/merge_requests", 1);
        SERVER.verify("PUT", canonicalEndpoint + "/merge_requests/7/merge", 0);
        assertThat(SERVER.writeCount()).isEqualTo(1);
        assertNoNamespaceWrites();
    }

    @Test void shouldProtectEveryManagedHttpAdapter() {
        for(var adapter:context.getBeansOfType(GitBranchPort.class).values()) {
            Object target=AopTestUtils.getUltimateTargetObject(adapter);
            if(target instanceof io.releasehub.infrastructure.git.GitLabGitBranchAdapter||target instanceof io.releasehub.infrastructure.git.GitHubGitBranchAdapter) {
                var http=(RestTemplate)ReflectionTestUtils.getField(target,"restTemplate");
                assertThat(http).isNotNull();assertThat(http.getInterceptors()).anyMatch(i->i instanceof io.releasehub.infrastructure.git.GitWriteHttpInterceptor);
            }
        }
        Object target=AopTestUtils.getUltimateTargetObject(files);
        assertThat(((RestTemplate)ReflectionTestUtils.getField(target,"restTemplate")).getInterceptors()).anyMatch(i->i instanceof io.releasehub.infrastructure.git.GitWriteHttpInterceptor);
    }
    @Test void shouldRejectUnverifiedProviderAndOriginBeforeNetworkWrite() {
        assertThatThrownBy(()->factory.getAdapter(GitProvider.GITHUB).createBranch("https://github.com/acme/repo.git","fixture","feature","main"))
                .isInstanceOf(BusinessException.class);
        String alias=repo.getCloneUrl().replace("127.0.0.1","localhost");
        assertThatThrownBy(()->factory.getAdapter(GitProvider.GITLAB).createBranch(alias,"fixture-token","feature","main"))
                .isInstanceOf(BusinessException.class);
        assertThat(SERVER.requests).isEmpty();
    }
    @Test void shouldRefuseStartupWithoutProtectionWhenGuardExists() {
        SERVER.reply("POST",canonicalEndpoint+"/repository/branches",200,"{");
        assertThatThrownBy(this::write).isInstanceOf(RuntimeException.class);
        var disabled=new io.releasehub.infrastructure.git.GitSafetyProperties();disabled.setEnabled(false);
        var verifier=new io.releasehub.infrastructure.git.GitSafetyStartupVerifier(disabled,journal);
        assertThatThrownBy(verifier::afterSingletonsInstantiated).isInstanceOf(IllegalStateException.class);
    }
    @Test void shouldPinConfirmedBodyWriteToVerifiedProjectAndReleaseGuard() {
        branchSuccess();assertThat(write()).isTrue();
        var ops=journal.list(repo.getId().value(),0,20);assertThat(ops).hasSize(1);
        assertThat(ops.get(0).state()).isEqualTo("APPLIED");assertThat(ops.get(0).blocked()).isFalse();
        SERVER.verify("POST",canonicalEndpoint+"/repository/branches",1);
        assertThat(SERVER.body("POST",canonicalEndpoint+"/repository/branches").path("ref").asText()).isEqualTo(sha);
        assertThat(SERVER.body("POST",canonicalEndpoint+"/repository/branches").path("branch").asText()).isEqualTo("release/evidence");
        SERVER.verify("GET",endpoint,1);
        assertNoNamespaceWrites();
        assertThat(SERVER.writeCount()).isEqualTo(1);
    }
    @Test void shouldKeepUnknownAndBlockAliasBeforeSecondWrite() {
        SERVER.reply("POST",canonicalEndpoint+"/repository/branches",200,"{");
        assertThatThrownBy(this::write).isInstanceOf(RuntimeException.class);
        var first=journal.list(repo.getId().value(),0,20).get(0);assertThat(first.state()).isEqualTo("UNKNOWN");assertThat(first.blocked()).isTrue();
        String aliasPath="alias/"+UUID.randomUUID();var alias=createRepo(aliasPath);
        String aliasEndpoint="/api/v4/projects/"+URLEncoder.encode(aliasPath,StandardCharsets.UTF_8);identity(aliasEndpoint);
        assertThatThrownBy(()->factory.getAdapter(GitProvider.GITLAB).createBranch(alias.getCloneUrl(),"fixture-token","release/evidence","main")).isInstanceOf(BusinessException.class);
        SERVER.verify("POST",canonicalEndpoint+"/repository/branches",1);
        SERVER.verify("POST",aliasEndpoint+"/repository/branches",0);
        assertNoNamespaceWrites();
        assertThat(SERVER.writeCount()).isEqualTo(1);
    }
    @Test void shouldKeepGuardAfterRealTransactionRollback() {
        branchSuccess();
        assertThatThrownBy(()->new TransactionTemplate(transactions).executeWithoutResult(tx->{assertThat(write()).isTrue();throw new IllegalStateException("模拟业务提交失败");})).isInstanceOf(IllegalStateException.class);
        var op=journal.list(repo.getId().value(),0,20).get(0);assertThat(op.state()).isEqualTo("NEEDS_REVIEW");assertThat(op.blocked()).isTrue();
        assertThatThrownBy(this::write).isInstanceOf(BusinessException.class);
        SERVER.verify("POST",canonicalEndpoint+"/repository/branches",1);
    }
    @Test void shouldProbeExistingMergeRequestWithoutAnyWrite() {
        sourceBranch();
        SERVER.reply("GET",canonicalEndpoint+"/merge_requests",200,"["+mergeRequest("opened")+"]");
        SERVER.reply("GET",canonicalEndpoint+"/merge_requests/7",200,mergeRequest("opened"));
        assertThat(factory.getAdapter(GitProvider.GITLAB).checkMergeability(repo.getCloneUrl(),"fixture-token","feature","main").canMerge()).isTrue();
        assertThat(journal.list(repo.getId().value(),0,20)).isEmpty();
        assertThat(SERVER.requests).isNotEmpty().allMatch(event->event.method().equals("GET"));
        SERVER.verify("POST",canonicalEndpoint+"/merge_requests",0);
        SERVER.verify("PUT",canonicalEndpoint+"/merge_requests/7",0);
    }
    @Test void shouldReconcileConfirmedBranchEvidenceWithoutAnyWrite() {
        branchSuccess();assertThat(write()).isTrue();
        var created=journal.list(repo.getId().value(),0,20).getFirst();
        SERVER.reply("GET","/api/v4/projects/"+project+"/repository/branches/release%2Fevidence",200,
                "{\"name\":\"release/evidence\",\"commit\":{\"id\":\""+sha+"\"}}");
        SERVER.resetRequests();
        var observed=service.reconcile(created.id(),"tester","reconcile-key",created.version());
        assertThat(observed.status()).isEqualTo("FOUND");
        assertThat(service.reconcile(created.id(),"tester","reconcile-key",created.version())).isEqualTo(observed);
        assertThat(SERVER.requests).isNotEmpty().allMatch(event->event.method().equals("GET"));
    }
    @Test void shouldProtectFileWritesWithRevisionAndPreserveEvidence() {
        String file=canonicalEndpoint+"/repository/files/pom.xml";
        String content="<project>new version</project>";String hash=io.releasehub.infrastructure.git.GitRemoteIdentityResolver.hash(content);
        SERVER.reply("GET",file,200,"{\"last_commit_id\":\""+sha+"\",\"content_sha256\":\""+hash+"\"}");
        SERVER.reply("PUT",file,200,"{\"file_path\":\"pom.xml\",\"branch\":\"main\"}");
        assertThat(files.updateFile(repo.getCloneUrl(),"main","pom.xml",new GitLabFilePort.FileSnapshot(content,sha),content,"update fixture")).isTrue();
        SERVER.verify("PUT",file,1);
        assertThat(SERVER.body("PUT",file).path("last_commit_id").asText()).isEqualTo(sha);
        var op=journal.list(repo.getId().value(),0,20).get(0);assertThat(op.action()).isEqualTo("UPDATE_FILE");
        assertThat(journal.find(op.id()).orElseThrow().events().get(2).evidenceJson()).contains(sha,hash).doesNotContain(content,"fixture-token");
    }
    @Test void shouldPreservePartialFileSuccessAndBlockFurtherWrites() {
        String content="<project>partial update</project>";
        String hash=io.releasehub.infrastructure.git.GitRemoteIdentityResolver.hash(content);
        for(String name:java.util.List.of("pom.xml","module.xml")) {
            SERVER.reply("GET","/api/v4/projects/"+project+"/repository/files/"+name,200,
                    "{\"last_commit_id\":\""+sha+"\",\"content_sha256\":\""+hash+"\"}");
        }
        SERVER.reply("PUT",canonicalEndpoint+"/repository/files/pom.xml",200,"{\"file_path\":\"pom.xml\"}");
        SERVER.reply("PUT",canonicalEndpoint+"/repository/files/module.xml",500,"{}");
        new TransactionTemplate(transactions).executeWithoutResult(tx->{
            assertThat(files.updateFile(repo.getCloneUrl(),"main","pom.xml",new GitLabFilePort.FileSnapshot(content,sha),content,"first")).isTrue();
            assertThat(files.updateFile(repo.getCloneUrl(),"main","module.xml",new GitLabFilePort.FileSnapshot(content,sha),content,"second")).isFalse();
        });
        var ops=journal.list(repo.getId().value(),0,20);
        assertThat(ops).extracting(GitOperationJournalPort.Operation::state).containsExactlyInAnyOrder("NEEDS_REVIEW","UNKNOWN");
        // 后续文件未知会使组合结果待复核，但第一份文件已确认的远端事件不能丢失。
        var applied=ops.stream().filter(o->o.state().equals("NEEDS_REVIEW")).findFirst().orElseThrow();
        assertThat(journal.find(applied.id()).orElseThrow().events()).anyMatch(e->e.type().equals("APPLIED"));
        assertThat(ops).allMatch(GitOperationJournalPort.Operation::blocked);
        assertThatThrownBy(this::write).isInstanceOf(BusinessException.class);
        SERVER.verify("PUT",canonicalEndpoint+"/repository/files/pom.xml",1);
        SERVER.verify("PUT",canonicalEndpoint+"/repository/files/module.xml",1);
    }
    @Test void shouldPreserveUnconfirmedMergeWithoutClosingOrResending() {
        sourceBranch();
        SERVER.reply("GET",canonicalEndpoint+"/merge_requests",200,"[]");
        SERVER.reply("GET",canonicalEndpoint+"/repository/commits/"+sha+"/refs",200,"[]");
        SERVER.reply("GET",canonicalEndpoint+"/merge_requests/7",200,mergeRequest("opened"));
        SERVER.reply("POST",canonicalEndpoint+"/merge_requests",201,mergeRequest("opened"));
        SERVER.reply("PUT",canonicalEndpoint+"/merge_requests/7/merge",500,"{}");
        var result=factory.getAdapter(GitProvider.GITLAB).mergeBranch(repo.getCloneUrl(),"fixture-token","feature","main","fixture");
        assertThat(result.status()).isEqualTo(io.releasehub.domain.run.MergeStatus.UNKNOWN);
        assertThat(journal.list(repo.getId().value(),0,20)).hasSize(2)
                .allMatch(GitOperationJournalPort.Operation::blocked);
        SERVER.verify("POST",canonicalEndpoint+"/merge_requests",1);
        SERVER.verify("PUT",canonicalEndpoint+"/merge_requests/7/merge",1);
        SERVER.verify("PUT",canonicalEndpoint+"/merge_requests/7",0);
    }

    @Test void shouldRetainGuardWhenCreatedMrHasOnlyAnIid() {
        readyForNewMerge();
        // 两条路由给同样的不完整响应，让语义反例不被旧 namespace 路由的 404 掩盖。
        String incomplete="{\"iid\":17}";
        SERVER.reply("POST",canonicalEndpoint+"/merge_requests",201,incomplete);
        SERVER.reply("POST",endpoint+"/merge_requests",201,incomplete);

        var result=merge();
        assertThat(result.status()).isEqualTo(io.releasehub.domain.run.MergeStatus.UNKNOWN);
        // 缺少完整 CREATE 证明时直接 UNKNOWN，不能先写 APPLIED 再依赖业务层降级。
        var operations=journal.list(repo.getId().value(),0,20);
        assertThat(operations).hasSize(1).allMatch(operation->operation.state().equals("UNKNOWN")&&operation.blocked());
        assertThat(journal.find(operations.getFirst().id()).orElseThrow().events())
                .noneMatch(event->event.type().equals("APPLIED"))
                .anyMatch(event->event.type().equals("UNKNOWN"));
        assertNextWriteBlocked(1);
        SERVER.verify("POST",canonicalEndpoint+"/merge_requests",1);
        SERVER.verify("PUT",canonicalEndpoint+"/merge_requests/17/merge",0);
        assertNoNamespaceWrites();
    }

    @Test void shouldRetainGuardWhenMergedResponseLacksIdentityRefsAndSha() {
        readyForNewMerge();
        SERVER.reply("POST",canonicalEndpoint+"/merge_requests",201,mergeRequest("opened"));
        SERVER.reply("POST",endpoint+"/merge_requests",201,mergeRequest("opened"));
        String incomplete="{\"iid\":7,\"state\":\"merged\"}";
        SERVER.reply("PUT",canonicalEndpoint+"/merge_requests/7/merge",200,incomplete);
        SERVER.reply("PUT",endpoint+"/merge_requests/7/merge",200,incomplete);

        var result=merge();
        assertThat(result.status()).isEqualTo(io.releasehub.domain.run.MergeStatus.UNKNOWN);
        var operations=journal.list(repo.getId().value(),0,20);
        assertThat(operations).hasSize(2).allMatch(GitOperationJournalPort.Operation::blocked);
        var creation=operations.stream().filter(operation->operation.action().equals("CREATE_MR")).findFirst().orElseThrow();
        assertThat(creation.state()).isEqualTo("NEEDS_REVIEW");
        assertThat(journal.find(creation.id()).orElseThrow().events())
                .anyMatch(event->event.type().equals("APPLIED"))
                .anyMatch(event->event.type().equals("LOCAL_RESULT_UNCONFIRMED"));
        var mergeOperation=operations.stream().filter(operation->operation.action().equals("MERGE_MR")).findFirst().orElseThrow();
        assertThat(mergeOperation.state()).isEqualTo("UNKNOWN");
        assertThat(journal.find(mergeOperation.id()).orElseThrow().events())
                .noneMatch(event->event.type().equals("APPLIED"))
                .anyMatch(event->event.type().equals("UNKNOWN"));
        assertNextWriteBlocked(2);
        SERVER.verify("POST",canonicalEndpoint+"/merge_requests",1);
        SERVER.verify("PUT",canonicalEndpoint+"/merge_requests/7/merge",1);
        assertNoNamespaceWrites();
    }

    @Test void shouldReleaseGuardAfterConfirmedMergeBlock() {
        readyForNewMerge();
        SERVER.reply("POST",canonicalEndpoint+"/merge_requests",201,mergeRequest("opened"));
        SERVER.reply("PUT",canonicalEndpoint+"/merge_requests/7/merge",422,"{\"message\":\"Approval is required\"}");

        var result=merge();
        assertThat(result.status()).isEqualTo(io.releasehub.domain.run.MergeStatus.BLOCKED);
        assertThat(result.mrEvidence().blockReason()).isEqualTo(GitBranchPort.MergeBlockReason.APPROVAL_REQUIRED);
        var operations=journal.list(repo.getId().value(),0,20);
        assertThat(operations).hasSize(2).noneMatch(GitOperationJournalPort.Operation::blocked);
        assertThat(operations).extracting(GitOperationJournalPort.Operation::state)
                .containsExactlyInAnyOrder("APPLIED","REJECTED");
        branchSuccess();
        assertThat(write()).isTrue();
        assertThat(SERVER.writeCount()).isEqualTo(3);
        SERVER.verify("PUT",canonicalEndpoint+"/merge_requests/7/merge",1);
        assertNoNamespaceWrites();
    }

    @Test void shouldNotCreateGuardForUnknownReadBeforeAnyWrite() {
        SERVER.reply("GET",canonicalEndpoint+"/repository/branches/feature",200,"{}");

        assertThat(merge().status()).isEqualTo(io.releasehub.domain.run.MergeStatus.UNKNOWN);
        assertThat(journal.list(repo.getId().value(),0,20)).isEmpty();
        assertThat(SERVER.requests).isNotEmpty().allMatch(event->event.method().equals("GET"));
        assertThat(SERVER.writeCount()).isZero();
        // 同一项目随后的已确认写仍能执行，证明只读未知没有留下执行 guard。
        branchSuccess();
        assertThat(write()).isTrue();
        assertThat(journal.list(repo.getId().value(),0,20)).hasSize(1)
                .allMatch(operation->operation.state().equals("APPLIED")&&!operation.blocked());
        assertThat(SERVER.writeCount()).isEqualTo(1);
        assertNoNamespaceWrites();
    }

    @Test void shouldPinPipelineToVerifiedProjectWithoutReencodingQuery() {
        String ref="release/evidence+gate";
        SERVER.reply("POST",canonicalEndpoint+"/pipeline",201,pipeline(ref,sha));
        SERVER.reply("POST",endpoint+"/pipeline",201,pipeline(ref,sha));

        var result=factory.getAdapter(GitProvider.GITLAB).triggerPipeline(repo.getCloneUrl(),"fixture-token",String.valueOf(project),ref,sha);
        assertThat(result.outcome()).isEqualTo(GitWriteResult.Outcome.APPLIED);
        assertThat(result.evidence().id()).isEqualTo("321");
        assertThat(result.evidence().projectId()).isEqualTo(String.valueOf(project));
        assertThat(result.evidence().sha()).isEqualTo(sha);
        assertThat(SERVER.requests).noneMatch(GitLabHttpFixture.Request::internalHeaders);
        SERVER.verify("GET",endpoint,1);
        SERVER.verify("POST",canonicalEndpoint+"/pipeline",1);
        assertThat(SERVER.query("POST",canonicalEndpoint+"/pipeline")).isEqualTo("ref=release%2Fevidence%2Bgate");
        assertNoNamespaceWrites();
        assertThat(SERVER.writeCount()).isEqualTo(1);
        var operation=journal.list(repo.getId().value(),0,20).getFirst();
        assertThat(operation.action()).isEqualTo("TRIGGER_PIPELINE");
        assertThat(operation.state()).isEqualTo("APPLIED");
        assertThat(operation.blocked()).isFalse();
        assertThat(operation.targetJson()).contains("\"ref\":\"release/evidence+gate\"");
    }

    @Test void shouldPinTagToConfirmedProjectAndCommitWithoutLeakingInternalHeaders() {
        String tag="v1.1.0";
        String response="{\"name\":\""+tag+"\",\"commit\":{\"id\":\""+sha+"\"}}";
        SERVER.reply("GET",canonicalEndpoint+"/repository/commits/"+sha,200,"{\"id\":\""+sha+"\"}");
        SERVER.reply("POST",canonicalEndpoint+"/repository/tags",201,response);
        SERVER.reply("GET",canonicalEndpoint+"/repository/tags/"+tag,200,response);
        var adapter=factory.getAdapter(GitProvider.GITLAB);
        var created=adapter.createTag(repo.getCloneUrl(),"fixture-token",String.valueOf(project),tag,sha,"fixture tag");
        assertThat(created.outcome()).isEqualTo(GitWriteResult.Outcome.APPLIED);
        assertThat(created.evidence()).isEqualTo(new GitBranchPort.TagEvidence(String.valueOf(project),tag,sha));
        var observed=adapter.readTag(repo.getCloneUrl(),"fixture-token",String.valueOf(project),tag);
        assertThat(observed.status()).isEqualTo(GitReadResult.Status.FOUND);
        assertThat(observed.evidence()).isEqualTo(created.evidence());
        SERVER.verify("POST",canonicalEndpoint+"/repository/tags",1);
        assertThat(SERVER.body("POST",canonicalEndpoint+"/repository/tags").path("ref").asText()).isEqualTo(sha);
        assertThat(SERVER.writeCount()).isEqualTo(1);
        assertNoNamespaceWrites();
        assertThat(SERVER.requests).noneMatch(GitLabHttpFixture.Request::internalHeaders);
        assertThat(journal.list(repo.getId().value(),0,20)).hasSize(1)
                .allMatch(operation->operation.state().equals("APPLIED")&&!operation.blocked());
    }

    @Test void shouldKeepPipelineWithMismatchedShaBlockedBeforeAnyFurtherWrite() {
        SERVER.reply("POST",canonicalEndpoint+"/pipeline",201,pipeline("main","d".repeat(40)));
        var result=factory.getAdapter(GitProvider.GITLAB).triggerPipeline(
                repo.getCloneUrl(),"fixture-token",String.valueOf(project),"main",sha);
        assertThat(result.outcome()).isEqualTo(GitWriteResult.Outcome.UNKNOWN);
        assertThat(journal.list(repo.getId().value(),0,20)).hasSize(1)
                .allMatch(operation->operation.state().equals("UNKNOWN")&&operation.blocked());
        branchSuccess();
        assertThatThrownBy(this::write).isInstanceOf(BusinessException.class);
        assertThat(SERVER.writeCount()).isEqualTo(1);
        SERVER.verify("POST",canonicalEndpoint+"/pipeline",1);
        assertNoNamespaceWrites();
        assertThat(SERVER.requests).noneMatch(GitLabHttpFixture.Request::internalHeaders);
    }

    @Test void shouldKeepPipelineWithoutResponseIdBlocked() {
        SERVER.reply("POST",canonicalEndpoint+"/pipeline",201,"{}");
        assertThat(factory.getAdapter(GitProvider.GITLAB).triggerPipeline(repo.getCloneUrl(),"fixture-token",String.valueOf(project),"main",sha).outcome())
                .isEqualTo(GitWriteResult.Outcome.UNKNOWN);
        var op=journal.list(repo.getId().value(),0,20).get(0);
        assertThat(op.action()).isEqualTo("TRIGGER_PIPELINE");assertThat(op.state()).isEqualTo("UNKNOWN");assertThat(op.blocked()).isTrue();
        assertThatThrownBy(this::write).isInstanceOf(BusinessException.class);
        SERVER.verify("POST",canonicalEndpoint+"/pipeline",1);
    }
    @Test void shouldRejectUnprovenDeleteAndLocalFileFallback(@TempDir Path dir) throws Exception {
        assertThatThrownBy(()->factory.getAdapter(GitProvider.GITLAB).deleteBranch(repo.getCloneUrl(),"fixture-token","main")).isInstanceOf(BusinessException.class);
        assertThat(SERVER.requests).noneMatch(r->r.method().equals("DELETE"));
        Path pom=dir.resolve("pom.xml");Files.writeString(pom,"<project><version>1.0</version></project>");
        var updater=versionUpdaters.stream().filter(u->u.supports(BuildTool.MAVEN)).findFirst().orElseThrow();
        assertThatThrownBy(()->updater.update(VersionUpdateRequest.forMaven(repo.getId(),null,dir.toString(),"2.0",pom.toString()))).isInstanceOf(BusinessException.class);
        assertThat(Files.readString(pom)).contains("1.0");
    }
    @Test void shouldPreventMappingChangeWhileGuardIsHeld() {
        SERVER.reply("POST",canonicalEndpoint+"/repository/branches",200,"{");
        assertThatThrownBy(this::write).isInstanceOf(RuntimeException.class);
        String original=repo.getCloneUrl();repo.update("changed",SERVER.baseUrl()+"/other/repo.git","main","G001",RepoType.SERVICE,GitProvider.GITLAB,"fixture-token",false,Instant.now());
        assertThatThrownBy(()->repos.save(repo)).isInstanceOf(BusinessException.class);
        assertThat(repos.findById(repo.getId()).orElseThrow().getCloneUrl()).isEqualTo(original);
    }

    @Test void shouldHoldCreatedMrUntilTaskCheckpointCommits() {
        var claim = claimedMergeTask();
        readyForNewMerge();
        SERVER.reply("POST", canonicalEndpoint + "/merge_requests", 201, mergeRequest("opened"));
        var result = ensureTaskMr(claim);
        assertThat(result.outcome()).isEqualTo(GitWriteResult.Outcome.APPLIED);
        assertThat(result.evidence().disposition()).isEqualTo(GitBranchPort.MrDisposition.CREATED);
        assertTaskStatus(claim, "RUNNING");
        assertThat(journal.list(repo.getId().value(), 0, 20)).hasSize(1)
                .allMatch(operation -> operation.state().equals("APPLIED") && operation.blocked());
        branchSuccess();
        assertThatThrownBy(this::write).isInstanceOf(BusinessException.class);
        SERVER.verify("POST", canonicalEndpoint + "/merge_requests", 1);
        assertThat(SERVER.writeCount()).isEqualTo(1);

        expireTaskClaim(claim);
        assertThat(taskRuns.claimNextTask(claim.runId())).isEmpty();
        taskRuns.completeTask(claim, taskStep(claim, RunItemResult.CREATED), checkpointMetadata(result.evidence()));
        assertTaskStatus(claim, "COMPLETED");
        assertThat(journal.list(repo.getId().value(), 0, 20)).noneMatch(GitOperationJournalPort.Operation::blocked);
        var mergeClaim = taskRuns.claimNextTask(claim.runId()).orElseThrow();
        assertThat(mergeClaim.actionType()).isEqualTo(ActionType.TRY_MERGE);
        assertThat(mergeClaim.metadata()).containsEntry("git.projectId", String.valueOf(project))
                .containsEntry("git.mrIid", "7").containsEntry("git.sourceSha", sha);
        SERVER.reply("PUT", canonicalEndpoint + "/merge_requests/7/merge", 200, mergedRequest());
        GitBranchPort.MergeResult merged;
        try (var scope = GitOperationContext.withTask(mergeClaim)) {
            merged = factory.getAdapter(GitProvider.GITLAB).tryMergeRequest(
                    repo.getCloneUrl(), "fixture-token", checkpoint(mergeClaim), "fixture merge");
        }
        assertThat(merged.status()).isEqualTo(io.releasehub.domain.run.MergeStatus.SUCCESS);
        var completed = taskRuns.completeTask(mergeClaim, taskStep(mergeClaim, RunItemResult.MERGED),
                Map.of("git.mergeSha", merged.mrEvidence().mergeSha()));
        assertThat(completed.getExecutionStatus().name()).isEqualTo("DONE");
        assertThat(taskRuns.findTasks(claim.runId())).hasSize(4).allMatch(task -> task.status().equals("COMPLETED"));
        assertThat(completed.getItems().getFirst().getSteps()).hasSize(4);
        assertThat(journal.list(repo.getId().value(), 0, 20)).hasSize(2)
                .allMatch(operation -> operation.state().equals("APPLIED") && !operation.blocked());
        SERVER.verify("POST", canonicalEndpoint + "/merge_requests", 1);
        SERVER.verify("PUT", canonicalEndpoint + "/merge_requests/7/merge", 1);
        assertNoNamespaceWrites();
    }

    @Test void shouldRollBackTaskProjectionAndGuardAcknowledgementTogether() {
        var claim = claimedMergeTask();
        readyForNewMerge();
        SERVER.reply("POST", canonicalEndpoint + "/merge_requests", 201, mergeRequest("opened"));
        var result = ensureTaskMr(claim);
        assertThat(result.outcome()).isEqualTo(GitWriteResult.Outcome.APPLIED);
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            taskRuns.completeTask(claim, taskStep(claim, RunItemResult.CREATED), checkpointMetadata(result.evidence()));
            throw new IllegalStateException("fixture result transaction rollback");
        })).isInstanceOf(IllegalStateException.class).hasMessage("fixture result transaction rollback");

        assertTaskStatus(claim, "RUNNING");
        var item = taskRuns.findById(claim.runId()).orElseThrow().getItems().getFirst();
        assertThat(item.getSteps()).hasSize(2).noneMatch(step -> step.actionType() == ActionType.ENSURE_MR);
        assertThat(item.getMetadata()).doesNotContainKey("git.mrIid");
        assertThat(taskRuns.claimNextTask(claim.runId())).isEmpty();
        assertThat(journal.list(repo.getId().value(), 0, 20)).hasSize(1)
                .allMatch(operation -> operation.state().equals("APPLIED") && operation.blocked());
        branchSuccess();
        assertThatThrownBy(this::write).isInstanceOf(BusinessException.class);
        SERVER.verify("POST", canonicalEndpoint + "/merge_requests", 1);
        assertThat(SERVER.writeCount()).isEqualTo(1);
    }

    @Test void shouldRejectSupersededTaskClaimBeforeSendingRemoteWrite() {
        var claim = claimedMergeTask();
        readyForNewMerge();
        SERVER.reply("POST", canonicalEndpoint + "/merge_requests", 201, mergeRequest("opened"));
        branchSuccess();
        try (var scope = GitOperationContext.withTask(claim)) {
            assertThatThrownBy(this::write).as("ENSURE_MR claim cannot issue CREATE_BRANCH")
                    .isInstanceOf(BusinessException.class);
        }
        try (var scope = GitOperationContext.withRun(claim.runId())) {
            assertThatThrownBy(this::write).as("durable task Run cannot omit its fence")
                    .isInstanceOf(BusinessException.class);
        }
        assertThat(SERVER.writeCount()).isZero();
        assertThat(journal.list(repo.getId().value(), 0, 20)).isEmpty();
        assertThat(jdbc.update("update run_task set claim_token = ? where id = ?",
                UUID.randomUUID().toString(), claim.fence().taskId())).isEqualTo(1);
        SERVER.resetRequests();
        assertThatThrownBy(() -> ensureTaskMr(claim)).isInstanceOf(BusinessException.class);
        SERVER.verify("POST", canonicalEndpoint + "/merge_requests", 0);
        assertThat(SERVER.writeCount()).isZero();
        assertThat(journal.list(repo.getId().value(), 0, 20)).isEmpty();
        assertTaskStatus(claim, "RUNNING");
    }

    @Test void shouldNotReplayTaskAfterSendStartedHasUnknownResult() {
        var claim = claimedMergeTask();
        readyForNewMerge();
        SERVER.reply("POST", canonicalEndpoint + "/merge_requests", 500, "{}");
        var result = ensureTaskMr(claim);
        assertThat(result.outcome()).isEqualTo(GitWriteResult.Outcome.UNKNOWN);
        var operation = journal.list(repo.getId().value(), 0, 20).getFirst();
        assertThat(operation.state()).isEqualTo("UNKNOWN");
        assertThat(operation.blocked()).isTrue();
        assertThat(journal.find(operation.id()).orElseThrow().events())
                .anyMatch(event -> event.type().equals("SEND_STARTED"));
        assertThatThrownBy(() -> ensureTaskMr(claim)).isInstanceOf(BusinessException.class);
        SERVER.verify("POST", canonicalEndpoint + "/merge_requests", 1);
        assertThat(SERVER.writeCount()).isEqualTo(1);

        var unresolved = taskRuns.completeTask(claim, taskStep(claim, RunItemResult.UNKNOWN),
                Map.of("git.blockReason", "UNKNOWN"));
        assertThat(unresolved.getExecutionStatus().name()).isEqualTo("NEEDS_REVIEW");
        assertTaskStatus(claim, "UNKNOWN");
        assertThat(taskRuns.claimNextTask(claim.runId())).isEmpty();
        assertThat(journal.find(operation.id()).orElseThrow().operation().blocked()).isTrue();
        assertNoNamespaceWrites();
    }

    @Test void shouldRejectProjectRebindingAfterDurableMrCheckpoint() {
        var claim = claimedMergeTask();
        readyForNewMerge();
        SERVER.reply("POST", canonicalEndpoint + "/merge_requests", 201, mergeRequest("opened"));
        var result = ensureTaskMr(claim);
        assertThat(result.outcome()).isEqualTo(GitWriteResult.Outcome.APPLIED);
        taskRuns.completeTask(claim, taskStep(claim, RunItemResult.CREATED), checkpointMetadata(result.evidence()));
        var mergeClaim = taskRuns.claimNextTask(claim.runId()).orElseThrow();
        assertThat(mergeClaim.actionType()).isEqualTo(ActionType.TRY_MERGE);
        SERVER.reply("GET", endpoint, 200, "{\"id\":" + (project + 1) + "}");
        try (var scope = GitOperationContext.withTask(mergeClaim)) {
            assertThatThrownBy(() -> factory.getAdapter(GitProvider.GITLAB).tryMergeRequest(
                    repo.getCloneUrl(), "fixture-token", checkpoint(mergeClaim), "fixture merge"))
                    .isInstanceOf(BusinessException.class);
        }
        assertThat(SERVER.requests).noneMatch(request -> request.method().equals("PUT"));
        assertThat(SERVER.writeCount()).isEqualTo(1);
        var unresolved = taskRuns.completeTask(mergeClaim, taskStep(mergeClaim, RunItemResult.UNKNOWN),
                Map.of("git.blockReason", "UNKNOWN"));
        assertThat(unresolved.getExecutionStatus().name()).isEqualTo("FAILED");
        assertThat(unresolved.getItems().getFirst().getMetadata()).containsEntry("git.projectId", String.valueOf(project));
        assertThat(journal.list(repo.getId().value(), 0, 20)).hasSize(1);
        assertNoNamespaceWrites();
    }

    @Test void shouldCompleteContainedSourceWithoutInventingMrOrGuard() {
        var claim = claimedMergeTask();
        readyForNewMerge();
        SERVER.reply("GET", canonicalEndpoint + "/repository/commits/" + sha + "/refs", 200,
                "[{\"type\":\"branch\",\"name\":\"main\"}]");
        var result = ensureTaskMr(claim);
        assertThat(result.outcome()).isEqualTo(GitWriteResult.Outcome.APPLIED);
        assertThat(result.evidence().disposition()).isEqualTo(GitBranchPort.MrDisposition.ALREADY_MERGED);
        assertThat(result.evidence().iid()).isNull();
        assertThat(result.evidence().url()).isNull();
        taskRuns.completeTask(claim, taskStep(claim, RunItemResult.ALREADY_MERGED), checkpointMetadata(result.evidence()));
        var mergeClaim = taskRuns.claimNextTask(claim.runId()).orElseThrow();
        GitBranchPort.MergeResult merged;
        try (var scope = GitOperationContext.withTask(mergeClaim)) {
            merged = factory.getAdapter(GitProvider.GITLAB).tryMergeRequest(
                    repo.getCloneUrl(), "fixture-token", checkpoint(mergeClaim), "fixture merge");
        }
        assertThat(merged.status()).isEqualTo(io.releasehub.domain.run.MergeStatus.SUCCESS);
        assertThat(merged.mrEvidence().disposition()).isEqualTo(GitBranchPort.MrDisposition.ALREADY_MERGED);
        var completed = taskRuns.completeTask(mergeClaim, taskStep(mergeClaim, RunItemResult.ALREADY_MERGED), Map.of());
        assertThat(completed.getExecutionStatus().name()).isEqualTo("DONE");
        assertThat(journal.list(repo.getId().value(), 0, 20)).isEmpty();
        assertThat(SERVER.writeCount()).isZero();
        assertThat(taskRuns.findTasks(claim.runId())).hasSize(4).allMatch(task -> task.status().equals("COMPLETED"));
    }

    @Test void shouldReleaseConfirmedTaskBlockOnlyAfterResultCommits() {
        var claim = claimedMergeTask();
        readyForNewMerge();
        SERVER.reply("POST", canonicalEndpoint + "/merge_requests", 201, mergeRequest("opened"));
        var result = ensureTaskMr(claim);
        assertThat(result.outcome()).isEqualTo(GitWriteResult.Outcome.APPLIED);
        taskRuns.completeTask(claim, taskStep(claim, RunItemResult.CREATED), checkpointMetadata(result.evidence()));
        var mergeClaim = taskRuns.claimNextTask(claim.runId()).orElseThrow();
        SERVER.reply("PUT", canonicalEndpoint + "/merge_requests/7/merge", 422, "{\"message\":\"Approval is required\"}");
        GitBranchPort.MergeResult blocked;
        try (var scope = GitOperationContext.withTask(mergeClaim)) {
            blocked = factory.getAdapter(GitProvider.GITLAB).tryMergeRequest(
                    repo.getCloneUrl(), "fixture-token", checkpoint(mergeClaim), "fixture merge");
        }
        assertThat(blocked.status()).isEqualTo(io.releasehub.domain.run.MergeStatus.BLOCKED);
        assertThat(blocked.mrEvidence().blockReason()).isEqualTo(GitBranchPort.MergeBlockReason.APPROVAL_REQUIRED);
        assertThat(journal.list(repo.getId().value(), 0, 20))
                .anyMatch(operation -> operation.state().equals("REJECTED") && operation.blocked());
        var failed = taskRuns.completeTask(mergeClaim, taskStep(mergeClaim, RunItemResult.MERGE_BLOCKED),
                Map.of("git.blockReason", "APPROVAL_REQUIRED"));
        assertThat(failed.getExecutionStatus().name()).isEqualTo("FAILED");
        assertThat(journal.list(repo.getId().value(), 0, 20)).hasSize(2)
                .noneMatch(GitOperationJournalPort.Operation::blocked);
        branchSuccess();
        assertThat(write()).isTrue();
        assertThat(SERVER.writeCount()).isEqualTo(3);
        SERVER.verify("POST", canonicalEndpoint + "/merge_requests", 1);
        SERVER.verify("PUT", canonicalEndpoint + "/merge_requests/7/merge", 1);
        assertNoNamespaceWrites();
    }

    @Test
    @Timeout(30)
    void boundedWorkerLetsAnotherRepositoryProgressWhileTheFirstHttpResponseIsHeld() throws Exception {
        var other = createRepo(path + "-parallel");
        int otherProject = PROJECT.incrementAndGet();
        String otherSha = String.format("%040x", otherProject);
        String otherEndpoint = "/api/v4/projects/" + URLEncoder.encode(path + "-parallel", StandardCharsets.UTF_8);
        SERVER.reply("GET", otherEndpoint, 200, "{\"id\":" + otherProject + "}");
        SERVER.reply("GET", "/api/v4/projects/" + otherProject + "/repository/commits/main", 200,
                "{\"id\":\"" + otherSha + "\"}");
        var orderedRepos = java.util.stream.Stream.of(repo, other)
                .sorted(java.util.Comparator.comparing(value -> value.getId().value())).toList();
        var firstRepo = orderedRepos.getFirst();
        var secondRepo = orderedRepos.getLast();
        var projectIds = Map.of(repo.getId().value(), project, other.getId().value(), otherProject);
        var sourceShas = Map.of(repo.getId().value(), sha, other.getId().value(), otherSha);
        String firstApi = "/api/v4/projects/" + projectIds.get(firstRepo.getId().value());
        String secondApi = "/api/v4/projects/" + projectIds.get(secondRepo.getId().value());
        String windowId = UUID.randomUUID().toString();
        String iterationKey = "ITER-WORKER-" + UUID.randomUUID();
        String feature = "feature/" + iterationKey;
        String release = "release/worker-" + windowId;
        String featureRoute = "/repository/branches/" + URLEncoder.encode(feature, StandardCharsets.UTF_8);
        String releaseRoute = "/repository/branches/" + URLEncoder.encode(release, StandardCharsets.UTF_8);
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var featureBodies = new java.util.HashMap<String, String>();
        for (var repository : orderedRepos) {
            String api = "/api/v4/projects/" + projectIds.get(repository.getId().value());
            String commit = sourceShas.get(repository.getId().value());
            String featureBody = json.writeValueAsString(Map.of("name", feature, "commit", Map.of("id", commit)));
            featureBodies.put(api + "/repository/branches", featureBody);
            SERVER.reply("GET", api + featureRoute, 404, "{}");
            SERVER.reply("GET", api + releaseRoute, 200,
                    json.writeValueAsString(Map.of("name", release, "commit", Map.of("id", commit))));
            SERVER.reply("POST", api + "/repository/branches", 201, featureBody);
        }
        Instant now = Instant.now();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            taskWindows.save(io.releasehub.domain.releasewindow.ReleaseWindow.rehydrate(
                    io.releasehub.domain.releasewindow.ReleaseWindowId.of(windowId), "RW-" + windowId,
                    "并发任务验证", "", now, "G001", io.releasehub.domain.releasewindow.ReleaseWindowStatus.DRAFT,
                    now, now, false, null));
            taskIterations.save(io.releasehub.domain.iteration.Iteration.rehydrate(
                    io.releasehub.domain.iteration.IterationKey.of(iterationKey), "并发迭代", "", null, "G001",
                    Set.of(repo.getId(), other.getId()), io.releasehub.domain.iteration.IterationStatus.ACTIVE, now, now));
            for (var repository : orderedRepos) {
                taskVersions.saveWithVersion(iterationKey, repository.getId().value(), "1.0.0", "1.1.0-SNAPSHOT", "1.1.0",
                        feature, "SYSTEM", now, io.releasehub.domain.iteration.BranchCreationMode.NAMED);
            }
            taskBindings.attach(io.releasehub.domain.releasewindow.ReleaseWindowId.of(windowId),
                    io.releasehub.domain.iteration.IterationKey.of(iterationKey), now);
            taskBindings.updateReleaseBranch(windowId, iterationKey, release, now);
        });
        var admission = taskPlans.admit(windowId, orderedRepos.stream().map(value -> value.getId().value()).toList(),
                List.of(iterationKey), true, "parallel-task-operator", UUID.randomUUID().toString());
        var run = taskRuns.findById(admission.runId()).orElseThrow();
        String firstItem = run.getItems().stream().filter(item -> item.getRepo().equals(firstRepo.getId()))
                .findFirst().orElseThrow().getId().value();
        String secondItem = run.getItems().stream().filter(item -> item.getRepo().equals(secondRepo.getId()))
                .findFirst().orElseThrow().getId().value();
        var entered = new java.util.concurrent.CountDownLatch(1);
        var releaseResponse = new java.util.concurrent.CountDownLatch(1);
        Object target = AopTestUtils.getUltimateTargetObject(factory.getAdapter(GitProvider.GITLAB));
        var http = (RestTemplate) ReflectionTestUtils.getField(target, "restTemplate");
        assertThat(http).isNotNull();
        var original = List.copyOf(http.getInterceptors());
        var interceptors = new java.util.ArrayList<>(original);
        // 复用真实 HTTP fixture；扣住客户端响应，不把其单线程服务端变成跨仓阻塞源。
        interceptors.add((request, body, execution) -> {
            var response = execution.execute(request, body);
            String route = request.getURI().getRawPath();
            if (request.getMethod() == org.springframework.http.HttpMethod.POST && featureBodies.containsKey(route)) {
                String api = route.substring(0, route.length() - "/repository/branches".length());
                SERVER.reply("GET", api + featureRoute, 200, featureBodies.get(route));
                if (route.equals(firstApi + "/repository/branches")) {
                    entered.countDown();
                    try {
                        if (!releaseResponse.await(15, java.util.concurrent.TimeUnit.SECONDS)) {
                            response.close();
                            throw new java.io.IOException("fixture response gate timed out");
                        }
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        response.close();
                        throw new java.io.IOException("fixture response gate interrupted", interrupted);
                    }
                }
            }
            return response;
        });
        http.setInterceptors(interceptors);
        try (var worker = new io.releasehub.infrastructure.run.RunTaskWorker(taskRuns, taskService, 2, 0, 32)) {
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while (entered.getCount() != 0 && System.nanoTime() < deadline) {
                worker.scanOnce();
                entered.await(20, java.util.concurrent.TimeUnit.MILLISECONDS);
            }
            assertThat(entered.getCount()).as("first repository HTTP reached the response gate").isZero();
            assertThat(workerTaskStatus(run.getId().value(), firstItem, ActionType.ENSURE_FEATURE)).isEqualTo("RUNNING");
            assertThat(journal.list(firstRepo.getId().value(), 0, 20)).singleElement()
                    .satisfies(operation -> assertThat(operation.state()).isEqualTo("SEND_STARTED"));

            deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while (!"COMPLETED".equals(workerTaskStatus(run.getId().value(), secondItem, ActionType.ENSURE_FEATURE))
                    && System.nanoTime() < deadline) {
                worker.scanOnce();
                Thread.sleep(20);
            }
            assertThat(workerTaskStatus(run.getId().value(), secondItem, ActionType.ENSURE_FEATURE)).isEqualTo("COMPLETED");
            assertThat(workerTaskStatus(run.getId().value(), firstItem, ActionType.ENSURE_FEATURE)).isEqualTo("RUNNING");
            assertThat(taskRuns.findTasks(run.getId().value()).stream()
                    .filter(task -> task.itemId().equals(firstItem) && task.actionType() != ActionType.ENSURE_FEATURE))
                    .hasSize(3).allMatch(task -> task.status().equals("PENDING"));
            SERVER.verify("GET", firstApi + releaseRoute, 0);
            SERVER.verify("POST", firstApi + "/repository/branches", 1);
            SERVER.verify("POST", secondApi + "/repository/branches", 1);

            releaseResponse.countDown();
            deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while (!"COMPLETED".equals(workerTaskStatus(run.getId().value(), firstItem, ActionType.ENSURE_FEATURE))
                    && System.nanoTime() < deadline) Thread.sleep(20);
            assertThat(workerTaskStatus(run.getId().value(), firstItem, ActionType.ENSURE_FEATURE)).isEqualTo("COMPLETED");
            SERVER.verify("POST", firstApi + "/repository/branches", 1);
            assertThat(journal.list(firstRepo.getId().value(), 0, 20)).singleElement().satisfies(operation -> {
                assertThat(operation.state()).isEqualTo("APPLIED");
                assertThat(operation.blocked()).isFalse();
            });
        } finally {
            releaseResponse.countDown();
            http.setInterceptors(original);
        }
    }

    @Test
    void takeoverRejectsOldWriteAfterPrewriteReadReturns() throws Exception {
        assertLateReadCannotOutliveClaim(false);
    }

    @Test
    void takeoverRejectsOldReadonlyResultAfterPrewriteReadReturns() throws Exception {
        assertLateReadCannotOutliveClaim(true);
    }

    private void assertLateReadCannotOutliveClaim(boolean reuseExistingMr) throws Exception {
        var oldClaim = claimedMergeTask();
        readyForNewMerge();
        if (reuseExistingMr) SERVER.reply("GET", canonicalEndpoint + "/merge_requests", 200,
                "[" + mergeRequest("opened") + "]");
        SERVER.reply("POST", canonicalEndpoint + "/merge_requests", 201, mergeRequest("opened"));
        var originalTask = taskRuns.findTasks(oldClaim.runId()).stream()
                .filter(task -> task.taskId().equals(oldClaim.fence().taskId())).findFirst().orElseThrow();
        var prefix = taskRuns.findById(oldClaim.runId()).orElseThrow().getItems().getFirst().getSteps();
        var entered = new java.util.concurrent.CountDownLatch(1);
        var releaseResponse = new java.util.concurrent.CountDownLatch(1);
        var firstRead = new java.util.concurrent.atomic.AtomicBoolean(true);
        Object target = AopTestUtils.getUltimateTargetObject(factory.getAdapter(GitProvider.GITLAB));
        var http = (RestTemplate) ReflectionTestUtils.getField(target, "restTemplate");
        assertThat(http).isNotNull();
        var original = List.copyOf(http.getInterceptors());
        var interceptors = new java.util.ArrayList<>(original);
        interceptors.add((request, body, execution) -> {
            var response = execution.execute(request, body);
            if (request.getMethod() == org.springframework.http.HttpMethod.GET
                    && request.getURI().getRawPath().equals(canonicalEndpoint + "/merge_requests")
                    && firstRead.compareAndSet(true, false)) {
                holdFixtureResponse(response, entered, releaseResponse);
            }
            return response;
        });
        http.setInterceptors(interceptors);
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var late = executor.submit(() -> ensureTaskMr(oldClaim));
            assertThat(entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(journal.list(repo.getId().value(), 0, 20)).isEmpty();
            assertThat(SERVER.writeCount()).isZero();
            expireTaskClaim(oldClaim);

            var candidate = taskRuns.claimNextTask(oldClaim.runId());
            assertThat(candidate).as("expired unwritten task must be claimable while its old GET is held").isPresent();
            var newClaim = candidate.orElseThrow();
            assertThat(newClaim.fence().taskId()).isEqualTo(oldClaim.fence().taskId());
            assertThat(newClaim.fence().claimToken()).isNotEqualTo(oldClaim.fence().claimToken());
            assertThat(taskRuns.findTasks(oldClaim.runId()).stream()
                    .filter(task -> task.taskId().equals(oldClaim.fence().taskId())).findFirst().orElseThrow())
                    .isEqualTo(originalTask);

            releaseResponse.countDown();
            if (reuseExistingMr) {
                var oldResult = late.get(5, java.util.concurrent.TimeUnit.SECONDS);
                assertThat(oldResult.evidence().disposition()).isEqualTo(GitBranchPort.MrDisposition.MR_REUSED);
                assertThatThrownBy(() -> taskRuns.completeTask(oldClaim,
                        taskStep(oldClaim, RunItemResult.MR_REUSED), checkpointMetadata(oldResult.evidence())))
                        .isInstanceOf(BusinessException.class);
            } else {
                assertThatThrownBy(() -> late.get(5, java.util.concurrent.TimeUnit.SECONDS))
                        .isInstanceOf(java.util.concurrent.ExecutionException.class)
                        .hasCauseInstanceOf(BusinessException.class);
            }
            assertThat(SERVER.writeCount()).as("superseded reader must not send a write").isZero();
            assertThat(journal.list(repo.getId().value(), 0, 20)).isEmpty();
            assertThat(taskRuns.findById(oldClaim.runId()).orElseThrow().getItems().getFirst().getSteps()).isEqualTo(prefix);
            assertThat(jdbc.queryForObject("select claim_token from run_task where id=?", String.class,
                    oldClaim.fence().taskId())).isEqualTo(newClaim.fence().claimToken());

            var result = ensureTaskMr(newClaim);
            assertThat(result.outcome()).isEqualTo(GitWriteResult.Outcome.APPLIED);
            taskRuns.completeTask(newClaim, taskStep(newClaim,
                    reuseExistingMr ? RunItemResult.MR_REUSED : RunItemResult.CREATED), checkpointMetadata(result.evidence()));
            assertTaskStatus(newClaim, "COMPLETED");
            assertThat(jdbc.queryForObject("select count(*) from run_step where task_id=?", Integer.class,
                    newClaim.fence().taskId())).isEqualTo(1);
            SERVER.verify("POST", canonicalEndpoint + "/merge_requests", reuseExistingMr ? 0 : 1);
            assertThat(journal.list(repo.getId().value(), 0, 20)).hasSize(reuseExistingMr ? 0 : 1)
                    .noneMatch(GitOperationJournalPort.Operation::blocked);
        } finally {
            executor.shutdownNow();
            releaseResponse.countDown();
            try {
                assertThat(executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            } finally {
                http.setInterceptors(original);
            }
        }
    }

    @Test
    void expiryDuringSentResponseDoesNotRevokeTheRecordedOwner() throws Exception {
        var claim = claimedMergeTask();
        readyForNewMerge();
        SERVER.reply("POST", canonicalEndpoint + "/merge_requests", 201, mergeRequest("opened"));
        var entered = new java.util.concurrent.CountDownLatch(1);
        var releaseResponse = new java.util.concurrent.CountDownLatch(1);
        Object target = AopTestUtils.getUltimateTargetObject(factory.getAdapter(GitProvider.GITLAB));
        var http = (RestTemplate) ReflectionTestUtils.getField(target, "restTemplate");
        assertThat(http).isNotNull();
        var original = List.copyOf(http.getInterceptors());
        var interceptors = new java.util.ArrayList<>(original);
        interceptors.add((request, body, execution) -> {
            var response = execution.execute(request, body);
            if (request.getMethod() == org.springframework.http.HttpMethod.POST
                    && request.getURI().getRawPath().equals(canonicalEndpoint + "/merge_requests")) {
                holdFixtureResponse(response, entered, releaseResponse);
            }
            return response;
        });
        http.setInterceptors(interceptors);
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var inFlight = executor.submit(() -> ensureTaskMr(claim));
            assertThat(entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(journal.list(repo.getId().value(), 0, 20)).singleElement()
                    .satisfies(operation -> assertThat(operation.state()).isEqualTo("SEND_STARTED"));
            expireTaskClaim(claim);
            assertThat(taskRuns.claimNextTask(claim.runId())).isEmpty();
            assertThat(jdbc.queryForObject("select claim_token from run_task where id=?", String.class,
                    claim.fence().taskId())).isEqualTo(claim.fence().claimToken());
            releaseResponse.countDown();
            var result = inFlight.get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(result.outcome()).isEqualTo(GitWriteResult.Outcome.APPLIED);
            taskRuns.completeTask(claim, taskStep(claim, RunItemResult.CREATED), checkpointMetadata(result.evidence()));
            assertTaskStatus(claim, "COMPLETED");
            assertThat(journal.list(repo.getId().value(), 0, 20)).singleElement().satisfies(operation -> {
                assertThat(operation.state()).isEqualTo("APPLIED");
                assertThat(operation.blocked()).isFalse();
            });
            SERVER.verify("POST", canonicalEndpoint + "/merge_requests", 1);
        } finally {
            executor.shutdownNow();
            releaseResponse.countDown();
            try {
                assertThat(executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            } finally {
                http.setInterceptors(original);
            }
        }
    }

    @Test
    void committedIncompleteIntentWinsBeginLockButCannotAuthorizeRecovery() throws Exception {
        var mrClaim = claimedMergeTask();
        readyForNewMerge();
        SERVER.reply("GET", canonicalEndpoint + "/merge_requests", 200, "[" + mergeRequest("opened") + "]");
        var reused = ensureTaskMr(mrClaim);
        taskRuns.completeTask(mrClaim, taskStep(mrClaim, RunItemResult.MR_REUSED), checkpointMetadata(reused.evidence()));
        var claim = taskRuns.claimNextTask(mrClaim.runId()).orElseThrow();
        assertThat(claim.actionType()).isEqualTo(ActionType.TRY_MERGE);
        expireTaskClaim(claim);
        String operationId = UUID.randomUUID().toString();
        String remoteKey = GitOperationJournalPort.remoteKey("GITLAB", SERVER.baseUrl(), String.valueOf(project));
        String intent = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                Map.of("method", "PUT", "suffix", "/merge_requests/7/merge", "sha", sha));
        var command = new GitOperationJournalPort.Command(operationId, repo.getId().value(),
                GitOperationJournalPort.mappingHash("GITLAB", repo.getCloneUrl()), remoteKey, "GITLAB",
                SERVER.baseUrl(), String.valueOf(project), "MERGE_MR", intent, "a".repeat(64), claim.runId(),
                claim.operator(), claim.fence().claimToken(), claim.fence());
        GitOperationJournalPort actualJournal = AopTestUtils.getUltimateTargetObject(journal);
        var entered = new java.util.concurrent.CountDownLatch(1);
        var commitIntent = new java.util.concurrent.CountDownLatch(1);
        var blockerPid = new AtomicInteger();
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            // 真实 begin 在测试控制的同一物理事务内持有 Run→Task 锁，直到允许提交。
            var owner = executor.submit(() -> new TransactionTemplate(transactions).execute(tx -> {
                blockerPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                String id = actualJournal.begin(command).id();
                entered.countDown();
                try {
                    if (!commitIntent.await(10, java.util.concurrent.TimeUnit.SECONDS))
                        throw new IllegalStateException("fixture intent transaction timed out");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
                return id;
            }));
            assertThat(entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var contender = executor.submit(() -> taskRuns.claimNextTask(claim.runId()));
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            int blocked = 0;
            while (blocked == 0 && System.nanoTime() < deadline) {
                blocked = jdbc.queryForObject("select count(*) from pg_stat_activity where ? = any(pg_blocking_pids(pid))",
                        Integer.class, blockerPid.get());
                if (blocked == 0) Thread.sleep(20);
            }
            assertThat(blocked).as("takeover must actually wait for the original begin transaction").isPositive();
            commitIntent.countDown();
            assertThat(owner.get(5, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(operationId);
            assertThat(contender.get(5, java.util.concurrent.TimeUnit.SECONDS)).isEmpty();
        } finally {
            commitIntent.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }

        var guard = jdbc.queryForMap("select * from repo_execution_guard where remote_key=?", remoteKey);
        var events = jdbc.queryForList("select * from git_operation_event where operation_id=? order by event_sequence", operationId);
        int requestsBefore = SERVER.requests.size();
        for (String operationToken : List.of(claim.fence().claimToken(), UUID.randomUUID().toString())) {
            for (String state : List.of("INTENT_RECORDED", "SEND_STARTED", "APPLIED", "REJECTED", "UNKNOWN", "NEEDS_REVIEW")) {
                // INTENT/APPLIED/REJECTED 可被发现；缺少完整请求及 sender/响应证明的账本仍不授予执行或投影权。
                jdbc.update("update git_operation set state=?,claim_token=? where id=?", state, operationToken, operationId);
                var operation = jdbc.queryForMap("select * from git_operation where id=?", operationId);
                var candidates = taskRuns.findTaskCandidateRunIds(null, 1000);
                if (Set.of("INTENT_RECORDED", "APPLIED", "REJECTED").contains(state)) assertThat(candidates).contains(claim.runId());
                else assertThat(candidates).doesNotContain(claim.runId());
                assertThat(taskRuns.projectNextConfirmedTask(claim.runId())).isFalse();
                assertThat(taskRuns.claimNextTask(claim.runId())).isEmpty();
                assertThat(jdbc.queryForObject("select claim_token from run_task where id=?", String.class,
                        claim.fence().taskId())).isEqualTo(claim.fence().claimToken());
                assertThat(jdbc.queryForMap("select * from git_operation where id=?", operationId)).isEqualTo(operation);
                assertThat(jdbc.queryForList("select * from git_operation_event where operation_id=? order by event_sequence", operationId))
                        .isEqualTo(events);
                assertThat(jdbc.queryForMap("select * from repo_execution_guard where remote_key=?", remoteKey)).isEqualTo(guard);
            }
        }
        assertThat(SERVER.requests).hasSize(requestsBefore);
        assertThat(SERVER.writeCount()).isZero();
        assertTaskStatus(claim, "RUNNING");
    }

    @Test
    void mergeIntentNewOwnerKeepsTheResultWhenOldSenderLoses() throws Exception {
        String runId = mergeIntentReadyRun();
        var workers = java.util.concurrent.Executors.newFixedThreadPool(2);
        try (var gate = new MergeIntentGate(false, true)) {
            try {
                var oldOwner = workers.submit(() -> taskService.executeNextTask(runId));
                assertThat(gate.entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                var original = gate.command.get();
                String operationId = gate.operationId.get();
                var originalIdentity = mergeIntentIdentity(operationId);
                var originalEvents = journal.find(operationId).orElseThrow().events();
                var originalTask = jdbc.queryForMap("select * from run_task where id=?", original.taskFence().taskId());
                assertThat(originalEvents).extracting(GitOperationJournalPort.Event::type)
                        .containsExactly("INTENT_RECORDED");
                gate.expire();
                var newOwner = workers.submit(() -> taskService.executeNextTask(runId));
                assertThat(gate.responseEntered.await(5, java.util.concurrent.TimeUnit.SECONDS))
                        .as("the new owner must reach the original merge HTTP request").isTrue();
                String currentToken = jdbc.queryForObject("select claim_token from run_task where id=?",
                        String.class, original.taskFence().taskId());
                assertThat(currentToken).isNotEqualTo(original.taskFence().claimToken());
                assertThat(mergeIntentIdentity(operationId)).isEqualTo(originalIdentity);
                assertThat(journal.find(operationId).orElseThrow().operation().state()).isEqualTo("SEND_STARTED");

                gate.release.countDown();
                assertThatThrownBy(() -> oldOwner.get(5, java.util.concurrent.TimeUnit.SECONDS))
                        .isInstanceOf(java.util.concurrent.ExecutionException.class)
                        .hasCauseInstanceOf(BusinessException.class);
                assertThat(jdbc.queryForMap("select status,claim_token from run_task where id=?", original.taskFence().taskId()))
                        .containsEntry("status", "RUNNING").containsEntry("claim_token", currentToken);
                assertThat(jdbc.queryForObject("select count(*) from run_step where task_id=?",
                        Integer.class, original.taskFence().taskId())).isZero();
                SERVER.verify("PUT", canonicalEndpoint + "/merge_requests/7/merge", 1);

                gate.releaseResponse.countDown();
                assertThat(newOwner.get(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                assertThat(taskRuns.findById(runId).orElseThrow().getExecutionStatus())
                        .isEqualTo(io.releasehub.domain.run.RunExecutionStatus.DONE);
                var completed = jdbc.queryForMap("select * from run_task where id=?", original.taskFence().taskId());
                assertThat(completed).containsEntry("status", "COMPLETED").containsEntry("claim_token", currentToken)
                        .containsEntry("started_at", originalTask.get("started_at"));
                assertThat(jdbc.queryForObject("select count(*) from run_step where task_id=?",
                        Integer.class, original.taskFence().taskId())).isEqualTo(1);
                assertThat(mergeIntentIdentity(operationId)).isEqualTo(originalIdentity);
                var detail = journal.find(operationId).orElseThrow();
                assertThat(detail.events()).hasSize(3);
                assertThat(detail.events().getFirst()).isEqualTo(originalEvents.getFirst());
                assertThat(detail.events()).extracting(GitOperationJournalPort.Event::attemptId)
                        .containsOnly(originalEvents.getFirst().attemptId());
                mergeIntentAssertSender(operationId, new RunPort.TaskFence(original.taskFence().taskId(), currentToken));
                assertThat(detail.operation().state()).isEqualTo("APPLIED");
                assertThat(detail.operation().blocked()).isFalse();
                assertThat(journal.list(repo.getId().value(), 0, 20)).hasSize(1);
                SERVER.verify("PUT", canonicalEndpoint + "/merge_requests/7/merge", 1);
                assertThat(SERVER.writeCount()).isEqualTo(1);
            } finally {
                gate.releaseAll();
                workers.shutdown();
                assertThat(workers.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    @Test
    void mergeIntentCommittedSendKeepsItsOwnerAndLateResponse() throws Exception {
        String runId = mergeIntentReadyRun();
        var workers = java.util.concurrent.Executors.newSingleThreadExecutor();
        try (var gate = new MergeIntentGate(true, false)) {
            try {
                var oldOwner = workers.submit(() -> taskService.executeNextTask(runId));
                assertThat(gate.entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                var original = gate.command.get();
                String operationId = gate.operationId.get();
                assertThat(journal.find(operationId).orElseThrow().operation().state()).isEqualTo("SEND_STARTED");
                SERVER.verify("PUT", canonicalEndpoint + "/merge_requests/7/merge", 0);
                gate.expire();
                var before = jdbc.queryForMap("select * from run_task where id=?", original.taskFence().taskId());
                assertThat(taskService.executeNextTask(runId)).isFalse();
                assertThat(jdbc.queryForMap("select * from run_task where id=?", original.taskFence().taskId())).isEqualTo(before);
                SERVER.verify("PUT", canonicalEndpoint + "/merge_requests/7/merge", 0);
                gate.release.countDown();
                assertThat(oldOwner.get(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                assertThat(taskRuns.findById(runId).orElseThrow().getExecutionStatus())
                        .isEqualTo(io.releasehub.domain.run.RunExecutionStatus.DONE);
                mergeIntentAssertSender(operationId, original.taskFence());
                assertThat(journal.find(operationId).orElseThrow().operation().state()).isEqualTo("APPLIED");
                assertThat(journal.find(operationId).orElseThrow().operation().blocked()).isFalse();
                SERVER.verify("PUT", canonicalEndpoint + "/merge_requests/7/merge", 1);
            } finally {
                gate.releaseAll();
                workers.shutdown();
                assertThat(workers.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"unavailable", "closed", "source_changed", "contained", "blocked"})
    void mergeIntentPreflightClosesOnlyTheUnsentReservation(String observation) throws Exception {
        String runId = mergeIntentReadyRun();
        var workers = java.util.concurrent.Executors.newSingleThreadExecutor();
        try (var gate = new MergeIntentGate(false, false)) {
            try {
                var oldOwner = workers.submit(() -> taskService.executeNextTask(runId));
                assertThat(gate.entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                var original = gate.command.get();
                String operationId = gate.operationId.get();
                var identity = mergeIntentIdentity(operationId);
                var events = journal.find(operationId).orElseThrow().events();
                assertThat(events).extracting(GitOperationJournalPort.Event::type).containsExactly("INTENT_RECORDED");
                var originalMetadata = new LinkedHashMap<>(taskRuns.findById(runId).orElseThrow()
                        .getItems().getFirst().getMetadata());
                var guard = jdbc.queryForMap("select * from repo_execution_guard where remote_key=?", original.remoteKey());
                gate.expire();
                switch (observation) {
                    case "unavailable" -> SERVER.reply("GET", canonicalEndpoint + "/merge_requests/7", 503, "{}");
                    case "closed" -> SERVER.reply("GET", canonicalEndpoint + "/merge_requests/7", 200, mergeRequest("closed"));
                    case "source_changed" -> SERVER.reply("GET", canonicalEndpoint + "/merge_requests/7", 200,
                            mergeRequest("opened").replace(sha, "f".repeat(40)));
                    case "contained" -> SERVER.reply("GET", canonicalEndpoint + "/repository/commits/" + sha + "/refs", 200,
                            "[{\"type\":\"branch\",\"name\":\"main\"}]");
                    case "blocked" -> SERVER.reply("GET", canonicalEndpoint + "/merge_requests/7", 200,
                            mergeRequest("opened").replace("\"mergeable\"", "\"not_approved\""));
                    default -> throw new AssertionError(observation);
                }
                RunItemResult result = switch (observation) {
                    case "contained" -> RunItemResult.ALREADY_MERGED;
                    case "blocked" -> RunItemResult.MERGE_BLOCKED;
                    default -> RunItemResult.UNKNOWN;
                };
                Map<String, String> patch = new LinkedHashMap<>();
                patch.put("git.requestId", null);
                patch.put("git.blockReason", result == RunItemResult.ALREADY_MERGED ? null
                        : result == RunItemResult.MERGE_BLOCKED ? "APPROVAL_REQUIRED" : "UNKNOWN");
                if (result == RunItemResult.ALREADY_MERGED) {
                    patch.put("git.mergeSha", null);
                    patch.put("git.mrDisposition", "ALREADY_MERGED");
                }
                boolean completed = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> taskService.executeNextTask(runId));
                assertThat(completed).isTrue();
                String currentToken = jdbc.queryForObject("select claim_token from run_task where id=?",
                        String.class, original.taskFence().taskId());
                assertThat(currentToken).isNotEqualTo(original.taskFence().claimToken());
                String status = result == RunItemResult.ALREADY_MERGED ? "COMPLETED"
                        : result == RunItemResult.MERGE_BLOCKED ? "FAILED" : "UNKNOWN";
                assertThat(jdbc.queryForObject("select status from run_task where id=?",
                        String.class, original.taskFence().taskId())).isEqualTo(status);
                mergeNotSentAssertEvent(operationId, original.taskFence().taskId(), currentToken, result, patch, events.getFirst());
                assertThat(mergeIntentIdentity(operationId)).isEqualTo(identity);
                var releasedGuard = jdbc.queryForMap("select * from repo_execution_guard where remote_key=?", original.remoteKey());
                assertThat(releasedGuard).containsEntry("generation", guard.get("generation"))
                        .containsEntry("session_id", null).containsEntry("operation_id", null).containsEntry("reason", null);
                assertThat(jdbc.queryForList("select result from run_step where task_id=?", original.taskFence().taskId()))
                        .singleElement().satisfies(row -> assertThat(row).containsEntry("result", result.name()));
                var finalRun = taskRuns.findById(runId).orElseThrow();
                var expectedMetadata = new LinkedHashMap<>(originalMetadata);
                patch.forEach((key, value) -> { if (value == null) expectedMetadata.remove(key); else expectedMetadata.put(key, value); });
                assertThat(finalRun.getItems().getFirst().getMetadata()).isEqualTo(expectedMetadata);
                assertThat(finalRun.getExecutionStatus()).isEqualTo(result == RunItemResult.ALREADY_MERGED
                        ? io.releasehub.domain.run.RunExecutionStatus.DONE : result == RunItemResult.MERGE_BLOCKED
                        ? io.releasehub.domain.run.RunExecutionStatus.FAILED : io.releasehub.domain.run.RunExecutionStatus.NEEDS_REVIEW);
                var lastMergeAt = jdbc.queryForObject("select last_merge_at from window_iteration where window_id=?",
                        java.sql.Timestamp.class, originalMetadata.get("plan.windowId"));
                if (result == RunItemResult.ALREADY_MERGED) assertThat(lastMergeAt).isNotNull();
                else assertThat(lastMergeAt).isNull();
                SERVER.verify("POST", canonicalEndpoint + "/merge_requests", 0);
                SERVER.verify("PUT", canonicalEndpoint + "/merge_requests/7/merge", 0);
                assertThat(SERVER.writeCount()).isZero();
                var closedTask = jdbc.queryForMap("select * from run_task where id=?", original.taskFence().taskId());
                var closedOperation = jdbc.queryForMap("select * from git_operation where id=?", operationId);
                var closedEvents = journal.find(operationId).orElseThrow().events();
                gate.release.countDown();
                assertThatThrownBy(() -> oldOwner.get(5, java.util.concurrent.TimeUnit.SECONDS))
                        .isInstanceOf(java.util.concurrent.ExecutionException.class)
                        .hasCauseInstanceOf(BusinessException.class);
                assertThat(jdbc.queryForMap("select * from run_task where id=?", original.taskFence().taskId())).isEqualTo(closedTask);
                assertThat(jdbc.queryForMap("select * from git_operation where id=?", operationId)).isEqualTo(closedOperation);
                assertThat(journal.find(operationId).orElseThrow().events()).isEqualTo(closedEvents);
                assertThat(taskRuns.findById(runId).orElseThrow().getItems().getFirst().getMetadata()).isEqualTo(expectedMetadata);
                assertThat(jdbc.queryForMap("select * from repo_execution_guard where remote_key=?", original.remoteKey())).isEqualTo(releasedGuard);
                SERVER.verify("PUT", canonicalEndpoint + "/merge_requests/7/merge", 0);
            } finally {
                gate.releaseAll();
                workers.shutdown();
                assertThat(workers.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "unsentPreflight={0}")
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "contained_missing_mr", "contained_changed_mr", "conflict", "approval",
            "pipeline_pending", "pipeline_failed", "forbidden", "unavailable", "closed",
            "source_changed", "merged_target_reset"})
    void mergeNotSentPreflightClosesOriginalIntent(String observation) throws Exception {
        boolean followingIteration = observation.equals("unavailable");
        String runId = mergeIntentReadyRun(followingIteration);
        var workers = java.util.concurrent.Executors.newSingleThreadExecutor();
        try (var gate = new MergeIntentGate(false, false)) {
            try {
                var oldOwner = workers.submit(() -> taskService.executeNextTask(runId));
                assertThat(gate.entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                var original = gate.command.get();
                String operationId = gate.operationId.get();
                String taskId = original.taskFence().taskId();
                var originalIdentity = mergeIntentIdentity(operationId);
                var originalEvents = journal.find(operationId).orElseThrow().events();
                var originalTask = jdbc.queryForMap("select * from run_task where id=?", taskId);
                String itemId = originalTask.get("target_id").toString();
                String iterationKey = jdbc.queryForObject("select iteration_key from run_item where id=?", String.class, itemId);
                var originalMetadata = mergeNotSentMetadata(itemId);
                var originalOrder = jdbc.queryForMap("select planned_order,executed_order from run_item where id=?", itemId);
                var originalGuard = jdbc.queryForMap("select * from repo_execution_guard where remote_key=?", original.remoteKey());
                assertThat(originalEvents).extracting(GitOperationJournalPort.Event::type)
                        .containsExactly("INTENT_RECORDED");
                int requestsBeforeResume = SERVER.requests.size();
                gate.expire();
                mergeNotSentObservation(observation);
                System.out.printf("HTTP_MERGE_NOT_SENT_CHECKPOINT observation=%s run=%s task=%s operation=%s attempt=%s events=1 put=0%n",
                        observation, runId, taskId, operationId, originalEvents.getFirst().attemptId());

                boolean resumed = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> taskService.executeNextTask(runId));
                assertThat(resumed).isTrue();
                RunItemResult result = mergeNotSentResult(observation);
                var patch = mergeNotSentPatch(observation);
                var completedTask = jdbc.queryForMap("select * from run_task where id=?", taskId);
                String currentToken = completedTask.get("claim_token").toString();
                assertThat(currentToken).isNotEqualTo(original.taskFence().claimToken());
                String expectedTaskStatus = result == RunItemResult.ALREADY_MERGED ? "COMPLETED"
                        : result == RunItemResult.UNKNOWN ? "UNKNOWN" : "FAILED";
                assertThat(completedTask).containsEntry("status", expectedTaskStatus)
                        .containsEntry("started_at", originalTask.get("started_at"))
                        .containsEntry("task_order", originalTask.get("task_order"));
                assertThat(completedTask.get("finished_at")).isNotNull();
                assertThat(mergeIntentIdentity(operationId)).isEqualTo(originalIdentity);
                mergeNotSentAssertEvent(operationId, taskId, currentToken, result, patch, originalEvents.getFirst());
                var completedMetadata = new LinkedHashMap<>(originalMetadata);
                patch.forEach((key, value) -> { if (value == null) completedMetadata.remove(key); else completedMetadata.put(key, value); });
                assertThat(mergeNotSentMetadata(itemId)).isEqualTo(completedMetadata);
                assertThat(completedMetadata).doesNotContainKey("git.requestId");
                assertThat(jdbc.queryForMap("select planned_order,executed_order from run_item where id=?", itemId)).isEqualTo(originalOrder);
                assertThat(jdbc.queryForList("select result from run_step where task_id=?", taskId))
                        .singleElement().satisfies(row -> assertThat(row).containsEntry("result", result.name()));
                assertThat(jdbc.queryForObject("select final_result from run_item where id=?", String.class, itemId))
                        .isEqualTo(result.name());
                var guard = jdbc.queryForMap("select * from repo_execution_guard where remote_key=?", original.remoteKey());
                assertThat(guard).containsEntry("generation", originalGuard.get("generation"))
                        .containsEntry("session_id", null).containsEntry("operation_id", null).containsEntry("reason", null);
                assertThat(jdbc.queryForObject("select count(*) from git_operation where task_id=?", Integer.class, taskId)).isEqualTo(1);
                var completedRun = taskRuns.findById(runId).orElseThrow();
                assertThat(completedRun.getExecutionStatus()).isEqualTo(result == RunItemResult.ALREADY_MERGED
                        ? io.releasehub.domain.run.RunExecutionStatus.DONE : result == RunItemResult.UNKNOWN
                        ? io.releasehub.domain.run.RunExecutionStatus.NEEDS_REVIEW : io.releasehub.domain.run.RunExecutionStatus.FAILED);
                var lastMergeAt = jdbc.queryForObject("select last_merge_at from window_iteration where window_id=? and iteration_key=?",
                        java.sql.Timestamp.class, originalMetadata.get("plan.windowId"), iterationKey);
                if (result == RunItemResult.ALREADY_MERGED) assertThat(lastMergeAt).isNotNull();
                else assertThat(lastMergeAt).isNull();
                if (observation.startsWith("contained_")) {
                    assertThat(SERVER.requests.subList(requestsBeforeResume, SERVER.requests.size()))
                            .noneMatch(request -> request.method().equals("GET") && request.path().equals(canonicalEndpoint + "/merge_requests/7"));
                }
                if (result == RunItemResult.UNKNOWN) {
                    int runsBeforeRetry = jdbc.queryForObject("select count(*) from run", Integer.class);
                    assertThatThrownBy(() -> taskPlans.admitRetry(completedRun, List.of(itemId),
                            "fixture-task-operator", UUID.randomUUID().toString())).isInstanceOf(BusinessException.class);
                    assertThat(jdbc.queryForObject("select count(*) from run", Integer.class)).isEqualTo(runsBeforeRetry);
                    assertThat(completedRun.getFinishedAt()).isNull();
                }
                if (followingIteration) {
                    assertThat(taskRuns.findTasks(runId).stream().filter(task -> !task.itemId().equals(itemId)).toList())
                            .hasSize(4).allSatisfy(task -> assertThat(task.status()).isEqualTo("SKIPPED"));
                    int beforeNoWork = SERVER.requests.size();
                    assertThat(taskService.executeNextTask(runId)).isFalse();
                    assertThat(SERVER.requests).hasSize(beforeNoWork);
                }
                SERVER.verify("POST", canonicalEndpoint + "/merge_requests", 0);
                SERVER.verify("PUT", canonicalEndpoint + "/merge_requests/7/merge", 0);
                assertThat(SERVER.writeCount()).isZero();
                var closedOperation = jdbc.queryForMap("select * from git_operation where id=?", operationId);
                var closedEvents = journal.find(operationId).orElseThrow().events();
                gate.release.countDown();
                assertThatThrownBy(() -> oldOwner.get(5, java.util.concurrent.TimeUnit.SECONDS))
                        .isInstanceOf(java.util.concurrent.ExecutionException.class).hasCauseInstanceOf(BusinessException.class);
                assertThat(jdbc.queryForMap("select * from run_task where id=?", taskId)).isEqualTo(completedTask);
                assertThat(jdbc.queryForMap("select * from git_operation where id=?", operationId)).isEqualTo(closedOperation);
                assertThat(journal.find(operationId).orElseThrow().events()).isEqualTo(closedEvents);
                assertThat(mergeNotSentMetadata(itemId)).isEqualTo(completedMetadata);
                SERVER.verify("PUT", canonicalEndpoint + "/merge_requests/7/merge", 0);
                System.out.printf("HTTP_MERGE_NOT_SENT_RESULT observation=%s run=%s task=%s operation=%s attempt=%s result=%s "
                                + "oldTokenRevoked=true currentTokenPreserved=true events=%d post=0 put=0 lateWorkerRejected=true%n",
                        observation, runId, taskId, operationId, originalEvents.getFirst().attemptId(), result, closedEvents.size());
            } finally {
                gate.releaseAll();
                workers.shutdown();
                assertThat(workers.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    @Test
    void mergeNotSentBlockedRetryReusesOriginalMr() throws Exception {
        String runId = mergeIntentReadyRun();
        var workers = java.util.concurrent.Executors.newSingleThreadExecutor();
        try (var gate = new MergeIntentGate(false, false)) {
            try {
                var oldOwner = workers.submit(() -> taskService.executeNextTask(runId));
                assertThat(gate.entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                String operationId = gate.operationId.get();
                String taskId = gate.command.get().taskFence().taskId();
                String itemId = jdbc.queryForObject("select target_id from run_task where id=?", String.class, taskId);
                var originalIdentity = mergeIntentIdentity(operationId);
                var originalEvent = journal.find(operationId).orElseThrow().events().getFirst();
                gate.expire();
                mergeNotSentObservation("approval");
                boolean closed = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> taskService.executeNextTask(runId));
                assertThat(closed).isTrue();
                String currentToken = jdbc.queryForObject("select claim_token from run_task where id=?", String.class, taskId);
                mergeNotSentAssertEvent(operationId, taskId, currentToken, RunItemResult.MERGE_BLOCKED,
                        mergeNotSentPatch("approval"), originalEvent);
                var closedOperation = jdbc.queryForMap("select * from git_operation where id=?", operationId);
                var closedEvents = journal.find(operationId).orElseThrow().events();
                var closedTask = jdbc.queryForMap("select * from run_task where id=?", taskId);
                var closedMetadata = mergeNotSentMetadata(itemId);
                gate.release.countDown();
                assertThatThrownBy(() -> oldOwner.get(5, java.util.concurrent.TimeUnit.SECONDS))
                        .isInstanceOf(java.util.concurrent.ExecutionException.class).hasCauseInstanceOf(BusinessException.class);

                SERVER.reply("GET", canonicalEndpoint + "/merge_requests/7", 200, mergeRequest("opened"));
                var failedRun = taskRuns.findById(runId).orElseThrow();
                var admission = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> taskPlans.admitRetry(failedRun, List.of(itemId),
                        "fixture-task-operator", UUID.randomUUID().toString()));
                assertThat(admission.runId()).isNotEqualTo(runId);
                boolean retriedTask = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> taskService.executeNextTask(admission.runId()));
                assertThat(retriedTask).isTrue();
                var retried = taskRuns.findById(admission.runId()).orElseThrow();
                assertThat(retried.getExecutionStatus()).isEqualTo(io.releasehub.domain.run.RunExecutionStatus.DONE);
                assertThat(taskService.executeNextTask(admission.runId())).isFalse();
                assertThat(retried.getItems().getFirst().getMetadata())
                        .containsEntry("git.mrIid", "7").containsEntry("git.sourceSha", sha);
                var newOperation = jdbc.queryForMap("select * from git_operation where run_id=? and action='MERGE_MR'", admission.runId());
                assertThat(newOperation.get("id")).isNotEqualTo(operationId);
                assertThat(newOperation).containsEntry("state", "APPLIED");
                assertThat(journal.find(newOperation.get("id").toString()).orElseThrow().events())
                        .extracting(GitOperationJournalPort.Event::type).containsExactly("INTENT_RECORDED", "SEND_STARTED", "APPLIED");
                assertThat(mergeIntentIdentity(operationId)).isEqualTo(originalIdentity);
                assertThat(jdbc.queryForMap("select * from git_operation where id=?", operationId)).isEqualTo(closedOperation);
                assertThat(journal.find(operationId).orElseThrow().events()).isEqualTo(closedEvents);
                assertThat(jdbc.queryForMap("select * from run_task where id=?", taskId)).isEqualTo(closedTask);
                assertThat(mergeNotSentMetadata(itemId)).isEqualTo(closedMetadata);
                SERVER.verify("POST", canonicalEndpoint + "/merge_requests", 0);
                SERVER.verify("PUT", canonicalEndpoint + "/merge_requests/7/merge", 1);
                assertThat(SERVER.body("PUT", canonicalEndpoint + "/merge_requests/7/merge").path("sha").textValue()).isEqualTo(sha);
                assertThat(SERVER.writeCount()).isEqualTo(1);
                System.out.printf("HTTP_MERGE_NOT_SENT_RETRY originalRun=%s retryRun=%s task=%s originalOperation=%s "
                                + "originalAttempt=%s originalEvents=%d post=0 put=1 originalUnchanged=true%n",
                        runId, admission.runId(), taskId, operationId, originalEvent.attemptId(), closedEvents.size());
            } finally {
                gate.releaseAll();
                workers.shutdown();
                assertThat(workers.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    @Test
    void mergeNotSentCannotFinishWhileSendAwaitsOutcome() throws Exception {
        String runId = mergeIntentReadyRun();
        var claim = taskRuns.claimNextTask(runId).orElseThrow();
        assertThat(claim.actionType()).isEqualTo(ActionType.TRY_MERGE);
        var workers = java.util.concurrent.Executors.newSingleThreadExecutor();
        try (var gate = new MergeIntentGate(false, true)) {
            try {
                var sender = workers.submit(() -> {
                    try (var scope = GitOperationContext.withTask(claim)) {
                        return factory.getAdapter(GitProvider.GITLAB).tryMergeRequest(repo.getCloneUrl(), "fixture-token",
                                checkpoint(claim), "Merge feature into main");
                    }
                });
                assertThat(gate.entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                gate.release.countDown();
                assertThat(gate.responseEntered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                String operationId = gate.operationId.get();
                var originalTask = jdbc.queryForMap("select * from run_task where id=?", claim.fence().taskId());
                var originalOperation = jdbc.queryForMap("select * from git_operation where id=?", operationId);
                var originalEvents = journal.find(operationId).orElseThrow().events();
                var originalMetadata = mergeNotSentMetadata(claim.itemId());
                var originalGuard = jdbc.queryForMap("select * from repo_execution_guard where remote_key=?", gate.command.get().remoteKey());
                assertThat(originalEvents).extracting(GitOperationJournalPort.Event::type)
                        .containsExactly("INTENT_RECORDED", "SEND_STARTED");
                SERVER.verify("PUT", canonicalEndpoint + "/merge_requests/7/merge", 1);
                System.out.printf("HTTP_MERGE_SEND_AWAITING_OUTCOME run=%s task=%s operation=%s attempt=%s events=2 put=1%n",
                        runId, claim.fence().taskId(), operationId, originalEvents.getFirst().attemptId());
                assertThatThrownBy(() -> taskRuns.completeTask(claim, taskStep(claim, RunItemResult.UNKNOWN),
                        Map.of("git.blockReason", "UNKNOWN"))).isInstanceOf(BusinessException.class);
                assertThat(jdbc.queryForMap("select * from run_task where id=?", claim.fence().taskId())).isEqualTo(originalTask);
                assertThat(jdbc.queryForMap("select * from git_operation where id=?", operationId)).isEqualTo(originalOperation);
                assertThat(journal.find(operationId).orElseThrow().events()).isEqualTo(originalEvents);
                assertThat(mergeNotSentMetadata(claim.itemId())).isEqualTo(originalMetadata);
                assertThat(jdbc.queryForMap("select * from repo_execution_guard where remote_key=?", gate.command.get().remoteKey())).isEqualTo(originalGuard);
                assertThat(jdbc.queryForObject("select count(*) from run_step where task_id=?", Integer.class, claim.fence().taskId())).isZero();
                gate.releaseResponse.countDown();
                var result = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> sender.get(5, java.util.concurrent.TimeUnit.SECONDS));
                assertThat(result.status()).isEqualTo(io.releasehub.domain.run.MergeStatus.SUCCESS);
                var completed = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() ->
                        taskRuns.completeTask(claim, taskStep(claim, RunItemResult.MERGED), RunPort.mrResultUpdates(result)));
                assertThat(completed.getExecutionStatus()).isEqualTo(io.releasehub.domain.run.RunExecutionStatus.DONE);
                assertTaskStatus(claim, "COMPLETED");
                assertThat(journal.find(operationId).orElseThrow().events()).extracting(GitOperationJournalPort.Event::type)
                        .containsExactly("INTENT_RECORDED", "SEND_STARTED", "APPLIED");
                assertThat(journal.find(operationId).orElseThrow().operation().blocked()).isFalse();
                SERVER.verify("POST", canonicalEndpoint + "/merge_requests", 0);
                SERVER.verify("PUT", canonicalEndpoint + "/merge_requests/7/merge", 1);
                System.out.printf("HTTP_MERGE_SEND_LATE_FINISH run=%s task=%s operation=%s events=3 put=1 "
                        + "prematureUnknownRejected=true originalSenderPreserved=true%n", runId, claim.fence().taskId(), operationId);
            } finally {
                gate.releaseAll();
                workers.shutdown();
                assertThat(workers.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    @Test
    void mergeObservationBeforeFinishPreservesTheRecordedSenderAndGuard() throws Exception {
        String runId = mergeIntentReadyRun();
        var claim = taskRuns.claimNextTask(runId).orElseThrow();
        assertThat(claim.actionType()).isEqualTo(ActionType.TRY_MERGE);
        var workers = java.util.concurrent.Executors.newSingleThreadExecutor();
        try (var gate = new MergeIntentGate(false, true)) {
            try {
                var sender = workers.submit(() -> {
                    try (var scope = GitOperationContext.withTask(claim)) {
                        return factory.getAdapter(GitProvider.GITLAB).tryMergeRequest(repo.getCloneUrl(), "fixture-token",
                                checkpoint(claim), "Merge feature into main");
                    }
                });
                assertThat(gate.entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                gate.release.countDown();
                assertThat(gate.responseEntered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                String operationId = gate.operationId.get();
                var before = journal.find(operationId).orElseThrow();
                var originalIdentity = mergeIntentIdentity(operationId);
                var originalRun = jdbc.queryForMap("select * from run where id=?", runId);
                var originalItem = jdbc.queryForMap("select * from run_item where id=?", claim.itemId());
                var originalTasks = jdbc.queryForList("select * from run_task where run_id=? order by task_order", runId);
                var originalSteps = jdbc.queryForList("select * from run_step where run_item_id=? order by task_id", claim.itemId());
                var originalMetadata = mergeNotSentMetadata(claim.itemId());
                var originalGuard = jdbc.queryForMap("select * from repo_execution_guard where remote_key=?", gate.command.get().remoteKey());
                assertThat(before.operation().state()).isEqualTo("SEND_STARTED");
                assertThat(before.operation().blocked()).isTrue();
                assertThat(before.events()).extracting(GitOperationJournalPort.Event::type)
                        .containsExactly("INTENT_RECORDED", "SEND_STARTED");
                assertThat(before.observations()).isEmpty();
                SERVER.verify("PUT", canonicalEndpoint + "/merge_requests/7/merge", 1);

                SERVER.reply("GET", canonicalEndpoint + "/merge_requests/7", 200, mergedRequest());
                int readStart = SERVER.requests.size();
                var observed = service.reconcile(operationId, "merge-observer", UUID.randomUUID().toString(),
                        before.operation().version());

                assertThat(observed.status()).isEqualTo("FOUND");
                var after = journal.find(operationId).orElseThrow();
                assertThat(after.operation().version()).isGreaterThan(before.operation().version());
                assertThat(after.operation().state()).isEqualTo("SEND_STARTED");
                assertThat(after.operation().reason()).isEqualTo(before.operation().reason());
                assertThat(after.operation().blocked()).isTrue();
                assertThat(after.events()).isEqualTo(before.events());
                assertThat(after.observations()).containsExactly(observed);
                assertThat(mergeIntentIdentity(operationId)).isEqualTo(originalIdentity);
                assertThat(jdbc.queryForMap("select * from run where id=?", runId)).isEqualTo(originalRun);
                assertThat(jdbc.queryForMap("select * from run_item where id=?", claim.itemId())).isEqualTo(originalItem);
                assertThat(jdbc.queryForList("select * from run_task where run_id=? order by task_order", runId)).isEqualTo(originalTasks);
                assertThat(jdbc.queryForList("select * from run_step where run_item_id=? order by task_id", claim.itemId())).isEqualTo(originalSteps);
                assertThat(mergeNotSentMetadata(claim.itemId())).isEqualTo(originalMetadata);
                assertThat(jdbc.queryForMap("select * from repo_execution_guard where remote_key=?", gate.command.get().remoteKey()))
                        .isEqualTo(originalGuard);
                assertThat(SERVER.requests.subList(readStart, SERVER.requests.size()))
                        .extracting(GitLabHttpFixture.Request::method).containsExactly("GET", "GET");
                assertThat(SERVER.requests.subList(readStart, SERVER.requests.size()))
                        .extracting(GitLabHttpFixture.Request::path).containsExactly(endpoint, canonicalEndpoint + "/merge_requests/7");
                assertThat(SERVER.writeCount()).isEqualTo(1);

                gate.releaseResponse.countDown();
                var result = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> sender.get(5, java.util.concurrent.TimeUnit.SECONDS));
                assertThat(result.status()).isEqualTo(io.releasehub.domain.run.MergeStatus.SUCCESS);
                var applied = journal.find(operationId).orElseThrow();
                assertThat(applied.events()).extracting(GitOperationJournalPort.Event::type)
                        .containsExactly("INTENT_RECORDED", "SEND_STARTED", "APPLIED");
                assertThat(applied.events().subList(0, 2)).isEqualTo(before.events());
                assertThat(applied.observations()).containsExactly(observed);
                mergeIntentAssertSender(operationId, claim.fence());
                var completed = taskRuns.completeTask(claim, taskStep(claim, RunItemResult.MERGED), RunPort.mrResultUpdates(result));
                assertThat(completed.getExecutionStatus()).isEqualTo(io.releasehub.domain.run.RunExecutionStatus.DONE);
                assertTaskStatus(claim, "COMPLETED");
                assertThat(journal.find(operationId).orElseThrow().operation().blocked()).isFalse();
                SERVER.verify("POST", canonicalEndpoint + "/merge_requests", 0);
                SERVER.verify("PUT", canonicalEndpoint + "/merge_requests/7/merge", 1);
                assertThat(SERVER.writeCount()).isEqualTo(1);
            } finally {
                gate.releaseAll();
                workers.shutdownNow();
                assertThat(workers.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    @Test
    void mergeObservationReadBeforeFinishCannotCommitAfterItsVersionChanges() throws Exception {
        String runId = mergeIntentReadyRun();
        var claim = taskRuns.claimNextTask(runId).orElseThrow();
        assertThat(claim.actionType()).isEqualTo(ActionType.TRY_MERGE);
        Object evidence = AopTestUtils.getUltimateTargetObject(context.getBean(
                io.releasehub.infrastructure.git.GitRemoteEvidenceAdapter.class));
        var observationHttp = (RestTemplate) ReflectionTestUtils.getField(evidence, "http");
        assertThat(observationHttp).isNotNull();
        var originalInterceptors = List.copyOf(observationHttp.getInterceptors());
        var readEntered = new java.util.concurrent.CountDownLatch(1);
        var releaseRead = new java.util.concurrent.CountDownLatch(1);
        var firstRead = new java.util.concurrent.atomic.AtomicBoolean(true);
        var interceptors = new java.util.ArrayList<>(originalInterceptors);
        interceptors.add((request, body, execution) -> {
            var response = execution.execute(request, body);
            if (request.getMethod() == org.springframework.http.HttpMethod.GET
                    && request.getURI().getRawPath().equals(canonicalEndpoint + "/merge_requests/7")
                    && firstRead.compareAndSet(true, false)) {
                holdFixtureResponse(response, readEntered, releaseRead);
            }
            return response;
        });
        var workers = java.util.concurrent.Executors.newFixedThreadPool(2);
        try (var gate = new MergeIntentGate(false, true)) {
            try {
                observationHttp.setInterceptors(interceptors);
                var sender = workers.submit(() -> {
                    try (var scope = GitOperationContext.withTask(claim)) {
                        return factory.getAdapter(GitProvider.GITLAB).tryMergeRequest(repo.getCloneUrl(), "fixture-token",
                                checkpoint(claim), "Merge feature into main");
                    }
                });
                assertThat(gate.entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                gate.release.countDown();
                assertThat(gate.responseEntered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                String operationId = gate.operationId.get();
                var before = journal.find(operationId).orElseThrow();
                var originalRun = jdbc.queryForMap("select * from run where id=?", runId);
                var originalItem = jdbc.queryForMap("select * from run_item where id=?", claim.itemId());
                var originalTasks = jdbc.queryForList("select * from run_task where run_id=? order by task_order", runId);
                var originalSteps = jdbc.queryForList("select * from run_step where run_item_id=? order by task_id", claim.itemId());
                var originalMetadata = mergeNotSentMetadata(claim.itemId());
                assertThat(before.events()).extracting(GitOperationJournalPort.Event::type)
                        .containsExactly("INTENT_RECORDED", "SEND_STARTED");
                assertThat(before.observations()).isEmpty();

                SERVER.reply("GET", canonicalEndpoint + "/merge_requests/7", 200, mergedRequest());
                int readStart = SERVER.requests.size();
                var reader = workers.submit(() -> service.reconcile(operationId, "late-merge-observer",
                        UUID.randomUUID().toString(), before.operation().version()));
                assertThat(readEntered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                gate.releaseResponse.countDown();
                var result = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> sender.get(5, java.util.concurrent.TimeUnit.SECONDS));
                assertThat(result.status()).isEqualTo(io.releasehub.domain.run.MergeStatus.SUCCESS);
                var applied = journal.find(operationId).orElseThrow();
                var appliedRow = jdbc.queryForMap("select * from git_operation where id=?", operationId);
                var appliedGuard = jdbc.queryForMap("select * from repo_execution_guard where remote_key=?", gate.command.get().remoteKey());
                assertThat(applied.operation().state()).isEqualTo("APPLIED");
                assertThat(applied.operation().version()).isGreaterThan(before.operation().version());
                assertThat(applied.events()).extracting(GitOperationJournalPort.Event::type)
                        .containsExactly("INTENT_RECORDED", "SEND_STARTED", "APPLIED");
                assertThat(applied.observations()).isEmpty();
                assertTaskStatus(claim, "RUNNING");
                assertThat(jdbc.queryForObject("select count(*) from run_step where task_id=?", Integer.class, claim.fence().taskId()))
                        .isZero();

                // Finish is the only intervening writer; completeTask must remain after this stale observation.
                releaseRead.countDown();
                var failure = org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.ExecutionException.class,
                        () -> reader.get(5, java.util.concurrent.TimeUnit.SECONDS));
                assertThat(failure.getCause()).isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(io.releasehub.common.exception.ErrorCode.GIT_OPERATION_STALE));
                var after = journal.find(operationId).orElseThrow();
                assertThat(after.observations()).isEmpty();
                assertThat(after.events()).isEqualTo(applied.events());
                assertThat(jdbc.queryForMap("select * from git_operation where id=?", operationId)).isEqualTo(appliedRow);
                assertThat(jdbc.queryForMap("select * from repo_execution_guard where remote_key=?", gate.command.get().remoteKey()))
                        .isEqualTo(appliedGuard);
                assertThat(jdbc.queryForMap("select * from run where id=?", runId)).isEqualTo(originalRun);
                assertThat(jdbc.queryForMap("select * from run_item where id=?", claim.itemId())).isEqualTo(originalItem);
                assertThat(jdbc.queryForList("select * from run_task where run_id=? order by task_order", runId)).isEqualTo(originalTasks);
                assertThat(jdbc.queryForList("select * from run_step where run_item_id=? order by task_id", claim.itemId())).isEqualTo(originalSteps);
                assertThat(mergeNotSentMetadata(claim.itemId())).isEqualTo(originalMetadata);
                assertThat(SERVER.requests.subList(readStart, SERVER.requests.size()))
                        .extracting(GitLabHttpFixture.Request::method).containsExactly("GET", "GET");
                assertThat(SERVER.requests.subList(readStart, SERVER.requests.size()))
                        .extracting(GitLabHttpFixture.Request::path).containsExactly(endpoint, canonicalEndpoint + "/merge_requests/7");

                mergeIntentAssertSender(operationId, claim.fence());
                var completed = taskRuns.completeTask(claim, taskStep(claim, RunItemResult.MERGED), RunPort.mrResultUpdates(result));
                assertThat(completed.getExecutionStatus()).isEqualTo(io.releasehub.domain.run.RunExecutionStatus.DONE);
                assertTaskStatus(claim, "COMPLETED");
                assertThat(journal.find(operationId).orElseThrow().operation().blocked()).isFalse();
                SERVER.verify("POST", canonicalEndpoint + "/merge_requests", 0);
                SERVER.verify("PUT", canonicalEndpoint + "/merge_requests/7/merge", 1);
                assertThat(SERVER.writeCount()).isEqualTo(1);
            } finally {
                gate.releaseAll();
                releaseRead.countDown();
                workers.shutdownNow();
                try {
                    assertThat(workers.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                } finally {
                    observationHttp.setInterceptors(originalInterceptors);
                }
            }
        }
    }

    @Test
    void mergeObservationFoundAtRetargetedMrKeepsTheRealUnknownResult() throws Exception {
        var run = prepareMrMergeRun(false);
        String runId = run.getId().value();
        String itemId = run.getItems().getFirst().getId().value();
        var response = mrMergeResponse(run);
        response.remove("merge_commit_sha");
        SERVER.reply("PUT", canonicalEndpoint + "/merge_requests/7/merge", 200, response.toString());
        assertThat(taskService.executeNextTask(runId)).isTrue();
        assertUnconfirmedMrMerge(run, false);
        var operation = journal.listByRun(runId, 0, 20).stream()
                .filter(value -> value.action().equals("MERGE_MR")).findFirst().orElseThrow();
        var before = journal.find(operation.id()).orElseThrow();
        var originalIdentity = mergeIntentIdentity(operation.id());
        var originalRun = jdbc.queryForMap("select * from run where id=?", runId);
        var originalItem = jdbc.queryForMap("select * from run_item where id=?", itemId);
        var originalTasks = jdbc.queryForList("select * from run_task where run_id=? order by task_order", runId);
        var originalSteps = jdbc.queryForList("select * from run_step where run_item_id=? order by task_id", itemId);
        var originalMetadata = mergeNotSentMetadata(itemId);
        var originalGuard = jdbc.queryForMap("select * from repo_execution_guard where remote_key=?", operation.remoteKey());
        assertThat(before.events()).extracting(GitOperationJournalPort.Event::type)
                .containsExactly("INTENT_RECORDED", "SEND_STARTED", "UNKNOWN");
        assertThat(before.observations()).isEmpty();

        // A current remote observation may have a different target; it is not the original business checkpoint.
        var remote = mrMergeResponse(run).put("target_branch", "retargeted").putNull("merge_commit_sha");
        assertThat(remote.path("target_branch").asText()).isNotEqualTo(originalMetadata.get("git.targetBranch"));
        SERVER.reply("GET", canonicalEndpoint + "/merge_requests/7", 200, remote.toString());
        int readStart = SERVER.requests.size();
        var observed = service.reconcile(operation.id(), "unknown-merge-observer", UUID.randomUUID().toString(), operation.version());

        assertThat(observed.status()).isEqualTo("FOUND");
        var after = journal.find(operation.id()).orElseThrow();
        assertThat(after.operation().version()).isGreaterThan(operation.version());
        assertThat(after.operation().state()).isEqualTo("UNKNOWN");
        assertThat(after.operation().reason()).isEqualTo(before.operation().reason());
        assertThat(after.operation().blocked()).isTrue();
        assertThat(after.events()).isEqualTo(before.events());
        assertThat(after.observations()).containsExactly(observed);
        assertThat(mergeIntentIdentity(operation.id())).isEqualTo(originalIdentity);
        assertThat(jdbc.queryForMap("select * from run where id=?", runId)).isEqualTo(originalRun);
        assertThat(jdbc.queryForMap("select * from run_item where id=?", itemId)).isEqualTo(originalItem);
        assertThat(jdbc.queryForList("select * from run_task where run_id=? order by task_order", runId)).isEqualTo(originalTasks);
        assertThat(jdbc.queryForList("select * from run_step where run_item_id=? order by task_id", itemId)).isEqualTo(originalSteps);
        assertThat(mergeNotSentMetadata(itemId)).isEqualTo(originalMetadata);
        assertThat(jdbc.queryForMap("select * from repo_execution_guard where remote_key=?", operation.remoteKey())).isEqualTo(originalGuard);
        assertThat(SERVER.requests.subList(readStart, SERVER.requests.size()))
                .extracting(GitLabHttpFixture.Request::method).containsExactly("GET", "GET");
        assertThat(SERVER.requests.subList(readStart, SERVER.requests.size()))
                .extracting(GitLabHttpFixture.Request::path).containsExactly(endpoint, canonicalEndpoint + "/merge_requests/7");
        SERVER.verify("POST", canonicalEndpoint + "/merge_requests", 1);
        SERVER.verify("PUT", canonicalEndpoint + "/merge_requests/7/merge", 1);
        assertThat(SERVER.writeCount()).isEqualTo(2);

        var evidence = new com.fasterxml.jackson.databind.ObjectMapper().readTree(observed.evidenceJson());
        org.assertj.core.api.SoftAssertions.assertSoftly(soft -> {
            for (String field : List.of("project_id", "source_project_id", "target_project_id", "iid")) {
                soft.assertThat(evidence.path(field).isIntegralNumber()).as(field + " keeps the provider JSON type").isTrue();
                soft.assertThat(evidence.path(field)).as(field + " keeps the observed identity").isEqualTo(remote.path(field));
            }
            for (String field : List.of("source_branch", "target_branch", "sha", "state", "web_url")) {
                soft.assertThat(evidence.path(field).isTextual()).as(field + " keeps the provider JSON type").isTrue();
                soft.assertThat(evidence.path(field)).as(field + " keeps the current remote fact").isEqualTo(remote.path(field));
            }
            soft.assertThat(evidence.has("merge_commit_sha")).isTrue();
            soft.assertThat(evidence.path("merge_commit_sha").isNull()).isTrue();
        });
    }

    private Map<String, String> mergeNotSentMetadata(String itemId) {
        Map<String, String> values = new LinkedHashMap<>();
        jdbc.query("select metadata_key,metadata_value from run_item_metadata where run_item_id=?",
                row -> { values.put(row.getString(1), row.getString(2)); }, itemId);
        return values;
    }

    private void mergeNotSentObservation(String observation) {
        String route = canonicalEndpoint + "/merge_requests/7";
        String opened = mergeRequest("opened");
        switch (observation) {
            case "contained_missing_mr", "contained_changed_mr" -> {
                SERVER.reply("GET", canonicalEndpoint + "/repository/commits/" + sha + "/refs", 200,
                        "[{\"type\":\"branch\",\"name\":\"main\"}]");
                SERVER.reply("GET", route, observation.equals("contained_missing_mr") ? 404 : 200,
                        observation.equals("contained_missing_mr") ? "{}" : mergeRequest("closed").replace(sha, "f".repeat(40)));
            }
            case "conflict" -> SERVER.reply("GET", route, 200, opened.replace("\"mergeable\"", "\"conflict\""));
            case "approval" -> SERVER.reply("GET", route, 200, opened.replace("\"mergeable\"", "\"not_approved\""));
            case "pipeline_pending" -> SERVER.reply("GET", route, 200, opened.replace("\"mergeable\"", "\"ci_still_running\""));
            case "pipeline_failed" -> SERVER.reply("GET", route, 200,
                    opened.substring(0, opened.length() - 1).replace("\"mergeable\"", "\"ci_must_pass\"")
                            + ",\"head_pipeline\":{\"status\":\"failed\"}}");
            case "forbidden" -> SERVER.reply("GET", route, 403, "{\"message\":\"Forbidden\"}");
            case "unavailable" -> SERVER.reply("GET", route, 503, "{}");
            case "closed" -> SERVER.reply("GET", route, 200, mergeRequest("closed"));
            case "source_changed" -> SERVER.reply("GET", route, 200, opened.replace(sha, "f".repeat(40)));
            case "merged_target_reset" -> SERVER.reply("GET", route, 200, mergedRequest());
            default -> throw new AssertionError(observation);
        }
    }

    private RunItemResult mergeNotSentResult(String observation) {
        if (observation.startsWith("contained_")) return RunItemResult.ALREADY_MERGED;
        return Set.of("conflict", "approval", "pipeline_pending", "pipeline_failed", "forbidden").contains(observation)
                ? RunItemResult.MERGE_BLOCKED : RunItemResult.UNKNOWN;
    }

    private Map<String, String> mergeNotSentPatch(String observation) {
        Map<String, String> patch = new LinkedHashMap<>();
        patch.put("git.requestId", null);
        patch.put("git.blockReason", switch (observation) {
            case "contained_missing_mr", "contained_changed_mr" -> null;
            case "conflict" -> "CONFLICT";
            case "approval" -> "APPROVAL_REQUIRED";
            case "pipeline_pending" -> "PIPELINE_PENDING";
            case "pipeline_failed" -> "PIPELINE_FAILED";
            case "forbidden" -> "PERMISSION_DENIED";
            default -> "UNKNOWN";
        });
        if (observation.startsWith("contained_")) {
            patch.put("git.mergeSha", null);
            patch.put("git.mrDisposition", "ALREADY_MERGED");
        }
        return patch;
    }

    private void mergeNotSentAssertEvent(String operationId, String taskId, String currentToken, RunItemResult result,
                                         Map<String, String> patch, GitOperationJournalPort.Event originalIntent) throws Exception {
        var detail = journal.find(operationId).orElseThrow();
        assertThat(detail.operation().state()).isEqualTo("NOT_SENT");
        assertThat(detail.operation().reason()).isEqualTo(patch.get("git.blockReason"));
        assertThat(detail.operation().blocked()).isFalse();
        assertThat(detail.events()).extracting(GitOperationJournalPort.Event::type).containsExactly("INTENT_RECORDED", "NOT_SENT");
        assertThat(detail.events().getFirst()).isEqualTo(originalIntent);
        var local = detail.events().getLast();
        assertThat(local.sequence()).isEqualTo(1);
        assertThat(local.attemptId()).isEqualTo(originalIntent.attemptId());
        assertThat(local.reason()).isEqualTo(patch.get("git.blockReason"));
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var expected = json.createObjectNode().put("taskId", taskId).put("claimToken", currentToken).put("result", result.name());
        expected.set("metadata", json.valueToTree(patch));
        assertThat(json.readTree(local.evidenceJson())).isEqualTo(expected);
    }

    private String mergeIntentReadyRun() {
        return mergeIntentReadyRun(false);
    }

    private String mergeIntentReadyRun(boolean followingIteration) {
        var mrClaim = claimedMergeTask(followingIteration);
        readyForNewMerge();
        SERVER.reply("GET", canonicalEndpoint + "/merge_requests", 200, "[" + mergeRequest("opened") + "]");
        var reused = ensureTaskMr(mrClaim);
        assertThat(reused.evidence().disposition()).isEqualTo(GitBranchPort.MrDisposition.MR_REUSED);
        taskRuns.completeTask(mrClaim, taskStep(mrClaim, RunItemResult.MR_REUSED), checkpointMetadata(reused.evidence()));
        SERVER.reply("PUT", canonicalEndpoint + "/merge_requests/7/merge", 200, mergedRequest());
        return mrClaim.runId();
    }

    private Map<String, Object> mergeIntentIdentity(String operationId) {
        return jdbc.queryForMap("select id,repo_id,remote_key,provider,base_url,project_id,action,target_json,request_hash,"
                + "run_id,task_id,claim_token,operator,session_id,attempt_id,generation,created_at from git_operation where id=?",
                operationId);
    }

    private void mergeIntentAssertSender(String operationId, RunPort.TaskFence fence) throws Exception {
        var event = journal.find(operationId).orElseThrow().events().stream()
                .filter(value -> value.sequence() == 1 && value.type().equals("SEND_STARTED")).findFirst().orElseThrow();
        var evidence = new com.fasterxml.jackson.databind.ObjectMapper().readTree(event.evidenceJson());
        assertThat(evidence.path("taskId").isTextual()).isTrue();
        assertThat(evidence.path("taskId").asText()).isEqualTo(fence.taskId());
        assertThat(evidence.path("claimToken").isTextual()).isTrue();
        assertThat(evidence.path("claimToken").asText()).isEqualTo(fence.claimToken());
    }

    /** Pauses after a real Journal transaction commits; never replaces its database decisions. */
    private final class MergeIntentGate implements AutoCloseable {
        private final java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
        private final java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        private final java.util.concurrent.CountDownLatch responseEntered = new java.util.concurrent.CountDownLatch(1);
        private final java.util.concurrent.CountDownLatch releaseResponse = new java.util.concurrent.CountDownLatch(1);
        private final java.util.concurrent.atomic.AtomicReference<GitOperationJournalPort.Command> command =
                new java.util.concurrent.atomic.AtomicReference<>();
        private final java.util.concurrent.atomic.AtomicReference<String> operationId = new java.util.concurrent.atomic.AtomicReference<>();
        private final io.releasehub.infrastructure.git.GitWriteHttpInterceptor interceptor;
        private final RestTemplate http;
        private final List<org.springframework.http.client.ClientHttpRequestInterceptor> originalInterceptors;
        private final GitOperationJournalPort originalJournal;

        private MergeIntentGate(boolean afterSending, boolean holdResponse) {
            interceptor = context.getBean(io.releasehub.infrastructure.git.GitWriteHttpInterceptor.class);
            originalJournal = (GitOperationJournalPort) ReflectionTestUtils.getField(interceptor, "journal");
            assertThat(originalJournal).isSameAs(journal);
            Object target = AopTestUtils.getUltimateTargetObject(factory.getAdapter(GitProvider.GITLAB));
            http = (RestTemplate) ReflectionTestUtils.getField(target, "restTemplate");
            assertThat(http).isNotNull();
            originalInterceptors = List.copyOf(http.getInterceptors());
            var paused = new java.util.concurrent.atomic.AtomicBoolean();
            var decoratedJournal = (GitOperationJournalPort) java.lang.reflect.Proxy.newProxyInstance(
                    GitOperationJournalPort.class.getClassLoader(), new Class<?>[]{GitOperationJournalPort.class},
                    (proxy, method, arguments) -> {
                        Object result;
                        try { result = method.invoke(originalJournal, arguments); }
                        catch (java.lang.reflect.InvocationTargetException error) { throw error.getCause(); }
                        boolean originalBegin = method.getName().equals("begin")
                                && arguments[0] instanceof GitOperationJournalPort.Command value
                                && value.action().equals("MERGE_MR") && command.compareAndSet(null, value);
                        if (originalBegin) operationId.set(((GitOperationJournalPort.Operation) result).id());
                        boolean originalSend = method.getName().equals("sending") && operationId.get() != null
                                && operationId.get().equals(arguments[0]);
                        if ((afterSending ? originalSend : originalBegin) && paused.compareAndSet(false, true)) {
                            entered.countDown();
                            if (!release.await(15, java.util.concurrent.TimeUnit.SECONDS))
                                throw new IllegalStateException("fixture Journal gate timed out");
                        }
                        return result;
                    });
            var interceptors = new java.util.ArrayList<>(originalInterceptors);
            if (holdResponse) interceptors.add((request, body, execution) -> {
                var response = execution.execute(request, body);
                if (request.getMethod() == org.springframework.http.HttpMethod.PUT
                        && request.getURI().getRawPath().equals(canonicalEndpoint + "/merge_requests/7/merge")) {
                    holdFixtureResponse(response, responseEntered, releaseResponse);
                }
                return response;
            });
            ReflectionTestUtils.setField(interceptor, "journal", decoratedJournal);
            http.setInterceptors(interceptors);
        }

        private void expire() {
            var fence = command.get().taskFence();
            assertThat(jdbc.update("update run_task set claim_expires_at=clock_timestamp()-interval '1 second' "
                    + "where id=? and status='RUNNING' and claim_token=?", fence.taskId(), fence.claimToken())).isEqualTo(1);
        }

        private void releaseAll() {
            release.countDown();
            releaseResponse.countDown();
        }

        @Override public void close() {
            releaseAll();
            http.setInterceptors(originalInterceptors);
            ReflectionTestUtils.setField(interceptor, "journal", originalJournal);
        }
    }

    private void expireTaskClaim(RunPort.TaskClaim claim) {
        assertThat(jdbc.update("update run_task set claim_expires_at=clock_timestamp()-interval '1 second' "
                        + "where id=? and status='RUNNING' and claim_token=?",
                claim.fence().taskId(), claim.fence().claimToken())).isEqualTo(1);
    }

    private static void holdFixtureResponse(org.springframework.http.client.ClientHttpResponse response,
            java.util.concurrent.CountDownLatch entered, java.util.concurrent.CountDownLatch release) throws java.io.IOException {
        entered.countDown();
        try {
            if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
                response.close();
                throw new java.io.IOException("fixture response gate timed out");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            response.close();
            throw new java.io.IOException("fixture response gate interrupted", interrupted);
        }
    }

    private String workerTaskStatus(String runId, String itemId, ActionType action) {
        return taskRuns.findTasks(runId).stream().filter(task -> task.itemId().equals(itemId) && task.actionType() == action)
                .findFirst().orElseThrow().status();
    }

    /** 真实受理并领取任务；前置分支事实来自本地 HTTP fixture，不手工构造领取权。 */
    private RunPort.TaskClaim claimedMergeTask() {
        return claimedMergeTask(false);
    }

    private RunPort.TaskClaim claimedMergeTask(boolean followingIteration) {
        String windowId = UUID.randomUUID().toString();
        String iterationKey = "ITER-" + UUID.randomUUID();
        String followingKey = "ITER-NEXT-" + UUID.randomUUID();
        Instant now = Instant.now();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            taskWindows.save(io.releasehub.domain.releasewindow.ReleaseWindow.rehydrate(
                    io.releasehub.domain.releasewindow.ReleaseWindowId.of(windowId), "RW-" + windowId,
                    "任务写证据", "", now, "G001", io.releasehub.domain.releasewindow.ReleaseWindowStatus.DRAFT,
                    now, now, false, null));
            taskIterations.save(io.releasehub.domain.iteration.Iteration.rehydrate(
                    io.releasehub.domain.iteration.IterationKey.of(iterationKey), "任务迭代", "", null, "G001",
                    Set.of(repo.getId()), io.releasehub.domain.iteration.IterationStatus.ACTIVE, now, now));
            taskVersions.saveWithVersion(iterationKey, repo.getId().value(), "1.0.0", "1.1.0-SNAPSHOT", "1.1.0",
                    "feature", "SYSTEM", now, io.releasehub.domain.iteration.BranchCreationMode.AUTO);
            taskBindings.attach(io.releasehub.domain.releasewindow.ReleaseWindowId.of(windowId),
                    io.releasehub.domain.iteration.IterationKey.of(iterationKey), now);
            taskBindings.updateReleaseBranch(windowId, iterationKey, "main", now);
            if (followingIteration) {
                taskIterations.save(io.releasehub.domain.iteration.Iteration.rehydrate(
                        io.releasehub.domain.iteration.IterationKey.of(followingKey), "后继任务迭代", "", null, "G001",
                        Set.of(repo.getId()), io.releasehub.domain.iteration.IterationStatus.ACTIVE, now, now));
                taskVersions.saveWithVersion(followingKey, repo.getId().value(), "1.0.0", "1.1.0-SNAPSHOT", "1.1.0",
                        "feature-next", "SYSTEM", now, io.releasehub.domain.iteration.BranchCreationMode.AUTO);
                taskBindings.attach(io.releasehub.domain.releasewindow.ReleaseWindowId.of(windowId),
                        io.releasehub.domain.iteration.IterationKey.of(followingKey), now.plusMillis(1));
                taskBindings.updateReleaseBranch(windowId, followingKey, "main", now);
            }
        });
        var admission = taskPlans.admit(windowId, List.of(repo.getId().value()),
                followingIteration ? List.of(iterationKey, followingKey) : List.of(iterationKey),
                true, "fixture-task-operator", UUID.randomUUID().toString());
        var run = taskRuns.findById(admission.runId()).orElseThrow();
        for (ActionType action : List.of(ActionType.ENSURE_FEATURE, ActionType.ENSURE_RELEASE)) {
            var claim = taskRuns.claimNextTask(run.getId().value()).orElseThrow();
            assertThat(claim.actionType()).isEqualTo(action);
            String branch = action == ActionType.ENSURE_FEATURE ? "feature" : "main";
            SERVER.reply("GET", canonicalEndpoint + "/repository/branches/" + branch, 200,
                    "{\"name\":\"" + branch + "\",\"commit\":{\"id\":\"" + sha + "\"}}");
            GitBranchPort.BranchStatus observed;
            try (var scope = GitOperationContext.withTask(claim)) {
                observed = factory.getAdapter(GitProvider.GITLAB).getBranchStatus(repo.getCloneUrl(), "fixture-token", branch);
            }
            assertThat(observed.exists()).isTrue();
            assertThat(observed.latestCommit()).isEqualTo(sha);
            assertThat(observed.projectId()).isEqualTo(String.valueOf(project));
            taskRuns.completeTask(claim, taskStep(claim, RunItemResult.ALREADY_EXISTS),
                    Map.of("git.projectId", observed.projectId()));
        }
        var claim = taskRuns.claimNextTask(run.getId().value()).orElseThrow();
        assertThat(claim.actionType()).isEqualTo(ActionType.ENSURE_MR);
        return claim;
    }

    private GitWriteResult<GitBranchPort.MrEvidence> ensureTaskMr(RunPort.TaskClaim claim) {
        try (var scope = GitOperationContext.withTask(claim)) {
            return factory.getAdapter(GitProvider.GITLAB).ensureMergeRequest(
                    repo.getCloneUrl(), "fixture-token", "feature", "main", "fixture MR");
        }
    }

    private RunStep taskStep(RunPort.TaskClaim claim, RunItemResult result) {
        return new RunStep(claim.actionType(), result, claim.startedAt(), claim.startedAt().plusMillis(1), "fixture task result");
    }

    private void assertTaskStatus(RunPort.TaskClaim claim, String status) {
        assertThat(taskRuns.findTasks(claim.runId()).stream()
                .filter(task -> task.taskId().equals(claim.fence().taskId())).findFirst().orElseThrow().status()).isEqualTo(status);
    }

    private Map<String, String> checkpointMetadata(GitBranchPort.MrEvidence evidence) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("git.projectId", evidence.projectId());
        values.put("git.sourceBranch", evidence.sourceBranch());
        values.put("git.targetBranch", evidence.targetBranch());
        values.put("git.sourceSha", evidence.sourceSha());
        values.put("git.mrDisposition", evidence.disposition().name());
        if (evidence.iid() != null) values.put("git.mrIid", evidence.iid());
        if (evidence.url() != null) values.put("git.mrUrl", evidence.url());
        if (evidence.mergeSha() != null) values.put("git.mergeSha", evidence.mergeSha());
        return values;
    }

    private GitBranchPort.MrEvidence checkpoint(RunPort.TaskClaim claim) {
        var facts = claim.metadata();
        return new GitBranchPort.MrEvidence(facts.get("git.projectId"), facts.get("git.sourceBranch"),
                facts.get("git.targetBranch"), facts.get("git.mrIid"), facts.get("git.mrUrl"),
                facts.get("git.sourceSha"), facts.get("git.mergeSha"),
                GitBranchPort.MrDisposition.valueOf(facts.get("git.mrDisposition")), null);
    }

    private String mergedRequest() {
        String opened = mergeRequest("merged");
        return opened.substring(0, opened.length() - 1) + ",\"merge_commit_sha\":\"" + "b".repeat(40) + "\"}";
    }

    private GitBranchPort.MergeResult merge() {
        return factory.getAdapter(GitProvider.GITLAB).mergeBranch(repo.getCloneUrl(),"fixture-token","feature","main","fixture");
    }
    private void readyForNewMerge() {
        sourceBranch();
        SERVER.reply("GET",canonicalEndpoint+"/merge_requests",200,"[]");
        SERVER.reply("GET",canonicalEndpoint+"/repository/commits/"+sha+"/refs",200,"[]");
        SERVER.reply("GET",canonicalEndpoint+"/merge_requests/7",200,mergeRequest("opened"));
    }
    private void assertNextWriteBlocked(int previousWrites) {
        assertThat(SERVER.writeCount()).isEqualTo(previousWrites);
        branchSuccess();
        assertThatThrownBy(this::write).isInstanceOf(BusinessException.class);
        assertThat(SERVER.writeCount()).isEqualTo(previousWrites);
        assertThat(journal.list(repo.getId().value(),0,20)).hasSize(previousWrites)
                .allMatch(GitOperationJournalPort.Operation::blocked);
    }
    private void assertNoNamespaceWrites() {
        assertThat(SERVER.requests).noneMatch(request->GitLabHttpFixture.isWrite(request.method())
                &&request.path().startsWith(endpoint+"/"));
    }

    private void sourceBranch() {
        SERVER.reply("GET",canonicalEndpoint+"/repository/branches/feature",200,
                "{\"name\":\"feature\",\"commit\":{\"id\":\""+sha+"\"}}");
    }
    private String pipeline(String ref,String commitSha) {
        return "{\"project_id\":"+project+",\"id\":321,\"ref\":\""+ref+"\",\"sha\":\""+commitSha
                +"\",\"web_url\":\""+SERVER.baseUrl()+"/acme/project/-/pipelines/321\"}";
    }
    private String mergeRequest(String state) {
        return "{\"iid\":7,\"project_id\":"+project+",\"source_project_id\":"+project+",\"target_project_id\":"+project
                +",\"source_branch\":\"feature\",\"target_branch\":\"main\",\"state\":\""+state
                +"\",\"sha\":\""+sha+"\",\"web_url\":\""+SERVER.baseUrl()+"/acme/project/-/merge_requests/7\",\"detailed_merge_status\":\"mergeable\"}";
    }


}
