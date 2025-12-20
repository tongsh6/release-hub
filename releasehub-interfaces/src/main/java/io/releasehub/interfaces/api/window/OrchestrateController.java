package io.releasehub.interfaces.api.window;

import io.releasehub.application.run.RunAppService;
import io.releasehub.common.response.ApiResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/windows")
@RequiredArgsConstructor
public class OrchestrateController {
    private final RunAppService runAppService;

    @PostMapping("/{id}/orchestrate")
    public ApiResponse<String> orchestrate(@PathVariable("id") String windowId, @RequestBody OrchestrateRequest request) {
        var run = runAppService.startOrchestrate(windowId, request.getRepoIds(), request.getIterationKeys(), request.isFailFast(), request.getOperator());
        return ApiResponse.success(run.getId());
    }

    @Data
    public static class OrchestrateRequest {
        private List<String> repoIds;
        private List<String> iterationKeys;
        private boolean failFast = true;
        private String operator;
    }
}
