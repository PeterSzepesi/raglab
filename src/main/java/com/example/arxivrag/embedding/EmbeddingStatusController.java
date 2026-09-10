package com.example.arxivrag.embedding;

import com.example.arxivrag.paper.PaperRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/embeddings")
public class EmbeddingStatusController {

    private final PaperRepository paperRepository;

    private final EmbeddingCheckpointRepository checkpointRepository;

    private final EmbeddingModelGateway embeddingModel;

    private final EmbeddingIngestionProgress progress;

    private final EmbeddingJobRepository jobRepository;

    private final com.example.arxivrag.vector.VectorStoreGateway vectorStoreGateway;

    public EmbeddingStatusController(
            PaperRepository paperRepository,
            EmbeddingCheckpointRepository checkpointRepository,
            EmbeddingModelGateway embeddingModel,
            EmbeddingIngestionProgress progress,
            EmbeddingJobRepository jobRepository,
            com.example.arxivrag.vector.VectorStoreGateway vectorStoreGateway
    ) {
        this.paperRepository = paperRepository;
        this.checkpointRepository = checkpointRepository;
        this.embeddingModel = embeddingModel;
        this.progress = progress;
        this.jobRepository = jobRepository;
        this.vectorStoreGateway = vectorStoreGateway;
    }

    @GetMapping("/status")
    public EmbeddingStatus status() {
        var paperCount = paperRepository.count();
        var checkpointCount = checkpointRepository.countByIdEmbeddingModel(embeddingModel.modelName());
        var checkpointedPaperCount = checkpointRepository.countDistinctPaperIdsByEmbeddingModel(embeddingModel.modelName());

        var job = progress.snapshot();
        var latestJob = jobRepository.findLatest().orElse(null);
        var vectorRows = vectorStoreGateway.countByEmbeddingModel(embeddingModel.modelName());
        var persistedJob = "IDLE".equals(job.status()) ? latestJob : null;
        var displayJobStatus = persistedJob == null ? job.status() : persistedJob.status();
        var displayJobTotal = persistedJob == null ? job.totalPapers() : persistedJob.totalPapers();
        var displayJobScanned = persistedJob == null ? job.papersScanned() : persistedJob.papersScanned();
        var displayJobPrepared = persistedJob == null ? job.documentsPrepared() : persistedJob.documentsPrepared();
        var displayJobEmbedded = persistedJob == null ? job.embedded() : persistedJob.embedded();
        var displayJobSkipped = persistedJob == null ? job.skipped() : persistedJob.skipped();
        var displayJobFailures = persistedJob == null ? job.failures() : persistedJob.failures();
        var displayJobStarted = persistedJob == null ? job.startedAt() : persistedJob.startedAt();
        var displayJobUpdated = persistedJob == null ? job.updatedAt() : persistedJob.updatedAt();
        var displayJobCompleted = persistedJob == null ? job.completedAt() : persistedJob.completedAt();
        var displayJobError = persistedJob == null ? job.lastError() : persistedJob.lastError();
        return new EmbeddingStatus(
                paperCount,
                checkpointCount,
                checkpointedPaperCount,
                paperCount == 0 ? 0.0d : (checkpointedPaperCount * 100.0d) / paperCount,
                embeddingModel.modelName(),
                embeddingModel.dimensions(),
                displayJobStatus,
                displayJobTotal,
                displayJobScanned,
                displayJobPrepared,
                displayJobEmbedded,
                displayJobSkipped,
                displayJobFailures,
                displayJobStarted,
                displayJobUpdated,
                displayJobCompleted,
                displayJobError,
                vectorRows,
                paperCount == 0 ? 0.0d : (vectorRows * 100.0d) / paperCount,
                latestJob == null ? null : latestJob.jobId(),
                latestJob == null ? null : latestJob.status(),
                latestJob == null ? null : latestJob.startedAt(),
                latestJob == null ? null : latestJob.updatedAt(),
                latestJob == null ? null : latestJob.completedAt(),
                latestJob == null ? null : latestJob.lastError(),
                latestJob == null ? 0L : latestJob.providerRequests()
        );
    }

    public record EmbeddingStatus(
            long papers,
            long checkpoints,
            long checkpointedPapers,
            double coveragePercent,
            String embeddingModel,
            int embeddingDimension,
            String jobStatus,
            long jobTotalPapers,
            long jobPapersScanned,
            long jobDocumentsPrepared,
            long jobEmbedded,
            long jobSkipped,
            long jobFailures,
            java.time.Instant jobStartedAt,
            java.time.Instant jobUpdatedAt,
            java.time.Instant jobCompletedAt,
            String jobLastError,
            long vectorRows,
            double vectorCoveragePercent,
            java.util.UUID latestJobId,
            String latestJobStatus,
            java.time.Instant latestJobStartedAt,
            java.time.Instant latestJobUpdatedAt,
            java.time.Instant latestJobCompletedAt,
            String latestJobLastError,
            long latestJobProviderRequests
    ) {
    }
}
