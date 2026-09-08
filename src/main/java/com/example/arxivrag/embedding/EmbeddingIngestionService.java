package com.example.arxivrag.embedding;

import com.example.arxivrag.paper.Paper;
import com.example.arxivrag.paper.PaperRepository;
import com.example.arxivrag.vector.EmbeddedDocument;
import com.example.arxivrag.vector.VectorStoreGateway;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class EmbeddingIngestionService {

    private final PaperRepository paperRepository;

    private final RetrievalDocumentFactory retrievalDocumentFactory;

    private final EmbeddingModelGateway embeddingModel;

    private final EmbeddingCheckpointRepository checkpointRepository;

    private final VectorStoreGateway vectorStoreGateway;

    private final EmbeddingIngestionProperties properties;

    private final TransactionTemplate transactionTemplate;

    public EmbeddingIngestionService(
            PaperRepository paperRepository,
            RetrievalDocumentFactory retrievalDocumentFactory,
            EmbeddingModelGateway embeddingModel,
            EmbeddingCheckpointRepository checkpointRepository,
            VectorStoreGateway vectorStoreGateway,
            EmbeddingIngestionProperties properties,
            TransactionTemplate transactionTemplate
    ) {
        this.paperRepository = paperRepository;
        this.retrievalDocumentFactory = retrievalDocumentFactory;
        this.embeddingModel = embeddingModel;
        this.checkpointRepository = checkpointRepository;
        this.vectorStoreGateway = vectorStoreGateway;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
    }

    public EmbeddingIngestionResult ingestAll() {
        if (properties.getBatchSize() <= 0) {
            throw new IllegalStateException("embedding.ingestion.batch-size must be greater than zero");
        }

        var startedAt = Instant.now();
        var state = new IngestionState();
        var pageRequest = PageRequest.of(0, properties.getBatchSize(), Sort.by("id"));
        var page = paperRepository.findAll(pageRequest);

        while (true) {
            processPapers(page.getContent(), state);
            if (!page.hasNext()) {
                break;
            }
            page = paperRepository.findAll(page.nextPageable());
        }

        return new EmbeddingIngestionResult(
                state.papersScanned,
                state.documentsPrepared,
                state.embedded,
                state.skipped,
                embeddingModel.modelName(),
                embeddingModel.dimensions(),
                Duration.between(startedAt, Instant.now())
        );
    }

    private void processPapers(List<Paper> papers, IngestionState state) {
        if (papers.isEmpty()) {
            return;
        }

        transactionTemplate.executeWithoutResult(status -> {
            var documents = papers.stream()
                    .map(retrievalDocumentFactory::fromPaper)
                    .toList();
            var checkpointIds = documents.stream()
                    .map(this::checkpointId)
                    .toList();
            var existingIds = new java.util.HashSet<EmbeddingCheckpointId>();
            checkpointRepository.findAllById(checkpointIds)
                    .forEach(checkpoint -> existingIds.add(checkpoint.getId()));

            var newCheckpoints = new ArrayList<EmbeddingCheckpoint>();
            for (var document : documents) {
                state.papersScanned++;
                state.documentsPrepared++;
                var checkpointId = checkpointId(document);
                if (existingIds.contains(checkpointId)) {
                    state.skipped++;
                    continue;
                }

                var vector = embeddingModel.embed(document);
                if (vector.size() != embeddingModel.dimensions()) {
                    throw new IllegalStateException("Embedding model returned " + vector.size() + " dimensions, expected " + embeddingModel.dimensions());
                }

                vectorStoreGateway.upsert(new EmbeddedDocument(
                        document.paperId(),
                        document.arxivId(),
                        document.chunkId(),
                        document.contentHash(),
                        embeddingModel.modelName(),
                        embeddingModel.dimensions(),
                        document.text(),
                        document.categories(),
                        document.submittedDate(),
                        vector
                ));
                newCheckpoints.add(new EmbeddingCheckpoint(checkpointId, vector.size()));
                state.embedded++;
            }

            checkpointRepository.saveAll(newCheckpoints);
            checkpointRepository.flush();
        });
    }

    private EmbeddingCheckpointId checkpointId(RetrievalDocument document) {
        return new EmbeddingCheckpointId(
                document.paperId(),
                document.chunkId(),
                embeddingModel.modelName(),
                document.contentHash()
        );
    }

    static class IngestionState {

        private long papersScanned;

        private long documentsPrepared;

        private long embedded;

        private long skipped;
    }
}
