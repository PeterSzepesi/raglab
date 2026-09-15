package com.example.arxivrag.arxiv;

import com.example.arxivrag.paper.Paper;
import com.example.arxivrag.paper.PaperRepository;
import com.example.arxivrag.search.CorpusGeneration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ArxivImportService {

    private final ArxivDatasetReader datasetReader;

    private final ArxivRecordFilter recordFilter;

    private final PaperMapper paperMapper;

    private final PaperRepository paperRepository;

    private final ArxivImportProperties properties;

    private final TransactionTemplate transactionTemplate;

    private final CorpusGeneration corpusGeneration;

    public ArxivImportService(
            ArxivDatasetReader datasetReader,
            ArxivRecordFilter recordFilter,
            PaperMapper paperMapper,
            PaperRepository paperRepository,
            ArxivImportProperties properties,
            TransactionTemplate transactionTemplate,
            CorpusGeneration corpusGeneration
    ) {
        this.datasetReader = datasetReader;
        this.recordFilter = recordFilter;
        this.paperMapper = paperMapper;
        this.paperRepository = paperRepository;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
        this.corpusGeneration = corpusGeneration;
    }

    public ArxivImportResult importDataset(Path datasetPath) {
        return importDataset(datasetPath, ArxivImportOptions.from(properties), () -> false, ignored -> { }).result();
    }

    public ImportExecution importDataset(Path datasetPath, ArxivImportOptions options,
                                         BooleanSupplier cancellationRequested,
                                         Consumer<ImportProgress> progressListener) {
        if (!Files.isRegularFile(datasetPath)) {
            throw new ArxivImportException("arXiv dataset file does not exist or is not a file: " + datasetPath);
        }
        if (properties.getBatchSize() <= 0) {
            throw new ArxivImportException("arxiv.import.batch-size must be greater than zero");
        }

        var startedAt = Instant.now();
        var state = new ImportState(properties.getBatchSize(), progressListener);

        var readResult = datasetReader.read(datasetPath, properties.getMalformedRecordPolicy(), datasetRecord -> {
            if (cancellationRequested.getAsBoolean()) {
                state.cancelled = true;
                return false;
            }
            if (state.hasReachedMaxRecords(options.maxRecords())) {
                return false;
            }

            try {
                state.recordsRead++;
                var record = datasetRecord.record();
                if (!recordFilter.accepts(record, options.categories(), options.fromDate())) {
                    state.skipped++;
                    state.publish();
                    return true;
                }

                state.accepted++;
                state.batch.add(paperMapper.toPaper(record));
                if (state.batch.size() >= properties.getBatchSize()) {
                    flush(state);
                }
                return !state.hasReachedMaxRecords(options.maxRecords());
            } catch (ArxivImportException exception) {
                state.invalidRecords++;
                if (properties.getMalformedRecordPolicy() == ArxivImportProperties.MalformedRecordPolicy.FAIL_FAST) {
                    throw exception;
                }
                return true;
            }
        });

        flush(state);

        var result = new ArxivImportResult(
                readResult.recordsRead(),
                state.accepted,
                state.imported,
                state.updated,
                state.skipped,
                readResult.invalidRecords() + state.invalidRecords,
                Duration.between(startedAt, Instant.now())
        );
        if (result.imported() + result.updated() > 0) {
            corpusGeneration.increment();
        }
        state.publish(readResult.invalidRecords());
        return new ImportExecution(result, state.cancelled, readResult.malformedLineSamples());
    }

    public ArxivImportPreview previewDataset(Path datasetPath, ArxivImportOptions options) {
        if (!Files.isRegularFile(datasetPath)) {
            throw new ArxivImportException("arXiv dataset file does not exist or is not a file: " + datasetPath);
        }
        var started = Instant.now();
        var acceptedIds = new ArrayList<String>();
        var seen = new HashSet<String>();
        var counts = new long[4]; // read, accepted, filtered, duplicate-in-file
        var read = datasetReader.read(datasetPath, properties.getMalformedRecordPolicy(), dataset -> {
            counts[0]++;
            if (options.maxRecords() != null && counts[1] >= options.maxRecords()) return false;
            try {
                var record = dataset.record();
                if (!recordFilter.accepts(record, options.categories(), options.fromDate())) {
                    counts[2]++;
                    return true;
                }
                paperMapper.toPaper(record);
                counts[1]++;
                if (!seen.add(record.id())) counts[3]++;
                else acceptedIds.add(record.id());
                return options.maxRecords() == null || counts[1] < options.maxRecords();
            } catch (ArxivImportException invalid) {
                return true;
            }
        });
        var existing = countExisting(acceptedIds);
        return new ArxivImportPreview(read.recordsRead(), counts[1], counts[2], read.invalidRecords(),
                counts[3], existing, read.malformedLineSamples(), Duration.between(started, Instant.now()));
    }

    void flush(ImportState state) {
        if (state.batch.isEmpty()) {
            return;
        }

        transactionTemplate.executeWithoutResult(status -> upsertBatch(state.batch, outcome -> {
            if (outcome == UpsertOutcome.INSERTED) {
                state.imported++;
            } else {
                state.updated++;
            }
        }));
        state.batch.clear();
        state.publish();
    }

    private long countExisting(List<String> acceptedIds) {
        long existing = 0;
        for (var start = 0; start < acceptedIds.size(); start += 500) {
            existing += paperRepository.findAllByArxivIdIn(
                    acceptedIds.subList(start, Math.min(start + 500, acceptedIds.size()))).size();
        }
        return existing;
    }

    public void upsertBatch(List<Paper> papers, Consumer<UpsertOutcome> outcomeConsumer) {
        var existingByArxivId = paperRepository.findAllByArxivIdIn(
                        papers.stream().map(Paper::getArxivId).toList()
                )
                .stream()
                .collect(Collectors.toMap(Paper::getArxivId, Function.identity()));

        for (var paper : papers) {
            var existing = existingByArxivId.get(paper.getArxivId());
            if (existing != null) {
                updateExistingPaper(existing, paper);
                outcomeConsumer.accept(UpsertOutcome.UPDATED);
            } else {
                paperRepository.save(paper);
                outcomeConsumer.accept(UpsertOutcome.INSERTED);
            }
        }
        paperRepository.flush();
    }

    private void updateExistingPaper(Paper existing, Paper source) {
        existing.setTitle(source.getTitle());
        existing.setAbstractText(source.getAbstractText());
        existing.setAuthors(source.getAuthors());
        existing.setCategories(source.getCategories());
        existing.setSubmittedDate(source.getSubmittedDate());
        existing.setUpdatedDate(source.getUpdatedDate());
        existing.setDoi(source.getDoi());
        existing.setJournalRef(source.getJournalRef());
    }

    enum UpsertOutcome {
        INSERTED,
        UPDATED
    }

    static class ImportState {

        private final ArrayList<Paper> batch;

        private long accepted;

        private long imported;

        private long updated;

        private long skipped;

        private long invalidRecords;

        private long recordsRead;
        private boolean cancelled;
        private final Consumer<ImportProgress> progressListener;

        ImportState(int batchSize, Consumer<ImportProgress> progressListener) {
            this.batch = new ArrayList<>(batchSize);
            this.progressListener = progressListener;
        }

        boolean hasReachedMaxRecords(Integer maxRecords) {
            return maxRecords != null && accepted >= maxRecords;
        }

        void publish() { publish(0); }
        void publish(long readerInvalid) {
            progressListener.accept(new ImportProgress(recordsRead, accepted, imported, updated, skipped,
                    invalidRecords + readerInvalid));
        }
    }

    public record ImportExecution(ArxivImportResult result, boolean cancelled,
                                  List<Long> malformedLineSamples) { }
    public record ImportProgress(long recordsRead, long accepted, long imported, long updated,
                                 long skipped, long invalidRecords) { }
    public record ArxivImportPreview(long recordsRead, long accepted, long filtered, long invalidRecords,
                                     long duplicateInFile, long alreadyStored, List<Long> malformedLineSamples,
                                     Duration elapsed) { }
}
