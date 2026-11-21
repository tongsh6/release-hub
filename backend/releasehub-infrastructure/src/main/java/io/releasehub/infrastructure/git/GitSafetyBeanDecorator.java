package io.releasehub.infrastructure.git;

import io.releasehub.application.gitoperation.GitOperationJournalPort;
import io.releasehub.application.port.out.GitBranchPort;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.application.settings.SettingsPort;
import io.releasehub.application.version.VersionUpdaterPort;
import io.releasehub.application.version.VersionUpdateRequest;
import io.releasehub.common.exception.BusinessException;
import io.releasehub.common.exception.ErrorCode;
import io.releasehub.domain.repo.CodeRepository;
import io.releasehub.domain.repo.GitProvider;
import io.releasehub.domain.repo.RepoId;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import java.util.Objects;

/** 保护所有 Spring 装配的分支入口、配置改向和本地文件回退。 */
@Component
public class GitSafetyBeanDecorator implements BeanPostProcessor {
    private final ObjectProvider<GitSafetyProperties> properties;
    private final ObjectProvider<GitSafetySessions> sessions;
    private final ObjectProvider<GitOperationJournalPort> journal;
    private final ObjectProvider<CodeRepositoryPort> repos;
    private final ObjectProvider<org.springframework.transaction.PlatformTransactionManager> transactions;
    public GitSafetyBeanDecorator(ObjectProvider<GitSafetyProperties> properties,ObjectProvider<GitSafetySessions> sessions,
                                  ObjectProvider<GitOperationJournalPort> journal,ObjectProvider<CodeRepositoryPort> repos,
                                  ObjectProvider<org.springframework.transaction.PlatformTransactionManager> transactions) {
        this.properties=properties;this.sessions=sessions;this.journal=journal;this.repos=repos;this.transactions=transactions;
    }
    @Override public Object postProcessAfterInitialization(Object bean,String name) {
        if(!(bean instanceof GitBranchPort || bean instanceof io.releasehub.application.port.out.GitLabFilePort || bean instanceof CodeRepositoryPort || bean instanceof SettingsPort || bean instanceof VersionUpdaterPort))return bean;
        var proxy=new ProxyFactory(bean);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice((MethodInterceptor) invocation -> {
            String method=invocation.getMethod().getName();
            if(!properties.getObject().isEnabled())return invocation.proceed();
            Class<?> type=org.springframework.aop.support.AopUtils.getTargetClass(bean);
            if(type==GitHubGitBranchAdapter.class&&java.util.Set.of("createBranch","deleteBranch","mergeBranch","createTag","archiveBranch","triggerPipeline","checkMergeability").contains(method)) {
                throw BusinessException.of(ErrorCode.GIT_PROVIDER_UNSUPPORTED);
            }
            if(!method.equals("supports")&&(type==MockGitBranchAdapter.class||type==InMemoryGitLabBranchAdapter.class
                    ||type==io.releasehub.infrastructure.gitlab.InMemoryGitLabFileAdapter.class))throw blocked();
            if(bean instanceof CodeRepositoryPort && (method.equals("save") || method.equals("deleteById"))
                    || bean instanceof SettingsPort && method.equals("saveGitLab")) {
                var tx=new org.springframework.transaction.support.TransactionTemplate(transactions.getObject());
                return tx.execute(status->{
                    journal.getObject().lockMappingChanges();
                    boolean changesMapping=method.equals("deleteById");
                    if(bean instanceof CodeRepositoryPort repository && method.equals("save")) {
                        var next=(CodeRepository)invocation.getArguments()[0];
                        var previous=repository.findById(next.getId());
                        changesMapping=previous.isPresent()&&(!Objects.equals(previous.get().getCloneUrl(),next.getCloneUrl())
                                ||previous.get().getGitProvider()!=next.getGitProvider());
                    }
                    if(bean instanceof SettingsPort settings) {
                        var next=(SettingsPort.SettingsGitLab)invocation.getArguments()[0];
                        changesMapping=settings.getGitLab().isEmpty()||!Objects.equals(settings.getGitLab().get().baseUrl(),next.baseUrl());
                    }
                    if(changesMapping&&journal.getObject().hasActiveGuards())throw blocked();
                    // 同目标凭据更新不解除保留，核对仍验证稳定项目 ID。
                    try{return invocation.proceed();}catch(RuntimeException|Error failure){throw failure;}
                    catch(Throwable failure){throw new IllegalStateException(failure);}
                });
            }
            if(bean instanceof VersionUpdaterPort && method.equals("update")) {
                var request=(VersionUpdateRequest)invocation.getArguments()[0];
                var repo=request.repoId()==null?java.util.Optional.<CodeRepository>empty():repos.getObject().findById(request.repoId());
                if(request.branchName()==null || repo.isEmpty() || repo.get().getGitProvider()!=GitProvider.GITLAB)throw blocked();
            }
            if((bean instanceof GitBranchPort || bean instanceof io.releasehub.application.port.out.GitLabFilePort) && !method.equals("supports")) {
                try(var scope=sessions.getObject().open()) {
                    try {
                        Object result=invocation.proceed();
                        if(result instanceof GitBranchPort.MergeResult merge
                                && merge.status()==io.releasehub.domain.run.MergeStatus.UNKNOWN)scope.failed();
                        return result;
                    }catch(Throwable failure){scope.failed();throw failure;}
                }
            }
            return invocation.proceed();
        });
        return proxy.getProxy();
    }
    private BusinessException blocked(){return BusinessException.of(ErrorCode.GIT_WRITE_BLOCKED);}
}
