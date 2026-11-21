package io.releasehub.bootstrap.api;

import io.releasehub.application.gitoperation.*;
import io.releasehub.common.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @ActiveProfiles("e2e") @org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class GitOperationJournalIT {
    private static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:18.1")
            .withDatabaseName("git_journal_test").withUsername("journal_test").withPassword("isolated-journal-db");
    static { DB.start(); }
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",DB::getJdbcUrl);r.add("spring.datasource.username",DB::getUsername);r.add("spring.datasource.password",DB::getPassword);
    }
    @AfterAll static void stopDatabase(){DB.stop();}
    @Autowired GitOperationJournalPort journal;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired io.releasehub.application.repo.CodeRepositoryPort repos;
    @Autowired io.releasehub.application.settings.SettingsPort settings;
    private String repoId;
    @BeforeEach void repositoryFixture() {
        settings.saveGitLab(new io.releasehub.application.settings.SettingsPort.SettingsGitLab("https://gitlab.invalid","fixture"));
        var repo=io.releasehub.domain.repo.CodeRepository.create("证据仓库","https://gitlab.invalid/acme/repo.git","main","G001",io.releasehub.domain.repo.RepoType.SERVICE,false,java.time.Instant.now());
        repos.save(repo);repoId=repo.getId().value();
    }
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;

    private GitOperationJournalPort.Command command(String remote,String session) {
        return command(remote,session,"https://gitlab.invalid");
    }
    private GitOperationJournalPort.Command command(String remote,String session,String server) {
        return new GitOperationJournalPort.Command(UUID.randomUUID().toString(),repoId,GitOperationJournalPort.mappingHash("GITLAB","https://gitlab.invalid/acme/repo.git"),GitOperationJournalPort.remoteKey("GITLAB",server,remote),"GITLAB",
                server,remote,"CREATE_BRANCH","{}","a".repeat(64),null,"tester",session,null);
    }
    private String remote(){return Long.toUnsignedString(UUID.randomUUID().getLeastSignificantBits());}
    @Test void shouldRejectStaleServerMappingBeforeAdmittingOperation() {
        // 过期命令中的服务地址与当前持久配置不同，其他测试留下的 guard 不影响这个输入。
        var c=command(remote(),UUID.randomUUID().toString(),"https://changed.invalid");
        assertThatThrownBy(()->journal.begin(c)).isInstanceOf(BusinessException.class);
        assertThat(journal.find(c.operationId())).isEmpty();
    }
    @Test void shouldExposeEvidenceOnlyAfterAuthentication() throws Exception {
        var c=command(remote(),UUID.randomUUID().toString());String id=journal.begin(c);journal.sending(id);
        journal.finish(id,GitWriteResult.Outcome.UNKNOWN,"{}","RESPONSE_LOST");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/git-operations/"+id))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        var login=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"username\":\"admin\",\"password\":\"admin\"}"))
                .andReturn().getResponse().getContentAsString();
        String token=json.readTree(login).path("data").path("token").asText();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/git-operations/"+id).header("Authorization","Bearer "+token))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.operation.state").value("UNKNOWN"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.events.length()").value(3));
        long version=journal.find(id).orElseThrow().operation().version();
        var observed=journal.observe(id,"admin","api-observation-key",version,GitReadResult.Status.FOUND,"{}");
        var request=org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/git-operations/"+id+"/reconcile")
                .header("Authorization","Bearer "+token).header("Idempotency-Key","api-observation-key")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"expectedStateVersion\":"+version+"}");
        mvc.perform(request).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.id").value(observed.id()));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/git-operations/"+id+"/reconcile")
                .header("Authorization","Bearer "+token).header("Idempotency-Key","api-observation-key")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"expectedStateVersion\":"+(version+1)+"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());
    }
    @Test void shouldPreserveExternalEvidenceAfterOuterRollback() {
        var c=command(remote(),UUID.randomUUID().toString());
        assertThatThrownBy(()->new TransactionTemplate(transactions).executeWithoutResult(tx->{
            String id=journal.begin(c);journal.sending(id);journal.finish(id,GitWriteResult.Outcome.APPLIED,"{\"sha\":\"abc\"}",null);
            throw new IllegalStateException("模拟业务回滚");
        })).isInstanceOf(IllegalStateException.class);
        journal.completeSession(c.sessionId(),false);
        var detail=journal.find(c.operationId()).orElseThrow();
        assertThat(detail.operation().state()).isEqualTo("NEEDS_REVIEW");
        assertThat(detail.operation().blocked()).isTrue();
        assertThat(detail.events()).extracting(GitOperationJournalPort.Event::type)
                .containsExactly("INTENT_RECORDED","SEND_STARTED","APPLIED","LOCAL_RESULT_UNCONFIRMED");
    }
    @Test void shouldAdmitOnlyOneConcurrentWriterForRemoteAliases() throws Exception {
        String key=remote();var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> action=()->{gate.await();try{journal.begin(command(key,UUID.randomUUID().toString()));return true;}catch(BusinessException e){return false;}};
            var one=pool.submit(action);var two=pool.submit(action);gate.countDown();
            assertThat(java.util.List.of(one.get(10,TimeUnit.SECONDS),two.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
    }
    @Test void shouldReleaseOnlyConfirmedSessionAndPermitDifferentRepository() {
        var c=command(remote(),UUID.randomUUID().toString());String id=journal.begin(c);journal.sending(id);
        journal.finish(id,GitWriteResult.Outcome.APPLIED,"{}",null);journal.completeSession(c.sessionId(),true);
        assertThat(journal.find(id).orElseThrow().operation().blocked()).isFalse();
        assertThatCode(()->journal.begin(command(c.projectId(),UUID.randomUUID().toString()))).doesNotThrowAnyException();
        repositoryFixture();
        assertThatCode(()->journal.begin(command(remote(),UUID.randomUUID().toString()))).doesNotThrowAnyException();
    }
    @Test void shouldRejectOldGenerationWithoutOverwritingEvidence() {
        var c=command(remote(),UUID.randomUUID().toString());String id=journal.begin(c);journal.sending(id);
        jdbc.update("update repo_execution_guard set generation=generation+1 where remote_key=?",c.remoteKey());
        assertThatThrownBy(()->journal.finish(id,GitWriteResult.Outcome.APPLIED,"{}",null)).isInstanceOf(BusinessException.class);
        assertThat(journal.find(id).orElseThrow().events()).hasSize(2);
    }
    @Test void shouldKeepUnknownBlockedAndMakeObservationsIdempotent() {
        var c=command(remote(),UUID.randomUUID().toString());String id=journal.begin(c);journal.sending(id);
        journal.finish(id,GitWriteResult.Outcome.UNKNOWN,"{}","RESPONSE_LOST");journal.completeSession(c.sessionId(),true);
        long version=journal.find(id).orElseThrow().operation().version();
        var first=journal.observe(id,"tester","same-key",version,GitReadResult.Status.FOUND,"{}");
        assertThat(journal.observe(id,"tester","same-key",version,GitReadResult.Status.NOT_FOUND,"{}")).isEqualTo(first);
        assertThatThrownBy(()->journal.observe(id,"tester","same-key",version+1,GitReadResult.Status.FOUND,"{}")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->journal.observe(id,"tester","new-key",version,GitReadResult.Status.FOUND,"{}")).isInstanceOf(BusinessException.class);
        assertThat(journal.find(id).orElseThrow().operation().blocked()).isTrue();
        assertThat(journal.find(id).orElseThrow().operation().state()).isEqualTo("UNKNOWN");
    }
    @Test void shouldKeepSendStartedWhenResultCommitFails() {
        var c=command(remote(),UUID.randomUUID().toString());String id=journal.begin(c);journal.sending(id);
        jdbc.execute("alter table git_operation add constraint fixture_reject_applied check (id <> '"+id+"' or state <> 'APPLIED')");
        try {
            assertThatThrownBy(()->journal.finish(id,GitWriteResult.Outcome.APPLIED,"{}",null)).isInstanceOf(RuntimeException.class);
            assertThat(journal.find(id).orElseThrow().operation().state()).isEqualTo("SEND_STARTED");
            assertThat(journal.find(id).orElseThrow().events()).hasSize(2);
            journal.completeSession(c.sessionId(),true);
            assertThat(journal.find(id).orElseThrow().operation().blocked()).isTrue();
        } finally {jdbc.execute("alter table git_operation drop constraint fixture_reject_applied");}
    }
}
