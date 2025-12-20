package io.releasehub.interfaces.api.iteration;

import io.releasehub.application.iteration.IterationAppService;
import io.releasehub.common.response.ApiResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/iterations")
@RequiredArgsConstructor
public class IterationController {
    private final IterationAppService iterationAppService;

    @PostMapping
    public ApiResponse<String> create(@RequestBody CreateIterationRequest request) {
        var it = iterationAppService.create(request.getIterationKey(), request.getDescription(), request.getRepoIds());
        return ApiResponse.success(it.getId().value());
    }

    @Data
    public static class CreateIterationRequest {
        private String iterationKey;
        private String description;
        private Set<String> repoIds;
    }
}
