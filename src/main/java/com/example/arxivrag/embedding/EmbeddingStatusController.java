package com.example.arxivrag.embedding;

import com.example.arxivrag.paper.PaperRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/embeddings", "/api/v1/embeddings"})
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "rag.features.vector-stores-enabled", havingValue = "true")
public class EmbeddingStatusController {

    private final PaperRepository paperRepository;

    private final EmbeddingCheckpointRepository checkpointRepository;

    private final EmbeddingModelGateway embeddingModel;

    private final EmbeddingIngestionProgress progress;

    private final EmbeddingJobRepository jobRepository;

    private final com.example.arxivrag.vector.VectorStoreGateway vectorStoreGateway;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final org.springframework.beans.factory.ObjectProvider<
            com.example.arxivrag.ai.foundry.FoundryQuotaLimiterRegistry> quotaLimiters;

    public EmbeddingStatusController(
            PaperRepository paperRepository,
            EmbeddingCheckpointRepository checkpointRepository,
            EmbeddingModelGateway embeddingModel,
            EmbeddingIngestionProgress progress,
            EmbeddingJobRepository jobRepository,
            com.example.arxivrag.vector.VectorStoreGateway vectorStoreGateway,
            org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
            org.springframework.beans.factory.ObjectProvider<
                    com.example.arxivrag.ai.foundry.FoundryQuotaLimiterRegistry> quotaLimiters
    ) {
        this.paperRepository = paperRepository;
        this.checkpointRepository = checkpointRepository;
        this.embeddingModel = embeddingModel;
        this.progress = progress;
        this.jobRepository = jobRepository;
        this.vectorStoreGateway = vectorStoreGateway;
        this.jdbcTemplate = jdbcTemplate;
        this.quotaLimiters = quotaLimiters;
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
        var capacity = quotaLimiters.getIfAvailable() == null
                ? com.example.arxivrag.ai.foundry.FoundryQuotaLimiterRegistry.CapacitySnapshot.offline()
                : quotaLimiters.getIfAvailable().capacity();
        var displayJobStatus = persistedJob == null ? job.status() : persistedJob.status();
        if ("RUNNING".equals(displayJobStatus) && capacity.bulkEmbeddingWaiting() > 0) {
            displayJobStatus = "WAITING_FOR_PROVIDER_CAPACITY";
        }
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
                latestJob == null ? 0L : latestJob.providerRequests(),
                RetrievalDocumentFactory.DOCUMENT_SCHEMA_VERSION,
                "hnsw-cosine-v1",
                indexDiagnostics(),
                capacity
        );
    }

    private IndexDiagnostics indexDiagnostics() {
        try {
            var sizes = jdbcTemplate.queryForMap("""
                    select pg_total_relation_size('paper_embeddings') as table_bytes,
                           pg_indexes_size('paper_embeddings') as index_bytes
                    """);
            var definitions = jdbcTemplate.queryForList("""
                    select indexdef from pg_indexes
                    where schemaname = current_schema() and tablename = 'paper_embeddings'
                    order by indexname
                    """, String.class);
            return new IndexDiagnostics(((Number) sizes.get("table_bytes")).longValue(),
                    ((Number) sizes.get("index_bytes")).longValue(), definitions);
        } catch (RuntimeException ignored) {
            return new IndexDiagnostics(null, null, java.util.List.of());
        }
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
            long latestJobProviderRequests,
            String retrievalDocumentVersion,
            String indexVersion,
            IndexDiagnostics indexDiagnostics,
            com.example.arxivrag.ai.foundry.FoundryQuotaLimiterRegistry.CapacitySnapshot providerCapacity
    ) {
    }

    public record IndexDiagnostics(Long tableBytes, Long indexBytes, java.util.List<String> definitions) { }
}
