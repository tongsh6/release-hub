package io.releasehub.bootstrap.api;

import io.releasehub.application.gitoperation.*;
import io.releasehub.application.port.out.*;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.application.settings.SettingsPort;
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
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @ActiveProfiles("e2e")
class GitWriteSafetyIT {
    private static final PostgreSQLContainer<?> DB=new PostgreSQLContainer<>("postgres:18.1")
            .withDatabaseName("git_safety_test").withUsername("safety_test").withPassword("isolated-safety-db");
    private static final FixtureServer SERVER=new FixtureServer();
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
        SERVER.reply("GET",endpoint+"/merge_requests",200,"["+mergeRequest("opened")+"]");
        SERVER.reply("GET",endpoint+"/merge_requests/7",200,mergeRequest("opened"));
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
        SERVER.reply("GET",endpoint+"/merge_requests",200,"[]");
        SERVER.reply("GET",endpoint+"/repository/commits/"+sha+"/refs",200,"[]");
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
        SERVER.reply("GET",endpoint+"/repository/branches/feature",200,"{}");

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
        SERVER.reply("POST",canonicalEndpoint+"/pipeline",201,"{\"id\":321}");
        SERVER.reply("POST",endpoint+"/pipeline",201,"{\"id\":321}");

        assertThat(factory.getAdapter(GitProvider.GITLAB).triggerPipeline(repo.getCloneUrl(),"fixture-token",ref))
                .isEqualTo("321");
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

    @Test void shouldKeepPipelineWithoutResponseIdBlocked() {
        SERVER.reply("POST",canonicalEndpoint+"/pipeline",201,"{}");
        assertThat(factory.getAdapter(GitProvider.GITLAB).triggerPipeline(repo.getCloneUrl(),"fixture-token","main")).isNull();
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

    private GitBranchPort.MergeResult merge() {
        return factory.getAdapter(GitProvider.GITLAB).mergeBranch(repo.getCloneUrl(),"fixture-token","feature","main","fixture");
    }
    private void readyForNewMerge() {
        sourceBranch();
        SERVER.reply("GET",endpoint+"/merge_requests",200,"[]");
        SERVER.reply("GET",endpoint+"/repository/commits/"+sha+"/refs",200,"[]");
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
        assertThat(SERVER.requests).noneMatch(request->FixtureServer.isWrite(request.method())
                &&request.path().startsWith(endpoint+"/"));
    }

    private void sourceBranch() {
        SERVER.reply("GET",endpoint+"/repository/branches/feature",200,
                "{\"name\":\"feature\",\"commit\":{\"id\":\""+sha+"\"}}");
    }
    private String mergeRequest(String state) {
        return "{\"iid\":7,\"project_id\":"+project+",\"source_project_id\":"+project+",\"target_project_id\":"+project
                +",\"source_branch\":\"feature\",\"target_branch\":\"main\",\"state\":\""+state
                +"\",\"sha\":\""+sha+"\",\"web_url\":\""+SERVER.baseUrl()+"/acme/project/-/merge_requests/7\",\"detailed_merge_status\":\"mergeable\"}";
    }

    /** 使用 JDK 自带 HTTP fixture，避免为 bootstrap 新增测试依赖。 */
    private static final class FixtureServer {
        private record Response(int status,String body) {}
        private record Request(String method,String path,String query,String body) {}
        private final java.util.Map<String,Response> responses=new java.util.concurrent.ConcurrentHashMap<>();
        private final java.util.List<Request> requests=new java.util.concurrent.CopyOnWriteArrayList<>();
        private final com.sun.net.httpserver.HttpServer server;
        private FixtureServer() {
            try {
                server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
                server.createContext("/",exchange->{
                    String method=exchange.getRequestMethod();String path=exchange.getRequestURI().getRawPath();
                    requests.add(new Request(method,path,exchange.getRequestURI().getRawQuery(),new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8)));
                    Response response=responses.getOrDefault(method+" "+path,new Response(404,"{}"));
                    byte[] body=response.body().getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type","application/json");
                    exchange.sendResponseHeaders(response.status(),body.length);
                    try(var output=exchange.getResponseBody()){output.write(body);}
                });
            }catch(java.io.IOException e){throw new IllegalStateException(e);}
        }
        void start(){server.start();}
        void stop(){server.stop(0);}
        String baseUrl(){return "http://127.0.0.1:"+server.getAddress().getPort();}
        void resetAll(){responses.clear();requests.clear();}
        void resetRequests(){requests.clear();}
        void reply(String method,String path,int status,String body){responses.put(method+" "+path,new Response(status,body));}
        void verify(String method,String path,int count){assertThat(requests.stream().filter(r->r.method().equals(method)&&r.path().equals(path)).count()).isEqualTo(count);}
        private static boolean isWrite(String method){return java.util.Set.of("POST","PUT","PATCH","DELETE").contains(method);}
        long writeCount(){return requests.stream().filter(request->isWrite(request.method())).count();}
        String query(String method,String path){return requests.stream().filter(request->request.method().equals(method)&&request.path().equals(path)).findFirst().orElseThrow().query();}
        com.fasterxml.jackson.databind.JsonNode body(String method,String path) {
            try{return new com.fasterxml.jackson.databind.ObjectMapper().readTree(requests.stream().filter(r->r.method().equals(method)&&r.path().equals(path)).findFirst().orElseThrow().body());}
            catch(java.io.IOException e){throw new IllegalStateException(e);}
        }
    }
}
