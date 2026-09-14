package com.example.arxivrag.arxiv;

import com.example.arxivrag.paper.Paper;
import com.example.arxivrag.paper.PaperRepository;
import com.example.arxivrag.search.CorpusGeneration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

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
        if (!Files.isRegularFile(datasetPath)) {
            throw new ArxivImportException("arXiv dataset file does not exist or is not a file: " + datasetPath);
        }
        if (properties.getBatchSize() <= 0) {
            throw new ArxivImportException("arxiv.import.batch-size must be greater than zero");
        }

        var startedAt = Instant.now();
        var state = new ImportState(properties.getBatchSize());

        var readResult = datasetReader.read(datasetPath, properties.getMalformedRecordPolicy(), datasetRecord -> {
            if (state.hasReachedMaxRecords(properties.getMaxRecords())) {
                return false;
            }

            try {
                var record = datasetRecord.record();
                if (!recordFilter.accepts(record, properties)) {
                    state.skipped++;
                    return true;
                }

                state.accepted++;
                state.batch.add(paperMapper.toPaper(record));
                if (state.batch.size() >= properties.getBatchSize()) {
                    flush(state);
                }
                return !state.hasReachedMaxRecords(properties.getMaxRecords());
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
        return result;
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

        ImportState(int batchSize) {
            this.batch = new ArrayList<>(batchSize);
        }

        boolean hasReachedMaxRecords(Integer maxRecords) {
            return maxRecords != null && accepted >= maxRecords;
        }
    }
}
