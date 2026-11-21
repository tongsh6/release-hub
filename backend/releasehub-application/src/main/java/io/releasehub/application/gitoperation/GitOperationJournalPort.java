package io.releasehub.application.gitoperation;

import io.releasehub.application.run.RunPort;
import io.releasehub.domain.run.RunItemResult;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** HTTP 意图、发送和响应事实独立提交；任务确认必须加入任务结果的同一事务。 */
public interface GitOperationJournalPort {
    record Command(String operationId, String repoId, String repositoryMapping, String remoteKey, String provider, String baseUrl, String projectId,
                   String action, String targetJson, String requestHash, String runId, String operator, String sessionId,
                   RunPort.TaskFence taskFence) {}
    record Operation(String id, String repoId, String remoteKey, String provider, String baseUrl, String projectId,
                     String action, String targetJson, String runId, String operator, String state, long version,
                     Instant createdAt, Instant updatedAt, String reason, boolean blocked) {}
    record Event(String attemptId, int sequence, String type, Instant at, String evidenceJson, String reason) {}
    record Observation(String id, String operator, String key, long expectedVersion, String status,
                       String evidenceJson, Instant observedAt) {}
    record Detail(Operation operation, List<Event> events, List<Observation> observations, boolean moreObservations) {
        public Detail { events = List.copyOf(events); observations = List.copyOf(observations); }
        @Override public List<Event> events() { return List.copyOf(events); }
        @Override public List<Observation> observations() { return List.copyOf(observations); }
    }
    String begin(Command command);
    void sending(String operationId);
    void finish(String operationId, GitWriteResult.Outcome outcome, String evidenceJson, String reason);
    void completeSession(String sessionId, boolean businessCommitted);
    /** 必须加入原任务结果事务，核对 CREATE_BRANCH 响应证明与 guard，返回原 numeric project；零远端调用。 */
    Optional<String> confirmedAppliedBranchProject(RunPort.TaskClaim claim);
    /** 仅在活动领取者的结果事务中确认；没有远端操作的任务不创建仓库保护。 */
    void acknowledgeTask(RunPort.TaskFence fence, RunItemResult result);
    List<Operation> list(String repoId, int page, int size);
    List<Operation> listByRun(String runId, int page, int size);
    Optional<Detail> find(String operationId);
    Optional<Observation> observation(String operationId, String operator, String key);
    Observation observe(String operationId, String operator, String key, long expectedVersion,
                        GitReadResult.Status status, String evidenceJson);
    boolean hasActiveGuards();
    void lockMappingChanges();
    static String remoteKey(String provider,String baseUrl,String projectId) {
        return mappingHash(provider,baseUrl+"\n"+projectId);
    }
    static String configuredBaseUrl(String value) {
        if(value==null)return "";
        try {
            java.net.URI uri=java.net.URI.create(value.trim());
            if(uri.getScheme()==null||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null)return "";
            String scheme=uri.getScheme().toLowerCase(java.util.Locale.ROOT);
            if(!scheme.equals("http")&&!scheme.equals("https"))return "";
            return scheme+"://"+uri.getHost().toLowerCase(java.util.Locale.ROOT)+(uri.getPort()<0?"":":"+uri.getPort())+uri.getPath().replaceAll("/+$","");
        }catch(IllegalArgumentException e){return "";}
    }
    static String mappingHash(String provider, String cloneUrl) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest((provider+"\n"+cloneUrl).getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
