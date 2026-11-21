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
        assertReviewRequired(1);
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
        assertReviewRequired(2);
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
        assertThat(unresolved.getExecutionStatus().name()).isEqualTo("NEEDS_REVIEW");
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
    void committedIntentWinsTheLockAndEveryOperationStatePreventsTakeover() throws Exception {
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
                String id = actualJournal.begin(command);
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
        for (String operationToken : List.of(claim.fence().claimToken(), UUID.randomUUID().toString())) {
            for (String state : List.of("INTENT_RECORDED", "SEND_STARTED", "APPLIED", "REJECTED", "UNKNOWN", "NEEDS_REVIEW")) {
                // 将实际 begin 留下的 operation 投影为各历史事实；资格不能按状态或当前 token 忽略它。
                jdbc.update("update git_operation set state=?,claim_token=? where id=?", state, operationToken, operationId);
                var operation = jdbc.queryForMap("select * from git_operation where id=?", operationId);
                assertThat(taskRuns.findTaskCandidateRunIds(null, 1000)).doesNotContain(claim.runId());
                assertThat(taskRuns.claimNextTask(claim.runId())).isEmpty();
                assertThat(jdbc.queryForObject("select claim_token from run_task where id=?", String.class,
                        claim.fence().taskId())).isEqualTo(claim.fence().claimToken());
                assertThat(jdbc.queryForMap("select * from git_operation where id=?", operationId)).isEqualTo(operation);
                assertThat(jdbc.queryForMap("select * from repo_execution_guard where remote_key=?", remoteKey)).isEqualTo(guard);
            }
        }
        assertThat(SERVER.writeCount()).isZero();
        assertTaskStatus(claim, "RUNNING");
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
        String windowId = UUID.randomUUID().toString();
        String iterationKey = "ITER-" + UUID.randomUUID();
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
        });
        var admission = taskPlans.admit(windowId, List.of(repo.getId().value()), List.of(iterationKey),
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
    private void assertReviewRequired(int count) {
        var operations=journal.list(repo.getId().value(),0,20);
        assertThat(operations).hasSize(count)
                .allMatch(operation->operation.state().equals("NEEDS_REVIEW")&&operation.blocked());
        for(var operation:operations) {
            assertThat(journal.find(operation.id()).orElseThrow().events())
                    .anyMatch(event->event.type().equals("APPLIED"))
                    .anyMatch(event->event.type().equals("LOCAL_RESULT_UNCONFIRMED"));
        }
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
