package io.releasehub.interfaces.rest;

import io.releasehub.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author tongshuanglong
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Ping")
public class PingController {

    @GetMapping("/ping")
    @Operation(summary = "Ping")
    public ApiResponse<String> ping() {
        return ApiResponse.success("pong");
    }
}
