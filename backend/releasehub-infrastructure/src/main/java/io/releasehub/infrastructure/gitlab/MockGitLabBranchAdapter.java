package io.releasehub.infrastructure.gitlab;

import io.releasehub.application.port.out.GitLabBranchPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模拟 GitLab 分支操作适配器
 * 用于开发和测试环境
 */
@Slf4j
@Component
public class MockGitLabBranchAdapter implements GitLabBranchPort {
    
    // 模拟分支存储: key = repoUrl, value = 分支集合
    private final Map<String, Set<String>> mockBranches = new ConcurrentHashMap<>();
    
    // 模拟标签存储: key = repoUrl, value = 标签集合
    private final Map<String, Set<String>> mockTags = new ConcurrentHashMap<>();
    
    public MockGitLabBranchAdapter() {
        // 初始化时，所有仓库默认有 master 分支
    }
    
    @Override
    public boolean createBranch(String repoCloneUrl, String branchName, String sourceBranch) {
        log.info("Mock: Creating branch '{}' from '{}' in repo {}", branchName, sourceBranch, repoCloneUrl);
        
        Set<String> branches = mockBranches.computeIfAbsent(repoCloneUrl, k -> {
            Set<String> set = new HashSet<>();
            set.add("master");
            set.add("main");
            return set;
        });
        
        // 检查源分支是否存在
        if (!branches.contains(sourceBranch) && !sourceBranch.equals("master") && !sourceBranch.equals("main")) {
            log.warn("Mock: Source branch '{}' not found", sourceBranch);
            return false;
        }
        
        // 检查目标分支是否已存在
        if (branches.contains(branchName)) {
            log.warn("Mock: Branch '{}' already exists", branchName);
            return false;
        }
        
        branches.add(branchName);
        log.info("Mock: Branch '{}' created successfully", branchName);
        return true;
    }
    
    @Override
    public boolean branchExists(String repoCloneUrl, String branchName) {
        Set<String> branches = mockBranches.get(repoCloneUrl);
        if (branches == null) {
            // 默认 master 和 main 分支存在
            return "master".equals(branchName) || "main".equals(branchName);
        }
        return branches.contains(branchName);
    }
    
    @Override
    public MergeResult mergeBranch(String repoCloneUrl, String sourceBranch, String targetBranch, String commitMessage) {
        log.info("Mock: Merging '{}' to '{}' in repo {} with message: {}", 
                sourceBranch, targetBranch, repoCloneUrl, commitMessage);
        
        // 模拟：检查分支是否存在
        if (!branchExists(repoCloneUrl, sourceBranch)) {
            log.warn("Mock: Source branch '{}' not found", sourceBranch);
            return MergeResult.failed("Source branch not found: " + sourceBranch);
        }
        
        if (!branchExists(repoCloneUrl, targetBranch)) {
            log.warn("Mock: Target branch '{}' not found", targetBranch);
            return MergeResult.failed("Target branch not found: " + targetBranch);
        }
        
        // 模拟：随机模拟冲突（用于测试）
        // 正常情况下返回成功
        log.info("Mock: Merge successful");
        return MergeResult.success();
    }
    
    @Override
    public boolean archiveBranch(String repoCloneUrl, String branchName, String reason) {
        log.info("Mock: Archiving branch '{}' in repo {} with reason {}", branchName, repoCloneUrl, reason);
        
        Set<String> branches = mockBranches.get(repoCloneUrl);
        if (branches == null || !branches.contains(branchName)) {
            log.warn("Mock: Branch '{}' not found", branchName);
            return false;
        }
        
        // 重命名为 archive/<reason>/<original>
        String archivedName = "archive/" + reason + "/" + branchName;
        branches.remove(branchName);
        branches.add(archivedName);
        
        log.info("Mock: Branch archived as '{}'", archivedName);
        return true;
    }
    
    @Override
    public boolean createTag(String repoCloneUrl, String tagName, String ref, String message) {
        log.info("Mock: Creating tag '{}' at '{}' in repo {} with message: {}", 
                tagName, ref, repoCloneUrl, message);
        
        Set<String> tags = mockTags.computeIfAbsent(repoCloneUrl, k -> new HashSet<>());
        
        if (tags.contains(tagName)) {
            log.warn("Mock: Tag '{}' already exists", tagName);
            return false;
        }
        
        tags.add(tagName);
        log.info("Mock: Tag '{}' created successfully", tagName);
        return true;
    }
    
    @Override
    public String triggerPipeline(String repoCloneUrl, String ref) {
        log.info("Mock: Triggering pipeline for ref '{}' in repo {}", ref, repoCloneUrl);
        
        // 模拟返回一个 pipeline ID
        String pipelineId = "mock-pipeline-" + System.currentTimeMillis();
        log.info("Mock: Pipeline triggered successfully with ID: {}", pipelineId);
        return pipelineId;
    }
    
    // 用于测试的辅助方法
    
    /**
     * 添加模拟分支（用于测试）
     */
    public void addMockBranch(String repoCloneUrl, String branchName) {
        mockBranches.computeIfAbsent(repoCloneUrl, k -> new HashSet<>()).add(branchName);
    }
    
    /**
     * 获取仓库的所有分支（用于测试）
     */
    public Set<String> getBranches(String repoCloneUrl) {
        return mockBranches.getOrDefault(repoCloneUrl, Set.of("master", "main"));
    }
    
    /**
     * 获取仓库的所有标签（用于测试）
     */
    public Set<String> getTags(String repoCloneUrl) {
        return mockTags.getOrDefault(repoCloneUrl, Set.of());
    }
    
    /**
     * 清除模拟数据（用于测试）
     */
    public void clearMockData() {
        mockBranches.clear();
        mockTags.clear();
    }
}
