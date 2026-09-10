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

    public EmbeddingStatusController(
            PaperRepository paperRepository,
            EmbeddingCheckpointRepository checkpointRepository,
            EmbeddingModelGateway embeddingModel,
            EmbeddingIngestionProgress progress
    ) {
        this.paperRepository = paperRepository;
        this.checkpointRepository = checkpointRepository;
        this.embeddingModel = embeddingModel;
        this.progress = progress;
    }

    @GetMapping("/status")
    public EmbeddingStatus status() {
        var paperCount = paperRepository.count();
        var checkpointCount = checkpointRepository.countByIdEmbeddingModel(embeddingModel.modelName());
        var checkpointedPaperCount = checkpointRepository.countDistinctPaperIdsByEmbeddingModel(embeddingModel.modelName());

        var job = progress.snapshot();
        return new EmbeddingStatus(
                paperCount,
                checkpointCount,
                checkpointedPaperCount,
                paperCount == 0 ? 0.0d : (checkpointedPaperCount * 100.0d) / paperCount,
                embeddingModel.modelName(),
                embeddingModel.dimensions(),
                job.status(),
                job.totalPapers(),
                job.papersScanned(),
                job.documentsPrepared(),
                job.embedded(),
                job.skipped(),
                job.failures(),
                job.startedAt(),
                job.updatedAt(),
                job.completedAt(),
                job.lastError()
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
            String jobLastError
    ) {
    }
}
