package io.releasehub.interfaces.api.dataquality;

import io.releasehub.application.dataquality.DataQualityDispositionCaseAppService;
import io.releasehub.application.dataquality.DataQualityDispositionCaseView;
import io.releasehub.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/data-quality/disposition-cases")
@RequiredArgsConstructor
@Tag(name = "数据质量 - 处置执行审计")
public class DataQualityDispositionCaseController {
    private final DataQualityDispositionCaseAppService appService;

    @PostMapping
    @Operation(summary = "Create a data quality disposition audit case")
    public ApiResponse<DataQualityDispositionCaseView> create(@RequestBody @Valid DispositionCaseCreateRequest request) {
        return ApiResponse.success(appService.create(request.toCommand()));
    }

    @GetMapping
    @Operation(summary = "List data quality disposition audit cases")
    public ApiResponse<List<DataQualityDispositionCaseView>> list() {
        return ApiResponse.success(appService.list());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get data quality disposition audit case")
    public ApiResponse<DataQualityDispositionCaseView> get(@PathVariable String id) {
        return ApiResponse.success(appService.get(id));
    }

    @PostMapping("/{id}/start")
    @Operation(summary = "Mark a disposition case as manually handling")
    public ApiResponse<DataQualityDispositionCaseView> start(@PathVariable String id,
                                                             @RequestBody @Valid DispositionCaseTransitionRequest request) {
        return ApiResponse.success(appService.start(id, request.toCommand()));
    }

    @PostMapping("/{id}/verify")
    @Operation(summary = "Record post-handling verification evidence")
    public ApiResponse<DataQualityDispositionCaseView> verify(@PathVariable String id,
                                                              @RequestBody @Valid DispositionCaseTransitionRequest request) {
        return ApiResponse.success(appService.verify(id, request.toCommand()));
    }

    @PostMapping("/{id}/fail")
    @Operation(summary = "Record failed disposition handling")
    public ApiResponse<DataQualityDispositionCaseView> fail(@PathVariable String id,
                                                            @RequestBody @Valid DispositionCaseTransitionRequest request) {
        return ApiResponse.success(appService.fail(id, request.toCommand()));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel disposition case handling")
    public ApiResponse<DataQualityDispositionCaseView> cancel(@PathVariable String id,
                                                              @RequestBody @Valid DispositionCaseTransitionRequest request) {
        return ApiResponse.success(appService.cancel(id, request.toCommand()));
    }
}
