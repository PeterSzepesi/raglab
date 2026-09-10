package com.example.arxivrag.embedding;

import com.example.arxivrag.paper.Paper;
import com.example.arxivrag.paper.PaperRepository;
import com.example.arxivrag.vector.EmbeddedDocument;
import com.example.arxivrag.vector.VectorStoreGateway;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class EmbeddingIngestionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmbeddingIngestionService.class);

    private final PaperRepository paperRepository;

    private final RetrievalDocumentFactory retrievalDocumentFactory;

    private final EmbeddingModelGateway embeddingModel;

    private final EmbeddingCheckpointRepository checkpointRepository;

    private final VectorStoreGateway vectorStoreGateway;

    private final EmbeddingIngestionProperties properties;

    private final TransactionTemplate transactionTemplate;

    private final EmbeddingIngestionProgress progress;

    public EmbeddingIngestionService(
            PaperRepository paperRepository,
            RetrievalDocumentFactory retrievalDocumentFactory,
            EmbeddingModelGateway embeddingModel,
            EmbeddingCheckpointRepository checkpointRepository,
            VectorStoreGateway vectorStoreGateway,
            EmbeddingIngestionProperties properties,
            TransactionTemplate transactionTemplate,
            EmbeddingIngestionProgress progress
    ) {
        this.paperRepository = paperRepository;
        this.retrievalDocumentFactory = retrievalDocumentFactory;
        this.embeddingModel = embeddingModel;
        this.checkpointRepository = checkpointRepository;
        this.vectorStoreGateway = vectorStoreGateway;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
        this.progress = progress;
    }

    public EmbeddingIngestionResult ingestAll() {
        if (properties.getBatchSize() <= 0) {
            throw new IllegalStateException("embedding.ingestion.batch-size must be greater than zero");
        }
        if (properties.getProgressLogInterval() <= 0) {
            throw new IllegalStateException("embedding.ingestion.progress-log-interval must be greater than zero");
        }

        var startedAt = Instant.now();
        var state = new IngestionState();
        var totalPapers = paperRepository.count();
        progress.start(totalPapers, embeddingModel.modelName(), embeddingModel.dimensions(), properties.getBatchSize());
        LOGGER.info("Starting embedding ingestion: papers={}, model={}, dimension={}, databaseBatchSize={}, providerBatching=single-document",
                totalPapers, embeddingModel.modelName(), embeddingModel.dimensions(), properties.getBatchSize());

        try {
            var pageRequest = PageRequest.of(0, properties.getBatchSize(), Sort.by("id"));
            var page = paperRepository.findAll(pageRequest);

            while (true) {
                processPapers(page.getContent(), state);
                if (!page.hasNext()) {
                    break;
                }
                page = paperRepository.findAll(page.nextPageable());
            }

            progress.complete();
            LOGGER.info("Embedding ingestion complete: scanned={}, embedded={}, skipped={}, elapsedSeconds={}",
                    state.papersScanned, state.embedded, state.skipped,
                    Duration.between(startedAt, Instant.now()).toMillis() / 1000.0);
            return new EmbeddingIngestionResult(
                    state.papersScanned,
                    state.documentsPrepared,
                    state.embedded,
                    state.skipped,
                    embeddingModel.modelName(),
                    embeddingModel.dimensions(),
                    Duration.between(startedAt, Instant.now())
            );
        } catch (RuntimeException exception) {
            progress.fail(exception);
            LOGGER.error("Embedding ingestion failed after scanned={}, embedded={}, skipped={}: {}",
                    state.papersScanned, state.embedded, state.skipped, exception.getMessage(), exception);
            throw exception;
        }
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
                progress.markScanned();
                var checkpointId = checkpointId(document);
                if (existingIds.contains(checkpointId)) {
                    state.skipped++;
                    progress.markSkipped();
                    logProgressIfDue();
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
                progress.markEmbedded();
                logProgressIfDue();
            }

            checkpointRepository.saveAll(newCheckpoints);
            checkpointRepository.flush();
        });
    }

    private void logProgressIfDue() {
        var snapshot = progress.snapshot();
        if (snapshot.papersScanned() % properties.getProgressLogInterval() == 0
                || snapshot.papersScanned() == snapshot.totalPapers()) {
            var percent = snapshot.totalPapers() == 0
                    ? 100.0d
                    : snapshot.papersScanned() * 100.0d / snapshot.totalPapers();
            LOGGER.info("Embedding progress: {}/{} papers scanned ({}%), embedded={}, skipped={}, model={}, dimension={}",
                    snapshot.papersScanned(), snapshot.totalPapers(), String.format(Locale.ROOT, "%.1f", percent),
                    snapshot.embedded(), snapshot.skipped(), snapshot.embeddingModel(), snapshot.embeddingDimension());
        }
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
