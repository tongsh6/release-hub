package io.releasehub.application.gitoperation;

import io.releasehub.common.exception.BusinessException;
import io.releasehub.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service @RequiredArgsConstructor
public class GitOperationAppService {
    private final GitOperationJournalPort journal;
    private final GitOperationEvidencePort evidence;
    public List<GitOperationJournalPort.Operation> list(String repoId,int page,int size) {
        validatePage(page,size);return journal.list(repoId,page,size);
    }
    public List<GitOperationJournalPort.Operation> listByRun(String runId,int page,int size) {
        validatePage(page,size);return journal.listByRun(runId,page,size);
    }
    public GitOperationJournalPort.Detail detail(String id) {
        return journal.find(id).orElseThrow(()->BusinessException.of(ErrorCode.RESOURCE_NOT_FOUND,id));
    }
    public GitOperationJournalPort.Observation reconcile(String id,String actor,String key,Long version) {
        if(key==null||key.isBlank()||key.length()>128||version==null||version<0)throw BusinessException.of(ErrorCode.GIT_OPERATION_INVALID);
        var previous=journal.observation(id,actor,key);
        if(previous.isPresent()) {
            if(previous.get().expectedVersion()!=version)throw BusinessException.of(ErrorCode.GIT_OPERATION_STALE);
            return previous.get();
        }
        var detail=detail(id);
        if(detail.operation().version()!=version)throw BusinessException.of(ErrorCode.GIT_OPERATION_STALE);
        // 网络读取在账本事务之外，提交时再次验证版本；同键并发由持久层收敛。
        var result=evidence.read(detail);
        return journal.observe(id,actor,key,version,result.status(),result.evidence()==null?"{}":result.evidence());
    }
    private void validatePage(int page,int size) {
        if(page<0||page>10000||size<1||size>50)throw BusinessException.of(ErrorCode.GIT_OPERATION_INVALID);
    }
}
