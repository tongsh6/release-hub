package io.releasehub.interfaces.api.run;

import io.releasehub.application.export.ExportAppService;
import io.releasehub.application.export.RunJsonView;
import io.releasehub.application.run.RunPort;
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
@RequestMapping("/api/v1/runs")
@RequiredArgsConstructor
@Tag(name = "Runs")
public class RunExportController {
    private final ExportAppService exportAppService;
    private final RunPort runPort;

    @GetMapping(value = "/{id}/export.csv", produces = "text/csv")
    @Operation(summary = "Export run as CSV")
    public ResponseEntity<String> exportCsv(@PathVariable("id") String id) {
        return ResponseEntity.ok(exportAppService.exportCsv(id));
    }

    @GetMapping(value = "/{id}/export.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Export run as JSON")
    public ResponseEntity<RunJsonView> exportJson(@PathVariable("id") String id) {
        var run = runPort.findById(id).orElseThrow();
        return ResponseEntity.ok(RunJsonView.from(run));
    }
}
