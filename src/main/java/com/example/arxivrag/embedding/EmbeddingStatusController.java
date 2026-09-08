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

    public EmbeddingStatusController(
            PaperRepository paperRepository,
            EmbeddingCheckpointRepository checkpointRepository,
            EmbeddingModelGateway embeddingModel
    ) {
        this.paperRepository = paperRepository;
        this.checkpointRepository = checkpointRepository;
        this.embeddingModel = embeddingModel;
    }

    @GetMapping("/status")
    public EmbeddingStatus status() {
        var paperCount = paperRepository.count();
        var checkpointCount = checkpointRepository.countByIdEmbeddingModel(embeddingModel.modelName());
        var checkpointedPaperCount = checkpointRepository.countDistinctPaperIdsByEmbeddingModel(embeddingModel.modelName());

        return new EmbeddingStatus(
                paperCount,
                checkpointCount,
                checkpointedPaperCount,
                paperCount == 0 ? 0.0d : (checkpointedPaperCount * 100.0d) / paperCount,
                embeddingModel.modelName(),
                embeddingModel.dimensions()
        );
    }

    public record EmbeddingStatus(
            long papers,
            long checkpoints,
            long checkpointedPapers,
            double coveragePercent,
            String embeddingModel,
            int embeddingDimension
    ) {
    }
}
