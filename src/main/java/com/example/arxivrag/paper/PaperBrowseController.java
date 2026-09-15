package com.example.arxivrag.paper;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.example.arxivrag.embedding.EmbeddingCheckpointRepository;
import com.example.arxivrag.embedding.EmbeddingModelGateway;
import com.example.arxivrag.embedding.RetrievalDocumentFactory;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping({"/api/papers", "/api/v1/papers"})
@ConditionalOnProperty(name = "rag.features.papers-enabled", havingValue = "true")
public class PaperBrowseController {
    private final PaperRepository papers;
    private final EmbeddingCheckpointRepository checkpoints;
    private final EmbeddingModelGateway embeddingModel;
    private final RetrievalDocumentFactory documents;

    public PaperBrowseController(PaperRepository papers, EmbeddingCheckpointRepository checkpoints,
                                 EmbeddingModelGateway embeddingModel, RetrievalDocumentFactory documents) {
        this.papers = papers;
        this.checkpoints = checkpoints;
        this.embeddingModel = embeddingModel;
        this.documents = documents;
    }

    @GetMapping
    public PaperPage browse(
            @RequestParam(required = false) Long after,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String arxivId,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String embeddingState,
            @RequestParam(defaultValue = "25") int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("fromDate must not be after toDate");
        }
        var requestedState = normalizeState(embeddingState);
        var accepted = new ArrayList<PaperSummary>();
        var scanAfter = after;
        var exhausted = false;
        var batchSize = Math.min(500, Math.max(50, limit * 4));
        for (var batch = 0; batch < 20 && accepted.size() <= limit && !exhausted; batch++) {
            var values = papers.browse(scanAfter, normalize(query), normalize(arxivId), normalize(title),
                    normalize(author), normalize(category), fromDate, toDate,
                    PageRequest.of(0, batchSize, Sort.by("id")));
            if (values.isEmpty()) {
                exhausted = true;
                break;
            }
            scanAfter = values.getLast().getId();
            exhausted = values.size() < batchSize;
            var states = embeddingStates(values);
            values.stream()
                    .filter(paper -> requestedState == null
                            || requestedState.equals(states.get(paper.getId()).status()))
                    .map(paper -> summary(paper, states.get(paper.getId())))
                    .forEach(accepted::add);
        }
        var hasMore = accepted.size() > limit || !exhausted;
        var items = accepted.size() > limit ? List.copyOf(accepted.subList(0, limit)) : List.copyOf(accepted);
        var nextCursor = hasMore ? (items.isEmpty() ? scanAfter : items.getLast().id()) : null;
        return new PaperPage(items, nextCursor, hasMore);
    }

    @GetMapping("/{arxivId:.+}")
    public PaperItem find(@PathVariable String arxivId) {
        var paper = papers.findByArxivId(arxivId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Paper not found"));
        return item(paper, embeddingStates(List.of(paper)).get(paper.getId()));
    }

    private java.util.Map<Long, EmbeddingState> embeddingStates(List<Paper> values) {
        if (values.isEmpty()) return java.util.Map.of();
        var existing = checkpoints.findByPaperIdsAndModel(embeddingModel.modelName(),
                values.stream().map(Paper::getId).toList());
        var byPaper = existing.stream().collect(java.util.stream.Collectors.groupingBy(
                checkpoint -> checkpoint.getId().getPaperId()));
        var result = new java.util.HashMap<Long, EmbeddingState>();
        for (var paper : values) {
            var document = documents.fromPaper(paper);
            var paperCheckpoints = byPaper.getOrDefault(paper.getId(), java.util.List.of());
            var current = paperCheckpoints.stream().anyMatch(checkpoint ->
                    RetrievalDocumentFactory.CHUNK_ID.equals(checkpoint.getId().getChunkId())
                            && document.contentHash().equals(checkpoint.getId().getContentHash())
                            && RetrievalDocumentFactory.DOCUMENT_SCHEMA_VERSION.equals(
                                    checkpoint.getId().getDocumentSchemaVersion()));
            result.put(paper.getId(), new EmbeddingState(current ? "CURRENT" : paperCheckpoints.isEmpty()
                    ? "MISSING" : "STALE", document.contentHash()));
        }
        return java.util.Map.copyOf(result);
    }

    private PaperItem item(Paper paper, EmbeddingState state) {
        var id = new ArxivId(paper.getArxivId());
        return new PaperItem(paper.getId(), paper.getArxivId(), paper.getTitle(), paper.getAbstractText(),
                paper.getAuthors(), paper.getCategories(), paper.getSubmittedDate(), paper.getUpdatedDate(),
                paper.getDoi(), paper.getJournalRef(), id.abstractUrl(), id.pdfUrl(),
                "CURRENT".equals(state.status()), state.status(), state.contentHash(), embeddingModel.modelName(),
                RetrievalDocumentFactory.DOCUMENT_SCHEMA_VERSION, paper.getCreatedAt(), paper.getUpdatedAt());
    }

    private PaperSummary summary(Paper paper, EmbeddingState state) {
        var id = new ArxivId(paper.getArxivId());
        var abstractText = paper.getAbstractText() == null ? "" : paper.getAbstractText();
        var preview = abstractText.length() <= 320 ? abstractText : abstractText.substring(0, 317).stripTrailing() + "...";
        return new PaperSummary(paper.getId(), paper.getArxivId(), paper.getTitle(), preview,
                paper.getAuthors(), paper.getCategories(), paper.getSubmittedDate(), id.abstractUrl(), id.pdfUrl(),
                state.status(), embeddingModel.modelName(), RetrievalDocumentFactory.DOCUMENT_SCHEMA_VERSION);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizeState(String value) {
        var normalized = normalize(value);
        if (normalized == null) return null;
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!Set.of("CURRENT", "STALE", "MISSING").contains(normalized)) {
            throw new IllegalArgumentException("embeddingState must be CURRENT, STALE, or MISSING");
        }
        return normalized;
    }

    public record PaperPage(List<PaperSummary> items, Long nextCursor, boolean hasMore) { }
    public record PaperSummary(Long id, String arxivId, String title, String abstractPreview,
                               List<String> authors, List<String> categories, LocalDate submittedDate,
                               String abstractUrl, String pdfUrl, String embeddingStatus, String embeddingModel,
                               String retrievalDocumentVersion) { }
    public record PaperItem(Long id, String arxivId, String title, String abstractText, List<String> authors,
                            List<String> categories, LocalDate submittedDate, LocalDate updatedDate,
                            String doi, String journalRef, String abstractUrl, String pdfUrl, boolean embedded,
                            String embeddingStatus, String contentHash, String embeddingModel,
                            String retrievalDocumentVersion, java.time.Instant createdAt,
                            java.time.Instant storedUpdatedAt) { }
    private record EmbeddingState(String status, String contentHash) { }
}
