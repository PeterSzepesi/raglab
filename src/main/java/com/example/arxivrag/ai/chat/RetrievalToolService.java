package com.example.arxivrag.ai.chat;

import com.example.arxivrag.paper.Paper;
import com.example.arxivrag.paper.PaperRepository;
import com.example.arxivrag.vector.SemanticSearchResult;
import com.example.arxivrag.vector.SemanticSearchService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RetrievalToolService {

    public static final String SEMANTIC_SEARCH = "semantic_search";
    public static final String RELATIONAL_PAPER_SEARCH = "relational_paper_search";
    public static final String PAPER_LOOKUP = "paper_lookup";

    private static final int DEFAULT_TOP_K = 5;
    private static final int MAX_TOP_K = 10;
    private static final int MAX_TEXT_LENGTH = 1200;

    private final SemanticSearchService semanticSearchService;

    private final PaperRepository paperRepository;

    public RetrievalToolService(SemanticSearchService semanticSearchService, PaperRepository paperRepository) {
        this.semanticSearchService = semanticSearchService;
        this.paperRepository = paperRepository;
    }

    public List<Map<String, Object>> toolDefinitions() {
        var searchProperties = new LinkedHashMap<String, Object>();
        searchProperties.put("query", stringSchema("Natural-language research question or search terms"));
        searchProperties.put("topK", integerSchema("Maximum number of papers to return", 1, MAX_TOP_K));
        searchProperties.put("category", stringSchema("Optional exact arXiv category such as cs.AI"));
        searchProperties.put("fromDate", stringSchema("Optional inclusive submitted date in YYYY-MM-DD format"));
        searchProperties.put("toDate", stringSchema("Optional inclusive submitted date in YYYY-MM-DD format"));

        var semantic = functionDefinition(
                SEMANTIC_SEARCH,
                "Find papers by semantic similarity using the local embedding model and vector store.",
                searchProperties,
                List.of("query")
        );
        var relational = functionDefinition(
                RELATIONAL_PAPER_SEARCH,
                "Find papers using local PostgreSQL title, abstract, category, and date filters.",
                searchProperties,
                List.of()
        );
        var lookupProperties = Map.<String, Object>of(
                "arxivId", stringSchema("Exact arXiv identifier, for example 2401.00001")
        );
        var lookup = functionDefinition(
                PAPER_LOOKUP,
                "Look up one canonical paper by its exact arXiv identifier.",
                lookupProperties,
                List.of("arxivId")
        );
        return List.of(semantic, relational, lookup);
    }

    public ToolExecution execute(String name, Map<String, Object> arguments) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("tool name must not be blank");
        }
        if (arguments == null) {
            throw new IllegalArgumentException("tool arguments must be an object");
        }
        return switch (name) {
            case SEMANTIC_SEARCH -> executeSemanticSearch(arguments);
            case RELATIONAL_PAPER_SEARCH -> executeRelationalSearch(arguments);
            case PAPER_LOOKUP -> executePaperLookup(arguments);
            default -> throw new IllegalArgumentException("unknown retrieval tool: " + name);
        };
    }

    private ToolExecution executeSemanticSearch(Map<String, Object> arguments) {
        validateKeys(arguments, Set.of("query", "topK", "category", "fromDate", "toDate"));
        var query = requiredString(arguments, "query");
        var topK = topK(arguments);
        var category = optionalString(arguments, "category");
        var fromDate = optionalDate(arguments, "fromDate");
        var toDate = optionalDate(arguments, "toDate");
        var results = semanticSearchService.search(query, topK, category, fromDate, toDate);
        return new ToolExecution(SEMANTIC_SEARCH, resultRows(results), citations(results));
    }

    private ToolExecution executeRelationalSearch(Map<String, Object> arguments) {
        validateKeys(arguments, Set.of("query", "topK", "category", "fromDate", "toDate"));
        var query = optionalString(arguments, "query");
        var topK = topK(arguments);
        var category = optionalString(arguments, "category");
        var fromDate = optionalDate(arguments, "fromDate");
        var toDate = optionalDate(arguments, "toDate");
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("fromDate must not be after toDate");
        }
        var papers = paperRepository.searchRelational(query, category, fromDate, toDate, topK);
        return new ToolExecution(RELATIONAL_PAPER_SEARCH, paperRows(papers), paperCitations(papers));
    }

    private ToolExecution executePaperLookup(Map<String, Object> arguments) {
        validateKeys(arguments, Set.of("arxivId"));
        var arxivId = requiredString(arguments, "arxivId");
        var paper = paperRepository.findByArxivId(arxivId);
        if (paper.isEmpty()) {
            return new ToolExecution(PAPER_LOOKUP, List.of(), List.of());
        }
        var value = paper.get();
        return new ToolExecution(PAPER_LOOKUP, paperRows(List.of(value)), paperCitations(List.of(value)));
    }

    private static List<Map<String, Object>> resultRows(List<SemanticSearchResult> results) {
        return results.stream().<Map<String, Object>>map(result -> {
            var row = new LinkedHashMap<String, Object>();
            row.put("paperId", result.paperId());
            row.put("arxivId", result.arxivId());
            row.put("url", ChatCitation.arxivUrlFor(result.arxivId()));
            row.put("title", result.title());
            row.put("abstractText", truncate(result.abstractText()));
            row.put("categories", result.categories());
            row.put("submittedDate", result.submittedDate());
            row.put("score", result.score());
            return row;
        }).toList();
    }

    private static List<Map<String, Object>> paperRows(List<Paper> papers) {
        return papers.stream().<Map<String, Object>>map(paper -> {
            var row = new LinkedHashMap<String, Object>();
            row.put("paperId", paper.getId());
            row.put("arxivId", paper.getArxivId());
            row.put("url", ChatCitation.arxivUrlFor(paper.getArxivId()));
            row.put("title", paper.getTitle());
            row.put("abstractText", truncate(paper.getAbstractText()));
            row.put("categories", paper.getCategories());
            row.put("submittedDate", paper.getSubmittedDate());
            return row;
        }).toList();
    }

    private static List<ChatCitation> citations(List<SemanticSearchResult> results) {
        return results.stream()
                .map(result -> new ChatCitation(result.paperId(), result.arxivId(), result.title(), result.score()))
                .toList();
    }

    private static List<ChatCitation> paperCitations(List<Paper> papers) {
        return papers.stream()
                .map(paper -> new ChatCitation(paper.getId(), paper.getArxivId(), paper.getTitle(), null))
                .toList();
    }

    private static Map<String, Object> functionDefinition(
            String name,
            String description,
            Map<String, Object> properties,
            List<String> required
    ) {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", name,
                        "description", description,
                        "parameters", Map.of(
                                "type", "object",
                                "properties", properties,
                                "required", required,
                                "additionalProperties", false
                        )
                )
        );
    }

    private static Map<String, Object> stringSchema(String description) {
        return Map.of("type", "string", "description", description);
    }

    private static Map<String, Object> integerSchema(String description, int minimum, int maximum) {
        return Map.of("type", "integer", "description", description, "minimum", minimum, "maximum", maximum);
    }

    private static void validateKeys(Map<String, Object> arguments, Set<String> allowed) {
        var unknown = arguments.keySet().stream().filter(key -> !allowed.contains(key)).findFirst();
        if (unknown.isPresent()) {
            throw new IllegalArgumentException("unknown tool argument: " + unknown.get());
        }
    }

    private static String requiredString(Map<String, Object> arguments, String name) {
        var value = optionalString(arguments, name);
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static String optionalString(Map<String, Object> arguments, String name) {
        var value = arguments.get(name);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String string) || string.isBlank()) {
            throw new IllegalArgumentException(name + " must be a non-blank string");
        }
        return string.trim();
    }

    private static int topK(Map<String, Object> arguments) {
        var value = arguments.get("topK");
        if (value == null) {
            return DEFAULT_TOP_K;
        }
        if (!(value instanceof Number number) || number.doubleValue() != Math.rint(number.doubleValue())) {
            throw new IllegalArgumentException("topK must be an integer between 1 and " + MAX_TOP_K);
        }
        var topK = number.intValue();
        if (topK < 1 || topK > MAX_TOP_K) {
            throw new IllegalArgumentException("topK must be between 1 and " + MAX_TOP_K);
        }
        return topK;
    }

    private static LocalDate optionalDate(Map<String, Object> arguments, String name) {
        var value = optionalString(arguments, name);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(name + " must use YYYY-MM-DD format");
        }
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= MAX_TEXT_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_TEXT_LENGTH) + "...";
    }
}
