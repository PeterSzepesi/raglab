package com.example.arxivrag.embedding;

import com.example.arxivrag.paper.Paper;
import com.example.arxivrag.paper.PaperRepository;
import com.example.arxivrag.search.CorpusGeneration;
import com.example.arxivrag.vector.EmbeddedDocument;
import com.example.arxivrag.vector.VectorStoreGateway;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final EmbeddingJobRepository jobRepository;
    private final EmbeddingJobLock jobLock;
    private final EmbeddingMetrics metrics;
    private final CorpusGeneration corpusGeneration;
    private final String ownerId = UUID.randomUUID().toString();

    public EmbeddingIngestionService(
            PaperRepository paperRepository,
            RetrievalDocumentFactory retrievalDocumentFactory,
            EmbeddingModelGateway embeddingModel,
            EmbeddingCheckpointRepository checkpointRepository,
            VectorStoreGateway vectorStoreGateway,
            EmbeddingIngestionProperties properties,
            TransactionTemplate transactionTemplate,
            EmbeddingIngestionProgress progress,
            EmbeddingJobRepository jobRepository,
            EmbeddingJobLock jobLock,
            EmbeddingMetrics metrics,
            CorpusGeneration corpusGeneration
    ) {
        this.paperRepository = paperRepository;
        this.retrievalDocumentFactory = retrievalDocumentFactory;
        this.embeddingModel = embeddingModel;
        this.checkpointRepository = checkpointRepository;
        this.vectorStoreGateway = vectorStoreGateway;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
        this.progress = progress;
        this.jobRepository = jobRepository;
        this.jobLock = jobLock;
        this.metrics = metrics;
        this.corpusGeneration = corpusGeneration;
    }

    public EmbeddingIngestionResult ingestAll() {
        return ingest(EmbeddingIngestionRequest.from(properties));
    }

    public EmbeddingIngestionResult ingest(EmbeddingIngestionRequest request) {
        return ingest(request, null);
    }

    public UUID prepare(EmbeddingIngestionRequest request) {
        validateConfiguration(request);
        return jobRepository.createQueued(request, embeddingModel.modelName(), embeddingModel.dimensions(),
                RetrievalDocumentFactory.DOCUMENT_SCHEMA_VERSION);
    }

    public EmbeddingIngestionResult ingest(EmbeddingIngestionRequest request, UUID preparedJobId) {
        validateConfiguration(request);
        try (var ignored = jobLock.acquire()) {
            return ingestWithLock(request, preparedJobId);
        }
    }

    private EmbeddingIngestionResult ingestWithLock(EmbeddingIngestionRequest request, UUID preparedJobId) {
        var startedAt = Instant.now();
        var candidateCount = paperRepository.countEmbeddingCandidates(
                request.fromPaperId(), request.toPaperId(), request.category(), request.fromDate(), request.toDate());
        var totalPapers = request.maxPapers() == null
                ? candidateCount
                : Math.min(candidateCount, request.maxPapers());
        var jobId = preparedJobId == null ? prepare(request) : preparedJobId;
        if (!jobRepository.claim(jobId, totalPapers, ownerId)) {
            throw new IllegalStateException("Embedding job " + jobId + " is not claimable");
        }
        var state = new IngestionState();
        progress.start(jobId, totalPapers, embeddingModel.modelName(), embeddingModel.dimensions(), properties.getBatchSize());
        LOGGER.info("Starting embedding job {}: papers={}, model={}, dimension={}, databaseBatchSize={}, providerBatchSize={}, filters={}",
                jobId, totalPapers, embeddingModel.modelName(), embeddingModel.dimensions(), properties.getBatchSize(),
                properties.getProviderBatchSize(), request.describeFilters());

        try {
            if (!request.dryRun()) {
                Long afterId = null;
                while (state.papersScanned < totalPapers) {
                    checkCancellation(jobId);
                    var pageSize = (int) Math.min(properties.getBatchSize(), totalPapers - state.papersScanned);
                    var papers = paperRepository.findEmbeddingPage(
                            afterId, request.fromPaperId(), request.toPaperId(), request.category(),
                            request.fromDate(), request.toDate(), PageRequest.of(0, pageSize, Sort.by("id"))
                    );
                    if (papers.isEmpty()) {
                        break;
                    }
                    processPage(papers, state, jobId);
                    afterId = papers.getLast().getId();
                    progress.markLastPaper(afterId);
                    jobRepository.updateProgress(jobId, ownerId, progress.snapshot(), afterId);
                }
            } else {
                state.papersScanned = totalPapers;
                state.documentsPrepared = totalPapers;
                for (var index = 0; index < totalPapers; index++) {
                    progress.markScanned();
                }
                jobRepository.updateProgress(jobId, ownerId, progress.snapshot(), null);
                LOGGER.info("Embedding dry run {}: eligible papers={}", jobId, totalPapers);
            }

            progress.complete();
            jobRepository.complete(jobId, ownerId, progress.snapshot());
            var elapsed = Duration.between(startedAt, Instant.now());
            if (state.embedded > 0) {
                corpusGeneration.increment();
                try {
                    vectorStoreGateway.analyze();
                } catch (RuntimeException exception) {
                    LOGGER.warn("Embedding data is persisted, but post-ingestion ANALYZE failed: {}",
                            exception.getMessage());
                }
            }
            LOGGER.info("Embedding job {} complete: scanned={}, embedded={}, skipped={}, providerRequests={}, elapsedSeconds={}",
                    jobId, state.papersScanned, state.embedded, state.skipped, state.providerRequests,
                    elapsed.toMillis() / 1000.0);
            return new EmbeddingIngestionResult(
                    state.papersScanned, state.documentsPrepared, state.embedded, state.skipped,
                    embeddingModel.modelName(), embeddingModel.dimensions(), elapsed, jobId,
                    state.providerRequests, state.failures
            );
        } catch (RuntimeException exception) {
            if (exception instanceof EmbeddingJobCancelledException
                    || Thread.currentThread().isInterrupted()
                    || cancellationRequested(jobId)) {
                progress.cancel();
                jobRepository.cancel(jobId, ownerId, progress.snapshot());
                LOGGER.info("Embedding job {} cancelled after scanned={}, embedded={}, skipped={}",
                        jobId, state.papersScanned, state.embedded, state.skipped);
                throw exception instanceof EmbeddingJobCancelledException
                        ? exception
                        : new EmbeddingJobCancelledException("Embedding job cancelled", exception);
            }
            progress.fail(exception);
            jobRepository.fail(jobId, ownerId, progress.snapshot(), exception);
            LOGGER.error("Embedding job {} failed after scanned={}, embedded={}, skipped={}: {}",
                    jobId, state.papersScanned, state.embedded, state.skipped, exception.getMessage(), exception);
            throw exception;
        }
    }

    private void processPage(List<Paper> papers, IngestionState state, UUID jobId) {
        checkCancellation(jobId);
        var documents = papers.stream().map(retrievalDocumentFactory::fromPaper).toList();
        var checkpointIds = documents.stream().map(this::checkpointId).toList();
        var existingIds = new HashSet<EmbeddingCheckpointId>();
        checkpointRepository.findAllById(checkpointIds).forEach(checkpoint -> existingIds.add(checkpoint.getId()));

        var pending = new ArrayList<RetrievalDocument>();
        for (var document : documents) {
            state.papersScanned++;
            state.documentsPrepared++;
            progress.markScanned();
            if (existingIds.contains(checkpointId(document))) {
                state.skipped++;
                progress.markSkipped();
                metrics.skipped(1);
            } else {
                pending.add(document);
            }
            logProgressIfDue();
        }

        for (var providerBatch : EmbeddingBatchPlanner.plan(
                pending,
                properties.getProviderBatchSize(),
                properties.getProviderMaxEstimatedTokens(),
                properties.getProviderMaxEstimatedDocumentTokens())) {
            checkCancellation(jobId);
            progress.providerRequestStarted();
            jobRepository.markWaitingForProvider(jobId, ownerId, progress.snapshot());
            var requestStartedAt = Instant.now();
            List<List<Double>> vectors;
            try {
                vectors = embeddingModel.embedBatch(providerBatch, EmbeddingWorkClass.BULK);
            } catch (RuntimeException exception) {
                progress.providerRequestFailed();
                metrics.providerFailure();
                state.failures++;
                throw exception;
            }
            progress.providerRequestCompleted(Duration.between(requestStartedAt, Instant.now()).toMillis());
            jobRepository.markRunning(jobId, ownerId, progress.snapshot());
            metrics.providerRequest(Duration.between(requestStartedAt, Instant.now()));
            state.providerRequests++;
            if (vectors.size() != providerBatch.size()) {
                throw new IllegalStateException("Embedding model returned " + vectors.size()
                        + " vectors for " + providerBatch.size() + " documents");
            }

            var embeddedDocuments = new ArrayList<EmbeddedDocument>(providerBatch.size());
            var newCheckpoints = new ArrayList<EmbeddingCheckpoint>(providerBatch.size());
            for (var index = 0; index < providerBatch.size(); index++) {
                var document = providerBatch.get(index);
                var vector = vectors.get(index);
                if (vector.size() != embeddingModel.dimensions()) {
                    throw new IllegalStateException("Embedding model returned " + vector.size()
                            + " dimensions, expected " + embeddingModel.dimensions());
                }
                embeddedDocuments.add(new EmbeddedDocument(
                        document.paperId(), document.arxivId(), document.chunkId(), document.contentHash(),
                        embeddingModel.modelName(), embeddingModel.dimensions(), document.text(), document.categories(),
                        document.submittedDate(), vector
                ));
                newCheckpoints.add(new EmbeddingCheckpoint(checkpointId(document), vector.size()));
            }

            var vectorWriteTimer = metrics.startVectorWrite();
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    vectorStoreGateway.upsertAll(embeddedDocuments);
                    checkpointRepository.saveAll(newCheckpoints);
                    checkpointRepository.flush();
                });
            } finally {
                metrics.stopVectorWrite(vectorWriteTimer);
            }
            state.embedded += embeddedDocuments.size();
            metrics.embedded(embeddedDocuments.size());
            embeddedDocuments.forEach(document -> progress.markEmbedded());
            progress.markLastPaper(providerBatch.getLast().paperId());
            jobRepository.updateProgress(jobId, ownerId, progress.snapshot(), providerBatch.getLast().paperId());
            logProgressIfDue();
        }
    }

    private void validateConfiguration(EmbeddingIngestionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("embedding ingestion request must not be null");
        }
        if (properties.getBatchSize() <= 0) {
            throw new IllegalStateException("embedding.ingestion.batch-size must be greater than zero");
        }
        if (properties.getProviderBatchSize() <= 0) {
            throw new IllegalStateException("embedding.ingestion.provider-batch-size must be greater than zero");
        }
        if (properties.getProviderMaxEstimatedTokens() <= 0
                || properties.getProviderMaxEstimatedDocumentTokens() <= 0) {
            throw new IllegalStateException("embedding.ingestion.provider-max-estimated-tokens must be greater than zero");
        }
        if (properties.getProgressLogInterval() <= 0) {
            throw new IllegalStateException("embedding.ingestion.progress-log-interval must be greater than zero");
        }
    }

    private void checkCancellation(UUID jobId) {
        if (Thread.currentThread().isInterrupted() || cancellationRequested(jobId)) {
            throw new EmbeddingJobCancelledException("Embedding job cancellation requested");
        }
    }

    private boolean cancellationRequested(UUID jobId) {
        try {
            return jobRepository.isCancellationRequested(jobId);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void logProgressIfDue() {
        var snapshot = progress.snapshot();
        if (snapshot.papersScanned() % properties.getProgressLogInterval() == 0
                || snapshot.papersScanned() == snapshot.totalPapers()) {
            LOGGER.info("Embedding progress job={}: {}/{} papers scanned, embedded={}, skipped={}, providerRequests={}, lastPaperId={}",
                    snapshot.jobId(), snapshot.papersScanned(), snapshot.totalPapers(), snapshot.embedded(),
                    snapshot.skipped(), snapshot.providerRequests(), snapshot.lastPaperId());
        }
    }

    private EmbeddingCheckpointId checkpointId(RetrievalDocument document) {
        return new EmbeddingCheckpointId(
                document.paperId(), document.chunkId(), embeddingModel.modelName(), document.contentHash(),
                RetrievalDocumentFactory.DOCUMENT_SCHEMA_VERSION
        );
    }

    static class IngestionState {
        private long papersScanned;
        private long documentsPrepared;
        private long embedded;
        private long skipped;
        private long failures;
        private long providerRequests;
    }

    public static final class EmbeddingJobCancelledException extends RuntimeException {
        public EmbeddingJobCancelledException(String message) {
            super(message);
        }

        public EmbeddingJobCancelledException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
