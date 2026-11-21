package io.releasehub.infrastructure.git;

import com.fasterxml.jackson.databind.JsonNode;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.common.exception.BusinessException;
import io.releasehub.common.exception.ErrorCode;
import io.releasehub.domain.repo.CodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;

/** 以远端稳定 ID 合并别名；既有 clone URL 的字符串规范化不作为并发锁身份。 */
@Component
public class GitRemoteIdentityResolver {
    private final CodeRepositoryPort repos;
    private final io.releasehub.application.settings.SettingsPort settings;
    private final RestTemplate readHttp;
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value="EI_EXPOSE_REP2", justification="共享的 Spring 仓库查询 Port 不可复制；封装不对外返回依赖")
    public GitRemoteIdentityResolver(CodeRepositoryPort repos,io.releasehub.application.settings.SettingsPort settings,RestTemplateBuilder builder) {
        this.repos=repos;this.settings=settings;this.readHttp=GitHttpClients.create(builder,Duration.ofSeconds(10));
    }
    public record Endpoint(String provider,String baseUrl,String locator,String projectEndpoint,String suffix) {}
    public record Identity(String repoId,String repositoryMapping,String remoteKey,String provider,String baseUrl,String projectId,Endpoint endpoint) {}

    public Identity resolve(URI uri,HttpHeaders headers) {
        Endpoint endpoint=parse(uri);
        if(!endpoint.provider().equals("GITLAB"))throw BusinessException.of(ErrorCode.GIT_PROVIDER_UNSUPPORTED);
        String configured=settings.getGitLab().map(s->io.releasehub.application.gitoperation.GitOperationJournalPort.configuredBaseUrl(s.baseUrl())).orElse("");
        if(!endpoint.baseUrl().equals(configured))throw unavailable();
        var candidates=new java.util.HashSet<String>();
        String authority=uri.getRawAuthority().equals("api.github.com")?"github.com":uri.getRawAuthority();
        for(String origin:java.util.List.of(uri.getScheme()+"://"+authority,endpoint.baseUrl().replace("api.github.com","github.com"))) {
            candidates.add(origin+"/"+endpoint.locator());candidates.add(origin+"/"+endpoint.locator()+".git");
        }
        String host=URI.create(endpoint.baseUrl()).getHost().replace("api.github.com","github.com");
        candidates.add("git@"+host+":"+endpoint.locator());candidates.add("git@"+host+":"+endpoint.locator()+".git");
        var matches=repos.findByCloneUrls(candidates,io.releasehub.domain.repo.GitProvider.valueOf(endpoint.provider()));
        if(matches.size()!=1)throw unavailable();
        CodeRepository repo=matches.get(0);
        try {
            JsonNode body=readHttp.exchange(URI.create(endpoint.projectEndpoint()),HttpMethod.GET,
                    new HttpEntity<>(headers),JsonNode.class).getBody();
            if(body==null || !body.path("id").isIntegralNumber())throw unavailable();
            String id=body.path("id").asText();
            return new Identity(repo.getId().value(),io.releasehub.application.gitoperation.GitOperationJournalPort.mappingHash(repo.getGitProvider().name(),repo.getCloneUrl()),io.releasehub.application.gitoperation.GitOperationJournalPort.remoteKey(endpoint.provider(),endpoint.baseUrl(),id),
                    endpoint.provider(),endpoint.baseUrl(),id,endpoint);
        } catch(BusinessException e){throw e;}catch(RuntimeException e){throw unavailable();}
    }
    public static Endpoint parse(URI uri) {
        if(uri.getUserInfo()!=null||uri.getFragment()!=null||uri.getHost()==null)throw BusinessException.of(ErrorCode.GIT_OPERATION_INVALID);
        String origin=uri.getScheme().toLowerCase(Locale.ROOT)+"://"+uri.getHost().toLowerCase(Locale.ROOT)
                +(uri.getPort()<0?"":":"+uri.getPort());
        String path=uri.getRawPath();String marker="/api/v4/projects/";int index=path.indexOf(marker);
        if(index>=0) {
            int end=path.indexOf('/',index+marker.length());if(end<0)end=path.length();
            String raw=path.substring(index+marker.length(),end);
            return new Endpoint("GITLAB",origin+path.substring(0,index),decode(raw),origin+path.substring(0,end),path.substring(end));
        }
        marker="/repos/";index=path.indexOf(marker);
        if(index>=0) {
            int ownerEnd=path.indexOf('/',index+marker.length());int end=ownerEnd<0?-1:path.indexOf('/',ownerEnd+1);
            if(ownerEnd<0)throw BusinessException.of(ErrorCode.GIT_OPERATION_INVALID);
            if(end<0)end=path.length();
            return new Endpoint("GITHUB",origin+path.substring(0,index),decode(path.substring(index+marker.length(),end)),
                    origin+path.substring(0,end),path.substring(end));
        }
        throw BusinessException.of(ErrorCode.GIT_OPERATION_INVALID);
    }
    public JsonNode metadata(Identity identity,String suffix,String ref,HttpHeaders headers) {
        try {
            String endpoint=identity.baseUrl()+"/api/v4/projects/"+identity.projectId()+suffix
                    +(ref==null?"":"?ref="+java.net.URLEncoder.encode(ref,StandardCharsets.UTF_8));
            JsonNode node=readHttp.exchange(URI.create(endpoint),HttpMethod.GET,new HttpEntity<>(headers),JsonNode.class).getBody();
            if(node==null)throw unavailable();return node;
        }catch(RuntimeException e){throw unavailable();}
    }
    public static String hash(String value){return hash(value.getBytes(StandardCharsets.UTF_8));}
    public static String hash(byte[] value) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    public static String decode(String value){return URLDecoder.decode(value,StandardCharsets.UTF_8);}
    private BusinessException unavailable(){return BusinessException.of(ErrorCode.GIT_IDENTITY_UNAVAILABLE);}
}
