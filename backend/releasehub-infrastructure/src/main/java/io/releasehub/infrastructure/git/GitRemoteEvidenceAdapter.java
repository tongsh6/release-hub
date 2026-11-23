package io.releasehub.infrastructure.git;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.releasehub.application.gitoperation.*;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.application.settings.SettingsPort;
import io.releasehub.domain.repo.RepoId;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;

/** 恢复核对只调用 GET；不复用会创建临时 MR 的合并检查接口。 */
@Component
@lombok.extern.slf4j.Slf4j
public class GitRemoteEvidenceAdapter implements GitOperationEvidencePort {
    private final CodeRepositoryPort repos;
    private final SettingsPort settings;
    private final ObjectMapper json;
    private final RestTemplate http;
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value="EI_EXPOSE_REP2", justification="共享的 Spring 仓库查询 Port 不可复制；封装不对外返回依赖")
    public GitRemoteEvidenceAdapter(CodeRepositoryPort repos,SettingsPort settings,ObjectMapper json,RestTemplateBuilder builder) {
        this.repos=repos;this.settings=settings;this.json=json.copy();
        http=GitHttpClients.create(builder,Duration.ofSeconds(10));
    }
    @Override public GitReadResult<String> read(GitOperationJournalPort.Detail detail) {
        var op=detail.operation();
        if(!"GITLAB".equals(op.provider()))return result(GitReadResult.Status.UNSUPPORTED,json.createObjectNode());
        try {
            var repo=repos.findById(RepoId.of(op.repoId())).orElse(null);
            if(repo==null)return result(GitReadResult.Status.UNAVAILABLE,json.createObjectNode());
            String clone=repo.getCloneUrl();
            if(clone.startsWith("git@")){int colon=clone.indexOf(':');clone="https://"+clone.substring(4,colon)+"/"+clone.substring(colon+1);}
            URI uri=URI.create(clone);
            String base=uri.getScheme()+"://"+uri.getHost()+(uri.getPort()<0?"":":"+uri.getPort());
            if(!base.equals(op.baseUrl())||!repo.getGitProvider().name().equals(op.provider()))return result(GitReadResult.Status.MISMATCH,json.createObjectNode());
            HttpHeaders headers=new HttpHeaders();String token=repo.getGitAccessToken();
            if(token==null||token.isBlank())token=settings.getGitLab().map(SettingsPort.SettingsGitLab::token).orElse("");
            headers.set("PRIVATE-TOKEN",token);
            String path=uri.getPath().replaceFirst("^/","").replaceFirst("\\.git$","");
            var identity=get(base+"/api/v4/projects/"+encode(path),headers);
            if(identity.getStatusCode().value()!=200)return result(GitReadResult.Status.INCOMPLETE,json.createObjectNode());
            if(GitRemoteIdentityResolver.confirmedProjectId(identity.getStatusCode().value(),identity.getBody())
                    .filter(id->id.equals(op.projectId())).isEmpty())return result(GitReadResult.Status.MISMATCH,json.createObjectNode());
            String project=base+"/api/v4/projects/"+encode(op.projectId());
            JsonNode target=json.readTree(op.targetJson());
            if(op.action().equals("MERGE_MR"))return readMergedMr(op,project,headers,target);
            String suffix=target.path("suffix").asText();
            if(op.action().equals("CREATE_MR"))return findMergeRequest(detail,project,headers,target);
            if(op.action().equals("UPDATE_MR")) {
                JsonNode mr=get(project+suffix.replaceFirst("/merge$",""),headers).getBody();
                if(mr==null)return result(GitReadResult.Status.INCOMPLETE,json.createObjectNode());
                var evidence=summary(mr);
                boolean matches=mr.path("state").asText().equals("closed")
                        &&(!target.has("sha")||target.path("sha").asText().equals(mr.path("sha").asText()));
                return result(matches?GitReadResult.Status.FOUND:GitReadResult.Status.MISMATCH,evidence);
            }
            if(op.action().equals("CREATE_BRANCH")||op.action().equals("CREATE_TAG")) {
                String route=op.action().equals("CREATE_BRANCH")?"/repository/branches/":"/repository/tags/";
                String name=target.path(op.action().equals("CREATE_BRANCH")?"branch":"tag_name").asText();
                JsonNode ref=get(project+route+encode(name),headers).getBody();
                if(ref==null)return result(GitReadResult.Status.INCOMPLETE,json.createObjectNode());
                String sha=ref.path("commit").path("id").asText();var evidence=summary(ref);evidence.put("commitSha",sha);
                String expected=target.path("ref").asText();
                if(!GitLabGitBranchAdapter.validSha(expected))return result(GitReadResult.Status.INCOMPLETE,evidence);
                boolean tag=op.action().equals("CREATE_TAG");
                if(tag&&(name.isBlank()||!ref.path("name").isTextual()||ref.path("name").asText().isBlank()))
                    return result(GitReadResult.Status.INCOMPLETE,evidence);
                boolean matches=expected.equals(sha)&&(!tag||name.equals(ref.path("name").asText()));
                return result(matches?GitReadResult.Status.FOUND:GitReadResult.Status.MISMATCH,evidence);
            }
            if(op.action().equals("UPDATE_FILE")) {
                JsonNode file=get(project+suffix+"?ref="+encode(target.path("branch").asText()),headers).getBody();
                if(file==null)return result(GitReadResult.Status.INCOMPLETE,json.createObjectNode());
                var evidence=json.createObjectNode();evidence.put("contentHash",file.path("content_sha256").asText());
                evidence.put("commitSha",file.path("last_commit_id").asText());
                return result(target.path("contentHash").asText().equals(file.path("content_sha256").asText())
                        ?GitReadResult.Status.FOUND:GitReadResult.Status.MISMATCH,evidence);
            }
            if(op.action().equals("TRIGGER_PIPELINE")) {
                String id=evidenceId(detail,"id");
                if(id==null)return result(GitReadResult.Status.UNSUPPORTED,json.createObjectNode());
                JsonNode pipeline=get(project+"/pipelines/"+encode(id),headers).getBody();
                if(pipeline==null)return result(GitReadResult.Status.INCOMPLETE,json.createObjectNode());
                var evidence=summary(pipeline);evidence.put("status",pipeline.path("status").asText());
                String expectedSha=target.path("sha").asText();
                if(!GitLabGitBranchAdapter.validSha(expectedSha))return result(GitReadResult.Status.INCOMPLETE,evidence);
                var confirmed=GitLabGitBranchAdapter.pipelineEvidence(pipeline,op.projectId(),target.path("ref").asText(),expectedSha);
                if(confirmed==null||!id.equals(confirmed.id()))return result(GitReadResult.Status.MISMATCH,evidence);
                evidence.put("projectId",confirmed.projectId());evidence.put("url",confirmed.url());
                return result(GitReadResult.Status.FOUND,evidence);
            }
            return result(GitReadResult.Status.UNSUPPORTED,json.createObjectNode());
        }catch(HttpClientErrorException e) {
            int status=e.getStatusCode().value();
            return result(status==401||status==403?GitReadResult.Status.FORBIDDEN:status==404?GitReadResult.Status.NOT_FOUND:GitReadResult.Status.UNAVAILABLE,json.createObjectNode());
        }catch(Exception e){
            log.warn("Git 证据查询失败：operation={} error={}",op.id(),e.getClass().getSimpleName());
            return result(GitReadResult.Status.UNAVAILABLE,json.createObjectNode());
        }
    }
    private GitReadResult<String> readMergedMr(GitOperationJournalPort.Operation op,String project,HttpHeaders headers,JsonNode target) {
        if(target==null||!target.isObject()||!"PUT".equals(target.path("method").textValue()))
            return result(GitReadResult.Status.INCOMPLETE,json.createObjectNode());
        String suffix=target.path("suffix").textValue();
        String expectedSha=target.path("sha").textValue();
        if(suffix==null||!suffix.matches("/merge_requests/[1-9][0-9]*/merge")||!GitLabGitBranchAdapter.validSha(expectedSha))
            return result(GitReadResult.Status.INCOMPLETE,json.createObjectNode());
        String iid=suffix.substring("/merge_requests/".length(),suffix.length()-"/merge".length());
        var response=get(project+"/merge_requests/"+iid,headers);
        JsonNode mr=response.getBody();
        if(mr==null||!mr.isObject())return result(GitReadResult.Status.INCOMPLETE,json.createObjectNode());
        // 只证明原 I/IID/A 对象当前 merged；实际分支保留为观察事实，不推定原 PUT 或业务计划成功。
        boolean matches=GitLabMergeRequestAdapter.confirmedMergedMr(
                response.getStatusCode().value(),mr,op.projectId(),iid,expectedSha).isPresent();
        var evidence=json.createObjectNode();
        for(String key:java.util.List.of("project_id","source_project_id","target_project_id","iid",
                "source_branch","target_branch","sha","web_url","state","merge_commit_sha")) {
            JsonNode value=mr.get(key);
            if(value!=null&&value.isValueNode())evidence.set(key,value);
        }
        return result(matches?GitReadResult.Status.FOUND:GitReadResult.Status.MISMATCH,evidence);
    }
    private GitReadResult<String> findMergeRequest(GitOperationJournalPort.Detail detail,String project,HttpHeaders headers,JsonNode target) throws Exception {
        String id=evidenceId(detail,"iid");var matches=new ArrayList<JsonNode>();String marker="ReleaseHub-Operation: "+detail.operation().id();
        if(id!=null) {
            JsonNode found=get(project+"/merge_requests/"+encode(id),headers).getBody();if(found!=null)matches.add(found);
        }else {
            boolean complete=false;
            for(int page=1;page<=5;page++) {
                var response=get(project+"/merge_requests?scope=all&state=all&per_page=100&page="+page,headers);
                JsonNode body=response.getBody();if(body==null||!body.isArray())return result(GitReadResult.Status.INCOMPLETE,json.createObjectNode());
                for(JsonNode mr:body)if(mr.path("description").asText().lines().anyMatch(marker::equals))matches.add(mr);
                String next=response.getHeaders().getFirst("X-Next-Page");
                if(next==null)return result(GitReadResult.Status.INCOMPLETE,json.createObjectNode());
                if(next.isBlank()){complete=true;break;}
                if(!next.equals(String.valueOf(page+1)))return result(GitReadResult.Status.INCOMPLETE,json.createObjectNode());
            }
            if(!complete)return result(GitReadResult.Status.INCOMPLETE,json.createObjectNode());
        }
        if(matches.isEmpty())return result(GitReadResult.Status.NOT_FOUND,json.createObjectNode());
        if(matches.size()!=1)return result(GitReadResult.Status.MISMATCH,json.createObjectNode().put("matches",matches.size()));
        JsonNode mr=matches.get(0);boolean matched=mr.path("description").asText().lines().anyMatch(marker::equals)
                &&target.path("source_branch").asText().equals(mr.path("source_branch").asText())
                &&target.path("target_branch").asText().equals(mr.path("target_branch").asText());
        return result(matched?GitReadResult.Status.FOUND:GitReadResult.Status.MISMATCH,summary(mr));
    }
    private String evidenceId(GitOperationJournalPort.Detail detail,String key) throws Exception {
        for(var event:detail.events()) {
            JsonNode value=json.readTree(event.evidenceJson()).get(key);
            if(value!=null&&value.asText().matches("[0-9]+"))return value.asText();
        }
        return null;
    }
    private ObjectNode summary(JsonNode node) {
        var result=json.createObjectNode();
        for(String key:java.util.List.of("id","iid","sha","merge_commit_sha","state","name","ref"))if(node.hasNonNull(key)&&node.get(key).isValueNode())result.put(key,node.get(key).asText());
        return result;
    }
    private ResponseEntity<JsonNode> get(String uri,HttpHeaders headers){return http.exchange(URI.create(uri),HttpMethod.GET,new HttpEntity<>(headers),JsonNode.class);}
    private String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
    private GitReadResult<String> result(GitReadResult.Status status,ObjectNode evidence){return new GitReadResult<>(status,evidence.toString());}
}
