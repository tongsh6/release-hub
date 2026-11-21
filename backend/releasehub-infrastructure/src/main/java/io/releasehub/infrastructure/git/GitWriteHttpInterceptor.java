package io.releasehub.infrastructure.git;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.releasehub.application.gitoperation.*;
import io.releasehub.common.exception.BusinessException;
import io.releasehub.common.exception.ErrorCode;
import org.springframework.http.*;
import org.springframework.http.client.*;
import org.springframework.http.client.support.HttpRequestWrapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.io.*;
import java.net.URI;
import java.time.Instant;
import java.util.*;

/** 最终 HTTP 出口保护每个写请求，包括旧组合操作内部的临时 MR/PR。 */
@Component
public class GitWriteHttpInterceptor implements ClientHttpRequestInterceptor {
    private final boolean enabled;
    private final GitSafetySessions sessions;
    private final GitOperationJournalPort journal;
    private final GitRemoteIdentityResolver identities;
    private final ObjectMapper json;
    private final TransactionTemplate withoutTransaction;
    public GitWriteHttpInterceptor(GitSafetyProperties properties,GitSafetySessions sessions,GitOperationJournalPort journal,
                                   GitRemoteIdentityResolver identities,ObjectMapper json,PlatformTransactionManager transactions) {
        this.enabled=properties.isEnabled();this.sessions=sessions;this.journal=journal;this.identities=identities;this.json=json.copy();
        withoutTransaction=new TransactionTemplate(transactions);withoutTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
    }
    @Override public ClientHttpResponse intercept(HttpRequest request,byte[] body,ClientHttpRequestExecution execution) throws IOException {
        if(request.getMethod()==HttpMethod.GET || request.getMethod()==HttpMethod.HEAD) {
            HttpHeaders headers=GitRemoteIdentityResolver.externalHeaders(request.getHeaders());
            return execution.execute(new HttpRequestWrapper(request) {
                @Override public HttpHeaders getHeaders(){return headers;}
            },body);
        }
        if(!enabled) throw BusinessException.of(ErrorCode.GIT_WRITE_BLOCKED);
        try(var scope=sessions.open()) {
            try {
                return Objects.requireNonNull(withoutTransaction.execute(tx->{
                    try{return write(request,body,execution,scope.id());}catch(IOException e){throw new UncheckedIOException(e);}
                }), "网络调用未返回响应");
            }catch(UncheckedIOException e){scope.failed();throw e.getCause();}
            catch(RuntimeException e){scope.failed();throw e;}
        }
    }
    private ClientHttpResponse write(HttpRequest request,byte[] original,ClientHttpRequestExecution execution,String session) throws IOException {
        var identity=GitRemoteIdentityResolver.confirm(identities.resolve(request.getURI(),request.getHeaders()),
                request.getHeaders().getFirst(GitRemoteIdentityResolver.EXPECTED_PROJECT_ID));
        String suffix=identity.endpoint().suffix();String action=action(request.getMethod(),suffix);
        String expectedProjectId=request.getHeaders().getFirst(GitRemoteIdentityResolver.EXPECTED_PROJECT_ID);
        String expectedSha=request.getHeaders().getFirst(GitRemoteIdentityResolver.EXPECTED_SHA);
        if(Set.of("CREATE_TAG","TRIGGER_PIPELINE","CREATE_MR","MERGE_MR").contains(action)
                &&!GitRemoteIdentityResolver.validProjectId(expectedProjectId))throw BusinessException.of(ErrorCode.GIT_OPERATION_INVALID);
        if(expectedProjectId!=null&&!expectedProjectId.equals(identity.projectId()))throw BusinessException.of(ErrorCode.GIT_IDENTITY_UNAVAILABLE);
        if(Set.of("TRIGGER_PIPELINE","CREATE_MR","MERGE_MR").contains(action)&&!GitLabGitBranchAdapter.validSha(expectedSha))throw BusinessException.of(ErrorCode.GIT_OPERATION_INVALID);
        // 当前 Provider 删除分支 API 没有已验证的原子 SHA 前置条件；不把一次读取误当 CAS。
        if(action.equals("DELETE_BRANCH"))throw BusinessException.of(ErrorCode.GIT_WRITE_BLOCKED);
        String rawQuery=request.getURI().getRawQuery();
        URI dispatchUri=URI.create(identity.baseUrl()+"/api/v4/projects/"+identity.projectId()+suffix
                +(rawQuery==null?"":"?"+rawQuery));
        String id=UUID.randomUUID().toString();byte[] body=original;
        ObjectNode input=original.length==0?json.createObjectNode():(ObjectNode)json.readTree(original);
        if(action.equals("MERGE_MR")&&!expectedSha.equals(input.path("sha").asText()))throw BusinessException.of(ErrorCode.GIT_OPERATION_INVALID);
        ObjectNode target=json.createObjectNode();
        if(action.equals("CREATE_BRANCH")||action.equals("CREATE_TAG")) {
            String ref=input.path("ref").asText();
            if(action.equals("CREATE_TAG")&&!GitLabGitBranchAdapter.validSha(ref))throw BusinessException.of(ErrorCode.GIT_OPERATION_INVALID);
            var commit=identities.metadata(identity,"/repository/commits/"+java.net.URLEncoder.encode(ref,java.nio.charset.StandardCharsets.UTF_8),null,request.getHeaders());
            String sha=commit.path("id").asText();
            if(!GitLabGitBranchAdapter.validSha(sha)
                    ||action.equals("CREATE_TAG")&&!ref.equals(sha))throw BusinessException.of(ErrorCode.GIT_IDENTITY_UNAVAILABLE);
            target.put("sourceRef",ref);input.put("ref",sha);
        }
        if(action.equals("UPDATE_FILE")) {
            String expectedRevision=input.path("last_commit_id").asText();
            if(!expectedRevision.matches("[0-9a-fA-F]{40,64}"))throw BusinessException.of(ErrorCode.GIT_OPERATION_INVALID);
            var before=identities.metadata(identity,suffix,input.path("branch").asText(),request.getHeaders());
            String currentRevision=before.path("last_commit_id").asText();
            if(!currentRevision.matches("[0-9a-fA-F]{40,64}"))throw BusinessException.of(ErrorCode.GIT_IDENTITY_UNAVAILABLE);
            if(!expectedRevision.equals(currentRevision))throw new java.util.ConcurrentModificationException("FILE_REVISION_CHANGED");
            // 原始读取 revision 继续交给 GitLab 做原子校验；不得换成发送前的最新 revision。
            target.put("beforeContentHash",before.path("content_sha256").asText());
            input.put("commit_message",input.path("commit_message").asText()+"\nReleaseHub-Operation: "+id);
        }
        if(action.equals("CREATE_MR")||action.equals("CREATE_PR")) {
            String field=action.equals("CREATE_MR")?"description":"body";
            input.put(field,input.path(field).asText("")+"\nReleaseHub-Operation: "+id);
        }
        body=json.writeValueAsBytes(input);
        target.put("suffix",suffix);target.put("method",request.getMethod().name());
        for(String key:List.of("branch","ref","source_branch","target_branch","head","base","tag_name","sha","last_commit_id","state_event","state")) {
            if(input.hasNonNull(key)&&input.get(key).isValueNode())target.put(key,input.get(key).asText());
        }
        if(input.has("content"))target.put("contentHash",GitRemoteIdentityResolver.hash(input.path("content").asText()));
        if(request.getURI().getRawQuery()!=null) {
            for(String pair:request.getURI().getRawQuery().split("&")) {
                String[] parts=pair.split("=",2);
                if(parts.length==2&&Set.of("ref","branch").contains(parts[0]))target.put(parts[0],GitRemoteIdentityResolver.decode(parts[1]));
            }
        }
        if(action.equals("TRIGGER_PIPELINE")||action.equals("CREATE_MR"))target.put("sha",expectedSha);
        String requestHash=GitRemoteIdentityResolver.hash(request.getMethod()+"|"+identity.remoteKey()+"|"+suffix+"|"
                +(rawQuery==null?"":rawQuery)+"|"+GitRemoteIdentityResolver.hash(body)+"|"+target.path("sha").asText());
        var auth=SecurityContextHolder.getContext().getAuthentication();
        String operator=GitOperationContext.operator();
        if(operator==null)operator=auth==null?"system":auth.getName();
        journal.begin(new GitOperationJournalPort.Command(id,identity.repoId(),identity.repositoryMapping(),identity.remoteKey(),identity.provider(),identity.baseUrl(),identity.projectId(),
                action,json.writeValueAsString(target),requestHash,GitOperationContext.runId(),operator,session,GitOperationContext.taskFence()));
        journal.sending(id);
        HttpHeaders headers=GitRemoteIdentityResolver.externalHeaders(request.getHeaders());headers.setContentLength(body.length);
        HttpRequest wrapped=new HttpRequestWrapper(request) {
            @Override public URI getURI(){return dispatchUri;}
            @Override public HttpHeaders getHeaders(){return headers;}
        };
        ClientHttpResponse response;
        byte[] bytes;
        try {
            response=execution.execute(wrapped,body);
            bytes=response.getBody().readNBytes(1024*1024+1);
            if(bytes.length>1024*1024){response.close();throw new IOException("远端写响应超过上限");}
        }catch(IOException|RuntimeException e){
            journal.finish(id,GitWriteResult.Outcome.UNKNOWN,"{}","RESPONSE_UNCONFIRMED");throw e;
        }
        int status=response.getStatusCode().value();var evidence=json.createObjectNode();evidence.put("httpStatus",status);
        JsonNode parsed=null;
        try{if(bytes.length>0)parsed=json.readTree(bytes);}catch(IOException ignored){/* 保留不完整结果，不保存原始响应。 */}
        if(parsed!=null&&!action.equals("CREATE_BRANCH")&&!action.equals("CREATE_MR")) {
            for(String key:List.of("id","iid","number","sha","merge_commit_sha","state","name","ref")) {
                if(parsed.hasNonNull(key)&&parsed.get(key).isValueNode())evidence.put(key,parsed.get(key).asText());
            }
            for(String key:List.of("commit","object")) {
                JsonNode object=parsed.path(key);
                if(object.hasNonNull("id"))evidence.put("commitSha",object.get("id").asText());
                if(object.hasNonNull("sha"))evidence.put("commitSha",object.get("sha").asText());
            }
        }
        GitWriteResult.Outcome outcome=status>=200&&status<300?GitWriteResult.Outcome.APPLIED
                : status>=400&&status<500&&status!=408?GitWriteResult.Outcome.REJECTED:GitWriteResult.Outcome.UNKNOWN;
        if(outcome==GitWriteResult.Outcome.APPLIED && (parsed==null
                ||action.equals("CREATE_PR")&&!evidence.has("number")||action.equals("TRIGGER_PIPELINE")&&!evidence.has("id")))outcome=GitWriteResult.Outcome.UNKNOWN;
        if(outcome==GitWriteResult.Outcome.APPLIED&&action.equals("TRIGGER_PIPELINE")) {
            var pipeline=GitLabGitBranchAdapter.pipelineEvidence(parsed,identity.projectId(),target.path("ref").asText(),expectedSha);
            if(pipeline==null)outcome=GitWriteResult.Outcome.UNKNOWN;
            else {evidence.put("projectId",pipeline.projectId());evidence.put("url",pipeline.url());}
        }
        if(outcome==GitWriteResult.Outcome.APPLIED&&action.equals("CREATE_MR")) {
            var created=GitLabMergeRequestAdapter.confirmedCreatedMr(status,parsed,identity.projectId(),
                    target.path("source_branch").textValue(),target.path("target_branch").textValue(),target.path("sha").textValue());
            if(created.isEmpty())outcome=GitWriteResult.Outcome.UNKNOWN;
            else for(String key:List.of("project_id","source_project_id","target_project_id","iid",
                    "source_branch","target_branch","sha","state","web_url")) {
                evidence.set(key,parsed.get(key));
            }
        }
        if(action.equals("CREATE_BRANCH")) {
            String branchSha=GitLabGitBranchAdapter.createdBranchSha(status,parsed,target.path("branch").textValue());
            if(branchSha!=null) {
                evidence.put("name",parsed.path("name").textValue());
                evidence.putObject("commit").put("id",branchSha);
            }
            String frozenSha=target.path("ref").textValue();
            if(outcome==GitWriteResult.Outcome.APPLIED&&(branchSha==null
                    ||!GitLabGitBranchAdapter.validSha(frozenSha)||!frozenSha.equalsIgnoreCase(branchSha))) {
                outcome=GitWriteResult.Outcome.UNKNOWN;
            }
        }
        if(outcome==GitWriteResult.Outcome.APPLIED&&action.equals("CREATE_TAG")) {
            if(!target.path("tag_name").asText().equals(evidence.path("name").asText())
                    ||!target.path("ref").asText().equals(evidence.path("commitSha").asText())) {
                outcome=GitWriteResult.Outcome.UNKNOWN;
            }
        }
        if(outcome==GitWriteResult.Outcome.APPLIED&&(action.equals("MERGE_MR")||action.equals("UPDATE_MR"))) {
            String expectedState=action.equals("MERGE_MR")?"merged":"closed";
            if(!expectedState.equals(evidence.path("state").asText()))outcome=GitWriteResult.Outcome.UNKNOWN;
        }
        if(outcome==GitWriteResult.Outcome.APPLIED&&action.equals("UPDATE_FILE")) {
            try {
                var after=identities.metadata(identity,suffix,input.path("branch").asText(),request.getHeaders());
                evidence.put("commitSha",after.path("last_commit_id").asText());
                evidence.put("contentHash",after.path("content_sha256").asText());
                if(!target.path("contentHash").asText().equals(after.path("content_sha256").asText()))outcome=GitWriteResult.Outcome.UNKNOWN;
            }catch(RuntimeException e){outcome=GitWriteResult.Outcome.UNKNOWN;}
        }
        if(action.equals("DISPATCH_EVENT"))outcome=GitWriteResult.Outcome.UNKNOWN;
        String rejectionReason=outcome==GitWriteResult.Outcome.REJECTED && identity.provider().equals("GITLAB")
                && Set.of("CREATE_MR","MERGE_MR").contains(action)
                ?GitLabMergeRequestAdapter.classifyRejection(status,parsed):null;
        if(rejectionReason!=null)evidence.put("rejectionReason",rejectionReason);
        try {journal.finish(id,outcome,json.writeValueAsString(evidence),
                outcome==GitWriteResult.Outcome.UNKNOWN?"RESPONSE_UNCONFIRMED":rejectionReason);}
        catch(RuntimeException e){response.close();throw e;}
        if(outcome==GitWriteResult.Outcome.UNKNOWN){response.close();throw new IOException("REMOTE_RESULT_UNKNOWN");}
        if(status>=400) {
            ObjectNode sanitized=json.createObjectNode().put("message","REMOTE_WRITE_REJECTED");
            if(rejectionReason!=null)sanitized.put("reason",rejectionReason);
            bytes=json.writeValueAsBytes(sanitized);
        }
        final byte[] captured=bytes;
        return new ClientHttpResponse() {
            @Override public HttpStatusCode getStatusCode() throws IOException{return response.getStatusCode();}
            @Override public String getStatusText() throws IOException{return response.getStatusText();}
            @Override public HttpHeaders getHeaders(){var result=new HttpHeaders();result.putAll(response.getHeaders());result.setContentLength(captured.length);return result;}
            @Override public InputStream getBody(){return new ByteArrayInputStream(captured);}
            @Override public void close(){response.close();}
        };
    }
    private String action(HttpMethod method,String suffix) {
        if(method==HttpMethod.DELETE&&(suffix.startsWith("/repository/branches/")||suffix.startsWith("/git/refs/heads/")))return "DELETE_BRANCH";
        if(method==HttpMethod.POST&&suffix.equals("/repository/branches"))return "CREATE_BRANCH";
        if(method==HttpMethod.POST&&suffix.equals("/git/refs"))return "CREATE_REF";
        if(method==HttpMethod.POST&&suffix.equals("/merge_requests"))return "CREATE_MR";
        if(method==HttpMethod.POST&&suffix.equals("/pulls"))return "CREATE_PR";
        if(method==HttpMethod.PUT&&suffix.matches("/merge_requests/[0-9]+/merge"))return "MERGE_MR";
        if((method==HttpMethod.PUT||method==HttpMethod.PATCH)&&suffix.matches("/(merge_requests|pulls)/[0-9]+"))return "UPDATE_MR";
        if(method==HttpMethod.POST&&suffix.equals("/merges"))return "MERGE_BRANCH";
        if(method==HttpMethod.POST&&suffix.equals("/repository/tags"))return "CREATE_TAG";
        if(method==HttpMethod.POST&&suffix.equals("/pipeline"))return "TRIGGER_PIPELINE";
        if(method==HttpMethod.POST&&suffix.equals("/dispatches"))return "DISPATCH_EVENT";
        if(method==HttpMethod.PUT&&suffix.startsWith("/repository/files/"))return "UPDATE_FILE";
        throw BusinessException.of(ErrorCode.GIT_OPERATION_INVALID);
    }
}
