package io.releasehub.interfaces.api.run;

import io.releasehub.application.gitoperation.GitOperationAppService;
import io.releasehub.application.gitoperation.GitOperationJournalPort;
import io.releasehub.common.exception.BusinessException;
import io.releasehub.common.exception.ErrorCode;
import io.releasehub.common.response.ApiResponse;
import io.releasehub.interfaces.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/v1") @RequiredArgsConstructor
public class GitOperationController {
    private final GitOperationAppService service;
    @GetMapping("/repositories/{id}/operations")
    public ApiResponse<List<GitOperationJournalPort.Operation>> list(@PathVariable String id,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ApiResponse.success(service.list(id,page,size));
    }
    @GetMapping("/runs/{id}/operations")
    public ApiResponse<List<GitOperationJournalPort.Operation>> listByRun(@PathVariable String id,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ApiResponse.success(service.listByRun(id,page,size));
    }
    @GetMapping("/git-operations/{id}")
    public ApiResponse<GitOperationJournalPort.Detail> detail(@PathVariable String id) {
        return ApiResponse.success(service.detail(id));
    }
    @PostMapping("/git-operations/{id}/reconcile")
    public ApiResponse<GitOperationJournalPort.Observation> reconcile(@PathVariable String id,
            @RequestHeader(value="Idempotency-Key",required=false) String key,@RequestBody(required=false) ReconcileRequest request) {
        if(request==null)throw BusinessException.of(ErrorCode.GIT_OPERATION_INVALID);
        return ApiResponse.success(service.reconcile(id,SecurityUtils.getCurrentUsername(),key,request.expectedStateVersion()));
    }
    public record ReconcileRequest(Long expectedStateVersion) {}
}
