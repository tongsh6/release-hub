package io.releasehub.interfaces.api.releasewindow;

import io.releasehub.application.export.ExportAppService;
import io.releasehub.application.export.ReleaseWindowReportView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/release-windows")
@RequiredArgsConstructor
@Tag(name = "发布窗口 - 报告导出")
public class ReleaseWindowReportController {
    private final ExportAppService exportAppService;

    @GetMapping(value = "/{id}/report.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Export release window report as JSON")
    public ResponseEntity<ReleaseWindowReportView> exportJson(@PathVariable("id") String id) {
        return ResponseEntity.ok(exportAppService.exportReleaseWindowReport(id));
    }

    @GetMapping(value = "/{id}/report.csv", produces = "text/csv")
    @Operation(summary = "Export release window report as CSV")
    public ResponseEntity<String> exportCsv(@PathVariable("id") String id) {
        return ResponseEntity.ok(exportAppService.exportReleaseWindowCsv(id));
    }
}
