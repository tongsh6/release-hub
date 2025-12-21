package io.releasehub.interfaces.api.run;

import io.releasehub.application.run.RunPort;
import io.releasehub.common.paging.PageMeta;
import io.releasehub.common.response.ApiPageResponse;
import io.releasehub.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/runs")
@RequiredArgsConstructor
@Tag(name = "Runs")
public class RunController {
    private final RunPort runPort;

    @GetMapping("/{id}")
    @Operation(summary = "Get run")
    public ApiResponse<Object> get(@PathVariable("id") String id) {
        return ApiResponse.success(runPort.findById(id).orElse(null));
    }

    @GetMapping
    @Operation(summary = "List runs")
    public ApiResponse<Object> list() {
        return ApiResponse.success(runPort.findAll());
    }

    @GetMapping("/paged")
    @Operation(summary = "List runs (paged)")
    public ApiPageResponse<java.util.List<io.releasehub.domain.run.Run>> listPaged(@RequestParam(name = "page", defaultValue = "0") int page,
                                                                                   @RequestParam(name = "size", defaultValue = "20") int size) {
        java.util.List<io.releasehub.domain.run.Run> all = runPort.findAll();
        int from = Math.max(page * size, 0);
        int to = Math.min(from + size, all.size());
        java.util.List<io.releasehub.domain.run.Run> slice = from >= all.size() ? java.util.List.<io.releasehub.domain.run.Run>of() : all.subList(from, to);
        return ApiPageResponse.success(slice, new PageMeta(page, size, all.size()));
    }
}
