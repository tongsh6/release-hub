package io.releasehub.interfaces.api.releasewindow;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.releasehub.application.export.ExportAppService;
import io.releasehub.application.export.ReleaseWindowReportView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api/v1/release-windows")
@RequiredArgsConstructor
@Tag(name = "发布窗口 - 报告导出")
public class ReleaseWindowReportController {
    private final ExportAppService exportAppService;
    private final ObjectMapper objectMapper;

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

    @GetMapping(value = "/{id}/report.md", produces = "text/markdown")
    @Operation(summary = "Export release window report as Markdown")
    public ResponseEntity<String> exportMarkdown(@PathVariable("id") String id) {
        return ResponseEntity.ok(exportAppService.exportReleaseWindowMarkdown(id));
    }

    @GetMapping(value = "/{id}/report.zip", produces = "application/zip")
    @Operation(summary = "Export release window evidence package as ZIP")
    public ResponseEntity<byte[]> exportPackage(@PathVariable("id") String id) throws IOException {
        ReleaseWindowReportView report = exportAppService.exportReleaseWindowReport(id);
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("manifest.txt", buildManifest(report).getBytes(StandardCharsets.UTF_8));
        files.put("report.json", objectMapper.writeValueAsBytes(report));
        files.put("report.csv", exportAppService.exportReleaseWindowCsv(id).getBytes(StandardCharsets.UTF_8));
        files.put("report.md", exportAppService.exportReleaseWindowMarkdown(id).getBytes(StandardCharsets.UTF_8));

        byte[] zip = zip(files);
        String filename = "release-window-" + safeFilename(report.windowKey()) + "-evidence.zip";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .contentLength(zip.length)
                .body(zip);
    }

    private String buildManifest(ReleaseWindowReportView report) {
        StringJoiner manifest = new StringJoiner(System.lineSeparator());
        manifest.add("ReleaseHub 发布窗口证据制品包");
        manifest.add("windowId=" + report.windowId());
        manifest.add("windowKey=" + report.windowKey());
        manifest.add("name=" + report.name());
        manifest.add("status=" + report.status());
        manifest.add("runCount=" + report.runCount());
        manifest.add("itemCount=" + report.itemCount());
        manifest.add("stepCount=" + report.stepCount());
        manifest.add("");
        manifest.add("files:");
        manifest.add("- report.json: 结构化窗口报告，适合系统间交换");
        manifest.add("- report.csv: 表格化 Run/RunItem/RunStep 明细，适合审计复核");
        manifest.add("- report.md: 可读 Markdown 报告，适合随发布记录归档");
        manifest.add("");
        return manifest.toString();
    }

    private byte[] zip(Map<String, byte[]> files) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                ZipEntry entry = new ZipEntry(file.getKey());
                entry.setTime(0L);
                zip.putNextEntry(entry);
                zip.write(file.getValue());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private String safeFilename(String value) {
        return value == null || value.isBlank() ? "unknown" : value.replaceAll("[^A-Za-z0-9._-]", "-");
    }
}
