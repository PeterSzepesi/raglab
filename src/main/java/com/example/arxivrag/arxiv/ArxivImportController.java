package com.example.arxivrag.arxiv;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping({"/api/imports", "/api/v1/imports"})
@ConditionalOnProperty(name = "rag.features.import-enabled", havingValue = "true")
public class ArxivImportController {
    private static final long MAX_UPLOAD_BYTES = 25L * 1024 * 1024;
    private final ArxivImportService imports;
    private final ArxivImportJobManager jobs;

    public ArxivImportController(ArxivImportService imports, ArxivImportJobManager jobs) {
        this.imports = imports;
        this.jobs = jobs;
    }

    @PostMapping("/preview")
    public ArxivImportService.ArxivImportPreview preview(@Valid @RequestBody ImportRequest request) {
        return imports.previewDataset(Path.of(request.sourcePath()), request.options());
    }

    @PostMapping("/jobs")
    public ArxivImportJob start(@Valid @RequestBody ImportRequest request) {
        return jobs.start(Path.of(request.sourcePath()), request.options());
    }

    @PostMapping(value = "/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ArxivImportJob upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) List<String> categories,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @Positive @Max(1_000_000) Integer maxRecords) throws Exception {
        if (file.isEmpty() || file.getSize() > MAX_UPLOAD_BYTES) {
            throw new IllegalArgumentException("Upload must contain 1 byte to 25 MiB");
        }
        var uploadRoot = Path.of("data", "uploads").toAbsolutePath().normalize();
        Files.createDirectories(uploadRoot);
        var target = uploadRoot.resolve(UUID.randomUUID() + ".jsonl");
        try (var input = file.getInputStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return jobs.start(target, new ArxivImportOptions(categories, fromDate, maxRecords));
    }

    @GetMapping("/jobs")
    public List<ArxivImportJob> recent(@RequestParam(defaultValue = "20") int limit) {
        return jobs.recent(limit);
    }

    @GetMapping("/jobs/{id}")
    public ArxivImportJob find(@PathVariable UUID id) { return jobs.find(id); }

    @PostMapping("/jobs/{id}/resume")
    public ArxivImportJob resume(@PathVariable UUID id) { return jobs.resume(id); }

    @DeleteMapping("/jobs/{id}")
    public ArxivImportJob cancel(@PathVariable UUID id) { return jobs.cancel(id); }

    public record ImportRequest(@NotBlank String sourcePath, List<String> categories, LocalDate fromDate,
                                @Positive @Max(1_000_000) Integer maxRecords) {
        ArxivImportOptions options() { return new ArxivImportOptions(categories, fromDate, maxRecords); }
    }
}
