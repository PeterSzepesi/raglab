package com.example.arxivrag.ai.chat;

import com.example.arxivrag.config.ExecutionProperties;
import com.example.arxivrag.embedding.RetrievalDocumentFactory;
import com.example.arxivrag.paper.Paper;
import com.example.arxivrag.paper.PaperRepository;
import com.example.arxivrag.search.CorpusGeneration;
import com.example.arxivrag.search.HybridSearchService;
import com.example.arxivrag.search.LexicalSearchService;
import com.example.arxivrag.search.SearchMode;
import com.example.arxivrag.vector.SemanticSearchResult;
import com.example.arxivrag.vector.SemanticSearchService;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class ChatRetrievalPlanner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatRetrievalPlanner.class);
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
    private final ExecutionProperties executionProperties;
    private final ExecutorService retrievalExecutor;
    private final RetrievalPolicyFactory policyFactory;

    public ChatRetrievalPlanner(
            SemanticSearchService semanticSearch,
            LexicalSearchService lexicalSearch,
            HybridSearchService hybridSearch,
            PaperRepository papers,
            RetrievalDocumentFactory documentFactory,
            ChatProperties properties,
            CorpusGeneration generation,
            ChatRetrievalCache cache,
            ExecutionProperties executionProperties,
            @Qualifier("interactiveRetrievalExecutor") ExecutorService retrievalExecutor,
            RetrievalPolicyFactory policyFactory
    ) {
        this.semanticSearch = semanticSearch;
        this.lexicalSearch = lexicalSearch;
        this.hybridSearch = hybridSearch;
        this.papers = papers;
        this.documentFactory = documentFactory;
        this.properties = properties;
        this.generation = generation;
        this.cache = cache;
        this.executionProperties = executionProperties;
        this.retrievalExecutor = retrievalExecutor;
        this.policyFactory = policyFactory;
    }

    public ChatRetrieval retrieve(ChatRequest request) {
        return retrieve(request, null);
    }

    public ChatRetrieval retrieve(ChatRequest request, com.example.arxivrag.vector.SearchStageObserver observer) {
        validate(request);
        var query = request.message().trim();
        var policy = policyFactory.create(request);
        var topK = policy.topK();
        LOGGER.debug("Selected retrieval policy version={} intent={} mode={} topK={} context={} output={}",
                policy.version(), policy.intent(), policy.relationalOnly() ? "RELATIONAL" : policy.mode(),
                policy.topK(), policy.maxContextCharacters(), policy.maxCompletionTokens());
        var matcher = EXACT_ID.matcher(query);
        if (matcher.find()) {
            var requestedId = matcher.group(1);
            var foundPaper = papers.findByArxivId(requestedId);
            if (foundPaper.isEmpty()) {
                foundPaper = papers.findByArxivId(requestedId.replaceFirst("v\\d+$", ""));
            }
            return foundPaper
                    .map(paper -> new ChatRetrieval(List.of(toResult(paper, 1.0d)),
                            List.of(RetrievalToolService.PAPER_LOOKUP), ChatRetrievalOutcome.EVIDENCE, policy))
                    .orElseGet(() -> new ChatRetrieval(List.of(), List.of(RetrievalToolService.PAPER_LOOKUP),
                            ChatRetrievalOutcome.PAPER_NOT_FOUND, policy));
        }

        var structuredOnly = policy.relationalOnly();
        var mode = policy.mode();
        var modeName = structuredOnly ? "RELATIONAL" : mode.name();
        var category = policy.filters().category();
        var key = new ChatRetrievalCache.Key(
                query.toLowerCase(Locale.ROOT), modeName, category,
                text(request.fromDate()), text(request.toDate()), topK,
                semanticSearch.embeddingModelName(), generation.current(), policy.version(),
                RetrievalDocumentFactory.DOCUMENT_SCHEMA_VERSION);
        return cache.getOrLoad(key,
                () -> retrieveUncached(request, observer, query, policy));
    }

    private ChatRetrieval retrieveUncached(
            ChatRequest request,
            com.example.arxivrag.vector.SearchStageObserver observer,
            String query,
            RetrievalPolicy policy
    ) {
        var structuredOnly = policy.relationalOnly();
        var mode = policy.mode();
        var category = policy.filters().category();
        var topK = policy.topK();
        var degraded = false;
        List<SemanticSearchResult> results;
        if (structuredOnly) {
            results = relational(category, request, topK);
        } else if (mode == SearchMode.LEXICAL) {
            results = lexical(request, query, category, topK);
        } else {
            try {
                results = retrieveRemoteWithDeadline(request, observer, query, policy);
            } catch (RetrievalDeadlineExceededException exception) {
                results = lexical(request, query, category, topK);
                degraded = true;
            }
        }
        if (!structuredOnly && !degraded && mode != SearchMode.LEXICAL && policy.minimumScore() > 0d) {
            results = results.stream().filter(result -> result.score() >= policy.minimumScore()).toList();
        }
        var toolName = structuredOnly ? RetrievalToolService.RELATIONAL_PAPER_SEARCH : switch (mode) {
            case AUTO -> throw new IllegalStateException("Automatic retrieval mode was not resolved");
            case SEMANTIC -> RetrievalToolService.SEMANTIC_SEARCH;
            case LEXICAL -> RetrievalToolService.RELATIONAL_PAPER_SEARCH;
            case HYBRID -> "hybrid_search";
        };
        var tools = degraded
                ? List.of(toolName, RetrievalToolService.RELATIONAL_PAPER_SEARCH)
                : List.of(toolName);
        var outcome = degraded
                ? ChatRetrievalOutcome.DEGRADED_LEXICAL
                : results.isEmpty() ? ChatRetrievalOutcome.NO_EVIDENCE : ChatRetrievalOutcome.EVIDENCE;
        return new ChatRetrieval(results, tools, outcome, policy);
    }

    private List<SemanticSearchResult> retrieveRemoteWithDeadline(
            ChatRequest request,
            com.example.arxivrag.vector.SearchStageObserver observer,
            String query,
            RetrievalPolicy policy
    ) {
        var mode = policy.mode();
        var category = policy.filters().category();
        var topK = policy.topK();
        var future = retrievalExecutor.submit(() -> switch (mode) {
            case AUTO -> throw new IllegalStateException("Automatic retrieval mode was not resolved");
            case SEMANTIC -> semanticSearch.search(query, topK, category,
                    request.fromDate(), request.toDate(), observer);
            case HYBRID -> hybridSearch.search(query, topK, category,
                    request.fromDate(), request.toDate(), observer,
                    policy.vectorWeight(), policy.lexicalWeight());
            case LEXICAL -> throw new IllegalStateException("Lexical retrieval does not use the provider deadline");
        });
        try {
            return future.get(executionProperties.getSemanticSearchTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new RetrievalDeadlineExceededException("Semantic retrieval exceeded its deadline", exception);
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Retrieval was interrupted", exception);
        } catch (ExecutionException exception) {
            var cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Retrieval failed", cause);
        }
    }

    private List<SemanticSearchResult> lexical(ChatRequest request, String query, String category, int topK) {
        return lexicalSearch.search(query, topK, category,
                request.fromDate(), request.toDate());
    }

    private List<SemanticSearchResult> relational(String category, ChatRequest request, int topK) {
        var values = papers.searchRelational(null, category, request.fromDate(), request.toDate(),
                topK);
        return java.util.stream.IntStream.range(0, values.size())
                .mapToObj(index -> toResult(values.get(index), 1.0d / (index + 1)))
                .toList();
    }

    private SemanticSearchResult toResult(Paper paper, double score) {
        return new SemanticSearchResult(paper.getId(), paper.getArxivId(), paper.getTitle(), paper.getAbstractText(),
                paper.getAuthors(), paper.getCategories(), paper.getSubmittedDate(), score,
                documentFactory.fromPaper(paper).text(), "relational");
    }

    private static void validate(ChatRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        if (request.fromDate() != null && request.toDate() != null && request.fromDate().isAfter(request.toDate())) {
            throw new IllegalArgumentException("fromDate must not be after toDate");
        }
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    private static final class RetrievalDeadlineExceededException extends RuntimeException {
        private RetrievalDeadlineExceededException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
