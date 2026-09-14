package com.example.arxivrag.ai.chat;

import com.example.arxivrag.embedding.RetrievalDocumentFactory;
import com.example.arxivrag.paper.Paper;
import com.example.arxivrag.paper.PaperRepository;
import com.example.arxivrag.search.CorpusGeneration;
import com.example.arxivrag.search.HybridSearchService;
import com.example.arxivrag.search.LexicalSearchService;
import com.example.arxivrag.search.SearchMode;
import com.example.arxivrag.vector.SemanticSearchResult;
import com.example.arxivrag.vector.SemanticSearchService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class ChatRetrievalPlanner {

    private static final Pattern EXACT_ID = Pattern.compile(
            "(?<![A-Za-z0-9./-])((?:\\d{4}\\.\\d{4,5}|[A-Za-z][A-Za-z0-9.-]*/\\d{7})(?:v\\d+)?)(?![A-Za-z0-9-])");

    private final SemanticSearchService semanticSearch;
    private final LexicalSearchService lexicalSearch;
    private final HybridSearchService hybridSearch;
    private final PaperRepository papers;
    private final RetrievalDocumentFactory documentFactory;
    private final ChatProperties properties;
    private final CorpusGeneration generation;
    private final ChatRetrievalCache cache;

    public ChatRetrievalPlanner(
            SemanticSearchService semanticSearch,
            LexicalSearchService lexicalSearch,
            HybridSearchService hybridSearch,
            PaperRepository papers,
            RetrievalDocumentFactory documentFactory,
            ChatProperties properties,
            CorpusGeneration generation,
            ChatRetrievalCache cache
    ) {
        this.semanticSearch = semanticSearch;
        this.lexicalSearch = lexicalSearch;
        this.hybridSearch = hybridSearch;
        this.papers = papers;
        this.documentFactory = documentFactory;
        this.properties = properties;
        this.generation = generation;
        this.cache = cache;
    }

    public ChatRetrieval retrieve(ChatRequest request) {
        return retrieve(request, null);
    }

    public ChatRetrieval retrieve(ChatRequest request, com.example.arxivrag.vector.SearchStageObserver observer) {
        validate(request);
        var query = request.message().trim();
        var matcher = EXACT_ID.matcher(query);
        if (matcher.find()) {
            var requestedId = matcher.group(1);
            var foundPaper = papers.findByArxivId(requestedId);
            if (foundPaper.isEmpty()) {
                foundPaper = papers.findByArxivId(requestedId.replaceFirst("v\\d+$", ""));
            }
            return foundPaper
                    .map(paper -> new ChatRetrieval(List.of(toResult(paper, 1.0d)),
                            List.of(RetrievalToolService.PAPER_LOOKUP)))
                    .orElseGet(() -> new ChatRetrieval(List.of(), List.of(RetrievalToolService.PAPER_LOOKUP)));
        }

        var structuredOnly = (request.retrievalMode() == null || request.retrievalMode().isBlank())
                && (request.category() != null || request.fromDate() != null || request.toDate() != null);
        var mode = structuredOnly ? null : chooseMode(request);
        var modeName = structuredOnly ? "RELATIONAL" : mode.name();
        var category = normalize(request.category());
        var key = new ChatRetrievalCache.Key(
                query.toLowerCase(Locale.ROOT), modeName, category,
                text(request.fromDate()), text(request.toDate()), properties.getRetrievalTopK(),
                semanticSearch.embeddingModelName(), generation.current());
        var cached = cache.get(key);
        if (cached.isPresent()) {
            return cached.get();
        }

        var started = System.nanoTime();
        var results = structuredOnly
                ? relational(category, request)
                : switch (mode) {
                    case SEMANTIC -> semanticSearch.search(query, properties.getRetrievalTopK(), category,
                            request.fromDate(), request.toDate(), observer);
                    case LEXICAL -> lexicalSearch.search(query, properties.getRetrievalTopK(), category,
                            request.fromDate(), request.toDate());
                    case HYBRID -> hybridSearch.search(query, properties.getRetrievalTopK(), category,
                            request.fromDate(), request.toDate(), observer);
                };
        cache.recordLoad(java.time.Duration.ofNanos(System.nanoTime() - started));
        var toolName = structuredOnly ? RetrievalToolService.RELATIONAL_PAPER_SEARCH : switch (mode) {
                case SEMANTIC -> RetrievalToolService.SEMANTIC_SEARCH;
                case LEXICAL -> RetrievalToolService.RELATIONAL_PAPER_SEARCH;
                case HYBRID -> "hybrid_search";
            };
        var retrieval = new ChatRetrieval(results, List.of(toolName));
        cache.put(key, retrieval);
        return retrieval;
    }

    private SearchMode chooseMode(ChatRequest request) {
        if (request.retrievalMode() != null && !request.retrievalMode().isBlank()) {
            return SearchMode.parse(request.retrievalMode());
        }
        return SearchMode.SEMANTIC;
    }

    private List<SemanticSearchResult> relational(String category, ChatRequest request) {
        var values = papers.searchRelational(null, category, request.fromDate(), request.toDate(),
                properties.getRetrievalTopK());
        return java.util.stream.IntStream.range(0, values.size())
                .mapToObj(index -> toResult(values.get(index), 1.0d / (index + 1)))
                .toList();
    }

    private SemanticSearchResult toResult(Paper paper, double score) {
        return new SemanticSearchResult(paper.getId(), paper.getArxivId(), paper.getTitle(), paper.getAbstractText(),
                paper.getAuthors(), paper.getCategories(), paper.getSubmittedDate(), score,
                documentFactory.fromPaper(paper).text());
    }

    private static void validate(ChatRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        if (request.fromDate() != null && request.toDate() != null && request.fromDate().isAfter(request.toDate())) {
            throw new IllegalArgumentException("fromDate must not be after toDate");
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }
}
