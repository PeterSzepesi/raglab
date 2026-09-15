package com.example.arxivrag.embedding;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/embeddings/jobs", "/api/v1/embeddings/jobs"})
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "rag.features.vector-stores-enabled", havingValue = "true")
public class EmbeddingJobController {

    private final EmbeddingJobManager jobManager;
    private final EmbeddingJobRepository jobRepository;

    public EmbeddingJobController(EmbeddingJobManager jobManager, EmbeddingJobRepository jobRepository) {
        this.jobManager = jobManager;
        this.jobRepository = jobRepository;
    }

    @PostMapping
    public ResponseEntity<JobAccepted> start(@RequestBody(required = false) StartRequest request) {
        var input = request == null ? new StartRequest(null, null, null, null, null, null, false) : request;
        var operationId = jobManager.start(new EmbeddingIngestionRequest(
                input.maxPapers(), input.fromPaperId(), input.toPaperId(), input.category(),
                input.fromDate(), input.toDate(), input.dryRun()
        ));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new JobAccepted(operationId, "QUEUED",
                "Embedding job accepted; poll /api/v1/embeddings/status for progress."));
    }

    @GetMapping("/latest")
    public ResponseEntity<EmbeddingJob> latest() {
        return jobRepository.findLatest()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping
    public List<EmbeddingJob> recent() {
        return jobRepository.findRecent(20);
    }

    @PostMapping("/{jobId}/resume")
    public ResponseEntity<JobAccepted> resume(@PathVariable UUID jobId) {
        jobManager.resume(jobId);
        return ResponseEntity.accepted().body(new JobAccepted(jobId, "QUEUED", "Embedding job resumed."));
    }

    @DeleteMapping("/{jobId}")
    public ResponseEntity<JobCancellation> cancel(@PathVariable UUID jobId) {
        return ResponseEntity.ok(new JobCancellation(jobId, jobManager.cancel(jobId)));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<EmbeddingJob> find(@PathVariable UUID jobId) {
        return jobRepository.findById(jobId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/current")
    public ResponseEntity<JobCancellation> cancel() {
        var cancelled = jobManager.cancel();
        return ResponseEntity.ok(new JobCancellation(jobManager.operationId(), cancelled));
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

    public record JobAccepted(UUID operationId, String status, String message) { }

    public record JobCancellation(UUID operationId, boolean cancelled) { }

}
