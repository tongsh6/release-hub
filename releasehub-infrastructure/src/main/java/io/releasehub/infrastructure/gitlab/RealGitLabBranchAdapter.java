package io.releasehub.infrastructure.gitlab;

import io.releasehub.application.port.out.GitLabBranchPort;
import io.releasehub.application.settings.SettingsPort;
import io.releasehub.common.exception.BusinessException;
import io.releasehub.common.exception.ValidationException;
import io.releasehub.domain.run.MergeStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 真实 GitLab 分支操作适配器
 * 通过 GitLab REST API 执行分支操作
 */
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(name = "releasehub.gitlab.real-adapter", havingValue = "true", matchIfMissing = false)
public class RealGitLabBranchAdapter implements GitLabBranchPort {

    private final SettingsPort settingsPort;
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public boolean createBranch(String repoCloneUrl, String branchName, String sourceBranch) {
        log.info("Creating branch '{}' from '{}' in repo {}", branchName, sourceBranch, repoCloneUrl);
        
        try {
            GitLabConfig config = getGitLabConfig();
            String projectPath = extractProjectPath(repoCloneUrl);
            String encodedPath = URLEncoder.encode(projectPath, StandardCharsets.UTF_8);
            
            String url = String.format("%s/api/v4/projects/%s/repository/branches", 
                    config.baseUrl, encodedPath);
            
            HttpHeaders headers = createHeaders(config.token);
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            Map<String, String> body = new HashMap<>();
            body.put("branch", branchName);
            body.put("ref", sourceBranch);
            
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity,
                    new ParameterizedTypeReference<>() {});
            
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Branch '{}' created successfully", branchName);
                return true;
            }
            return false;
            
        } catch (HttpClientErrorException.BadRequest e) {
            log.warn("Failed to create branch '{}': {}", branchName, e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Error creating branch '{}': {}", branchName, e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean branchExists(String repoCloneUrl, String branchName) {
        try {
            GitLabConfig config = getGitLabConfig();
            String projectPath = extractProjectPath(repoCloneUrl);
            String encodedPath = URLEncoder.encode(projectPath, StandardCharsets.UTF_8);
            String encodedBranch = URLEncoder.encode(branchName, StandardCharsets.UTF_8);
            
            String url = String.format("%s/api/v4/projects/%s/repository/branches/%s", 
                    config.baseUrl, encodedPath, encodedBranch);
            
            HttpHeaders headers = createHeaders(config.token);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity,
                    new ParameterizedTypeReference<>() {});
            
            return response.getStatusCode().is2xxSuccessful();
            
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        } catch (Exception e) {
            log.warn("Error checking branch existence '{}': {}", branchName, e.getMessage());
            return false;
        }
    }

    @Override
    public MergeResult mergeBranch(String repoCloneUrl, String sourceBranch, String targetBranch, String commitMessage) {
        log.info("Merging '{}' to '{}' in repo {}", sourceBranch, targetBranch, repoCloneUrl);
        
        try {
            GitLabConfig config = getGitLabConfig();
            String projectPath = extractProjectPath(repoCloneUrl);
            String encodedPath = URLEncoder.encode(projectPath, StandardCharsets.UTF_8);
            
            // Step 1: 创建 Merge Request
            String createMrUrl = String.format("%s/api/v4/projects/%s/merge_requests", 
                    config.baseUrl, encodedPath);
            
            HttpHeaders headers = createHeaders(config.token);
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            Map<String, Object> mrBody = new HashMap<>();
            mrBody.put("source_branch", sourceBranch);
            mrBody.put("target_branch", targetBranch);
            mrBody.put("title", commitMessage);
            mrBody.put("remove_source_branch", false);
            
            HttpEntity<Map<String, Object>> createEntity = new HttpEntity<>(mrBody, headers);
            
            ResponseEntity<Map<String, Object>> createResponse = restTemplate.exchange(
                    createMrUrl, HttpMethod.POST, createEntity,
                    new ParameterizedTypeReference<>() {});
            
            if (!createResponse.getStatusCode().is2xxSuccessful() || createResponse.getBody() == null) {
                return MergeResult.failed("Failed to create merge request");
            }
            
            Object iidObj = createResponse.getBody().get("iid");
            if (iidObj == null) {
                return MergeResult.failed("No MR iid returned");
            }
            int mrIid = iidObj instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(iidObj));
            
            // Step 2: 尝试接受（合并）MR
            String acceptMrUrl = String.format("%s/api/v4/projects/%s/merge_requests/%d/merge", 
                    config.baseUrl, encodedPath, mrIid);
            
            Map<String, Object> acceptBody = new HashMap<>();
            acceptBody.put("merge_commit_message", commitMessage);
            acceptBody.put("should_remove_source_branch", false);
            
            HttpEntity<Map<String, Object>> acceptEntity = new HttpEntity<>(acceptBody, headers);
            
            ResponseEntity<Map<String, Object>> acceptResponse = restTemplate.exchange(
                    acceptMrUrl, HttpMethod.PUT, acceptEntity,
                    new ParameterizedTypeReference<>() {});
            
            if (acceptResponse.getStatusCode().is2xxSuccessful()) {
                log.info("Merge successful: {} -> {}", sourceBranch, targetBranch);
                return MergeResult.success();
            }
            
            // 检查合并状态
            Map<String, Object> acceptResult = acceptResponse.getBody();
            if (acceptResult != null) {
                Object state = acceptResult.get("state");
                if ("merged".equals(state)) {
                    return MergeResult.success();
                }
                Object mergeError = acceptResult.get("merge_error");
                if (mergeError != null && mergeError.toString().contains("conflict")) {
                    return MergeResult.conflict(mergeError.toString());
                }
            }
            
            return MergeResult.failed("Merge failed with unknown reason");
            
        } catch (HttpClientErrorException e) {
            String responseBody = e.getResponseBodyAsString();
            log.warn("Merge failed: {}", responseBody);
            
            if (responseBody.contains("conflict") || responseBody.contains("cannot be merged")) {
                return MergeResult.conflict(responseBody);
            }
            if (responseBody.contains("already exists")) {
                // MR 已存在，尝试查找并合并
                return tryMergeExistingMr(repoCloneUrl, sourceBranch, targetBranch, commitMessage);
            }
            return MergeResult.failed(responseBody);
            
        } catch (Exception e) {
            log.error("Error merging branches: {}", e.getMessage(), e);
            return MergeResult.failed(e.getMessage());
        }
    }
    
    private MergeResult tryMergeExistingMr(String repoCloneUrl, String sourceBranch, 
                                            String targetBranch, String commitMessage) {
        try {
            GitLabConfig config = getGitLabConfig();
            String projectPath = extractProjectPath(repoCloneUrl);
            String encodedPath = URLEncoder.encode(projectPath, StandardCharsets.UTF_8);
            
            // 查找已存在的 MR
            String listMrUrl = String.format(
                    "%s/api/v4/projects/%s/merge_requests?source_branch=%s&target_branch=%s&state=opened",
                    config.baseUrl, encodedPath,
                    URLEncoder.encode(sourceBranch, StandardCharsets.UTF_8),
                    URLEncoder.encode(targetBranch, StandardCharsets.UTF_8));
            
            HttpHeaders headers = createHeaders(config.token);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            ResponseEntity<List<Map<String, Object>>> listResponse = restTemplate.exchange(
                    listMrUrl, HttpMethod.GET, entity,
                    new ParameterizedTypeReference<>() {});
            
            List<Map<String, Object>> mrs = listResponse.getBody();
            if (mrs == null || mrs.isEmpty()) {
                return MergeResult.failed("No existing MR found");
            }
            
            Object iidObj = mrs.get(0).get("iid");
            int mrIid = iidObj instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(iidObj));
            
            // 尝试合并现有 MR
            String acceptMrUrl = String.format("%s/api/v4/projects/%s/merge_requests/%d/merge", 
                    config.baseUrl, encodedPath, mrIid);
            
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> acceptBody = new HashMap<>();
            acceptBody.put("merge_commit_message", commitMessage);
            
            HttpEntity<Map<String, Object>> acceptEntity = new HttpEntity<>(acceptBody, headers);
            
            ResponseEntity<Map<String, Object>> acceptResponse = restTemplate.exchange(
                    acceptMrUrl, HttpMethod.PUT, acceptEntity,
                    new ParameterizedTypeReference<>() {});
            
            if (acceptResponse.getStatusCode().is2xxSuccessful()) {
                return MergeResult.success();
            }
            return MergeResult.failed("Failed to merge existing MR");
            
        } catch (Exception e) {
            log.error("Error merging existing MR: {}", e.getMessage());
            return MergeResult.failed(e.getMessage());
        }
    }

    @Override
    public boolean archiveBranch(String repoCloneUrl, String branchName, String reason) {
        log.info("Archiving branch '{}' in repo {} with reason: {}", branchName, repoCloneUrl, reason);
        
        try {
            GitLabConfig config = getGitLabConfig();
            String projectPath = extractProjectPath(repoCloneUrl);
            String encodedPath = URLEncoder.encode(projectPath, StandardCharsets.UTF_8);
            
            // GitLab 不支持直接重命名分支，需要：
            // 1. 从原分支创建新的 archive 分支
            // 2. 删除原分支
            
            String archivedBranchName = "archive/" + reason + "/" + branchName.replace("/", "-");
            
            // Step 1: 创建归档分支
            boolean created = createBranch(repoCloneUrl, archivedBranchName, branchName);
            if (!created) {
                log.warn("Failed to create archive branch '{}'", archivedBranchName);
                // 如果归档分支已存在，继续删除原分支
            }
            
            // Step 2: 删除原分支
            String deleteUrl = String.format("%s/api/v4/projects/%s/repository/branches/%s", 
                    config.baseUrl, encodedPath, URLEncoder.encode(branchName, StandardCharsets.UTF_8));
            
            HttpHeaders headers = createHeaders(config.token);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Void> response = restTemplate.exchange(
                    deleteUrl, HttpMethod.DELETE, entity, Void.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Branch '{}' archived as '{}'", branchName, archivedBranchName);
                return true;
            }
            return false;
            
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Branch '{}' not found, may already be archived", branchName);
            return true; // 分支不存在视为成功
        } catch (Exception e) {
            log.error("Error archiving branch '{}': {}", branchName, e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean createTag(String repoCloneUrl, String tagName, String ref, String message) {
        log.info("Creating tag '{}' at '{}' in repo {}", tagName, ref, repoCloneUrl);
        
        try {
            GitLabConfig config = getGitLabConfig();
            String projectPath = extractProjectPath(repoCloneUrl);
            String encodedPath = URLEncoder.encode(projectPath, StandardCharsets.UTF_8);
            
            String url = String.format("%s/api/v4/projects/%s/repository/tags", 
                    config.baseUrl, encodedPath);
            
            HttpHeaders headers = createHeaders(config.token);
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            Map<String, String> body = new HashMap<>();
            body.put("tag_name", tagName);
            body.put("ref", ref);
            body.put("message", message);
            
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity,
                    new ParameterizedTypeReference<>() {});
            
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Tag '{}' created successfully", tagName);
                return true;
            }
            return false;
            
        } catch (HttpClientErrorException.BadRequest e) {
            String responseBody = e.getResponseBodyAsString();
            if (responseBody.contains("already exists")) {
                log.warn("Tag '{}' already exists", tagName);
                return true; // 标签已存在视为成功
            }
            log.warn("Failed to create tag '{}': {}", tagName, responseBody);
            return false;
        } catch (Exception e) {
            log.error("Error creating tag '{}': {}", tagName, e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    public String triggerPipeline(String repoCloneUrl, String ref) {
        log.info("Triggering pipeline for ref '{}' in repo {}", ref, repoCloneUrl);
        
        try {
            GitLabConfig config = getGitLabConfig();
            String projectPath = extractProjectPath(repoCloneUrl);
            String encodedPath = URLEncoder.encode(projectPath, StandardCharsets.UTF_8);
            
            String url = String.format("%s/api/v4/projects/%s/pipeline", 
                    config.baseUrl, encodedPath);
            
            HttpHeaders headers = createHeaders(config.token);
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            Map<String, String> body = new HashMap<>();
            body.put("ref", ref);
            
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);
            
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity,
                    new ParameterizedTypeReference<>() {});
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object pipelineId = response.getBody().get("id");
                if (pipelineId != null) {
                    String id = String.valueOf(pipelineId);
                    log.info("Pipeline triggered successfully with ID: {}", id);
                    return id;
                }
            }
            
            log.warn("Failed to trigger pipeline for ref '{}'", ref);
            return null;
            
        } catch (HttpClientErrorException e) {
            log.warn("Failed to trigger pipeline for ref '{}': {}", ref, e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.error("Error triggering pipeline for ref '{}': {}", ref, e.getMessage(), e);
            return null;
        }
    }
    
    // ========== Helper Methods ==========
    
    private GitLabConfig getGitLabConfig() {
        var settings = settingsPort.getGitLab();
        if (settings.isEmpty()) {
            throw BusinessException.gitlabSettingsMissing();
        }
        String baseUrl = normalizeBaseUrl(settings.get().baseUrl());
        String token = settings.get().tokenMasked();
        return new GitLabConfig(baseUrl, token);
    }
    
    private HttpHeaders createHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("PRIVATE-TOKEN", token);
        return headers;
    }
    
    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            return "";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
    
    private String extractProjectPath(String cloneUrl) {
        if (cloneUrl == null || cloneUrl.isBlank()) {
            throw ValidationException.invalidParameter("cloneUrl");
        }

        Matcher sshMatch = Pattern.compile("^git@([^:]+):(.+?)(\\.git)?$").matcher(cloneUrl.trim());
        if (sshMatch.find()) {
            return sshMatch.group(2);
        }
        Matcher httpsMatch = Pattern.compile("^https?://([^/]+)/(.+?)(\\.git)?$").matcher(cloneUrl.trim());
        if (httpsMatch.find()) {
            return httpsMatch.group(2);
        }
        throw ValidationException.invalidParameter("cloneUrl");
    }
    
    private record GitLabConfig(String baseUrl, String token) {}
}
