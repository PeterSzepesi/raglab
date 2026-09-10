package com.example.arxivrag.embedding;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/embeddings/jobs")
public class EmbeddingJobController {

    private final EmbeddingJobManager jobManager;
    private final EmbeddingJobRepository jobRepository;

    public EmbeddingJobController(EmbeddingJobManager jobManager, EmbeddingJobRepository jobRepository) {
        this.jobManager = jobManager;
        this.jobRepository = jobRepository;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> start(@RequestBody(required = false) StartRequest request) {
        var input = request == null ? new StartRequest(null, null, null, null, null, null, false) : request;
        var operationId = jobManager.start(new EmbeddingIngestionRequest(
                input.maxPapers(), input.fromPaperId(), input.toPaperId(), input.category(),
                input.fromDate(), input.toDate(), input.dryRun()
        ));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "operationId", operationId,
                "status", "QUEUED",
                "message", "Embedding job accepted; poll /api/embeddings/status for progress."
        ));
    }

    @GetMapping("/latest")
    public ResponseEntity<EmbeddingJob> latest() {
        return jobRepository.findLatest()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<EmbeddingJob> find(@PathVariable UUID jobId) {
        return jobRepository.findById(jobId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/current")
    public ResponseEntity<Map<String, Object>> cancel() {
        var cancelled = jobManager.cancel();
        return ResponseEntity.ok(Map.of(
                "cancelled", cancelled,
                "operationId", jobManager.operationId() == null ? "" : jobManager.operationId()
        ));
    }

    public record StartRequest(
            Integer maxPapers,
            Long fromPaperId,
            Long toPaperId,
            String category,
            LocalDate fromDate,
            LocalDate toDate,
            boolean dryRun
    ) {
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> conflict(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", exception.getMessage()));
    }
}
