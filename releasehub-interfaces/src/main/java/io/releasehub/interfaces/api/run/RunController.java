package io.releasehub.interfaces.api.run;

import io.releasehub.application.run.RunPort;
import io.releasehub.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/runs")
@RequiredArgsConstructor
public class RunController {
    private final RunPort runPort;

    @GetMapping("/{id}")
    public ApiResponse<Object> get(@PathVariable("id") String id) {
        return ApiResponse.success(runPort.findById(id).orElse(null));
    }

    @GetMapping
    public ApiResponse<Object> list() {
        return ApiResponse.success(runPort.findAll());
    }
}
