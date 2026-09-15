package com.example.arxivrag.ai.chat;

import com.example.arxivrag.model.api.ChatModelGateway;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class GroundedContextBuilder {

    private final ChatProperties properties;
    private final ObjectMapper objectMapper;
    private final CitationResolver citationResolver;

    public GroundedContextBuilder(ChatProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, new CitationResolver());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public GroundedContextBuilder(ChatProperties properties, ObjectMapper objectMapper,
                                  CitationResolver citationResolver) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.citationResolver = citationResolver;
    }

    public GroundedContext build(ChatRetrieval retrieval) {
        return build(retrieval, null);
    }

    public GroundedContext build(ChatRetrieval retrieval, String question) {
        var unique = new LinkedHashMap<String, com.example.arxivrag.vector.SemanticSearchResult>();
        retrieval.results().stream()
                .sorted(java.util.Comparator.comparingDouble(
                        com.example.arxivrag.vector.SemanticSearchResult::score).reversed())
                .forEach(result -> unique.putIfAbsent(result.arxivId(), result));

        var rows = new ArrayList<Map<String, Object>>();
        var citations = new ArrayList<ChatCitation>();
        var retainedTitleTerms = new ArrayList<Set<String>>();
        var policy = retrieval.policy();
        var legacy = "legacy".equals(policy.version());
        var totalLimit = legacy ? properties.getMaxContextCharactersTotal() : policy.maxContextCharacters();
        var perPaperLimit = legacy ? properties.getMaxContextCharactersPerPaper()
                : policy.maxContextCharactersPerPaper();
        var used = 2;
        for (var result : unique.values()) {
            var titleTerms = terms(result.title());
            if (retainedTitleTerms.stream().anyMatch(existing ->
                    jaccard(existing, titleTerms) >= policy.duplicateTitleJaccardThreshold())) {
                continue;
            }
            var remaining = totalLimit - used;
            if (remaining < 100) {
                break;
            }
            var excerpt = relevantExcerpt(result.abstractText(), question,
                    Math.min(perPaperLimit, Math.max(0, remaining - 180)));
            var row = new LinkedHashMap<String, Object>();
            row.put("arxivId", result.arxivId());
            row.put("url", citationResolver.resolve(result).url());
            row.put("title", result.title());
            row.put("abstract", excerpt);
            row.put("score", result.score());
            if (!result.categories().isEmpty()) {
                row.put("categories", result.categories());
            }
            if (result.submittedDate() != null) {
                row.put("submittedDate", result.submittedDate());
            }
            var candidate = new ArrayList<>(rows);
            candidate.add(row);
            var json = json(candidate);
            if (json.length() > totalLimit) {
                break;
            }
            rows.add(row);
            retainedTitleTerms.add(titleTerms);
            used = json.length();
            citations.add(citationResolver.resolve(result));
        }
        return new GroundedContext(json(rows), citations, retrieval.toolsUsed());
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException exception) {
            throw new ChatModelGateway.ModelProviderException("Could not serialize grounded context", exception);
        }
    }

    private static String relevantExcerpt(String value, String question, int limit) {
        if (value == null || limit <= 0) {
            return "";
        }
        if (value.length() <= limit) {
            return value;
        }
        var sentences = value.strip().split("(?<=[.!?])\\s+");
        var queryTerms = terms(question);
        var best = 0;
        var bestScore = -1L;
        for (var index = 0; index < sentences.length; index++) {
            var sentenceTerms = terms(sentences[index]);
            var score = queryTerms.stream().filter(sentenceTerms::contains).count();
            if (score > bestScore) {
                best = index;
                bestScore = score;
            }
        }
        var selected = new ArrayList<String>();
        selected.add(sentences[best]);
        for (var distance = 1; selectedLength(selected) < limit && distance < sentences.length; distance++) {
            var before = best - distance;
            var after = best + distance;
            if (before >= 0) selected.addFirst(sentences[before]);
            if (after < sentences.length && selectedLength(selected) < limit) selected.add(sentences[after]);
        }
        return atWordBoundary(String.join(" ", selected), limit);
    }

    private static int selectedLength(List<String> selected) {
        return selected.stream().mapToInt(String::length).sum() + Math.max(0, selected.size() - 1);
    }

    private static String atWordBoundary(String value, int limit) {
        if (value.length() <= limit) return value;
        var end = Math.max(0, limit - 3);
        var boundary = value.lastIndexOf(' ', end);
        if (boundary >= Math.max(0, end / 2)) end = boundary;
        return value.substring(0, end).stripTrailing() + "...";
    }

    private static Set<String> terms(String value) {
        if (value == null || value.isBlank()) return Set.of();
        var result = new HashSet<String>();
        for (var token : value.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (token.length() > 2) result.add(token);
        }
        return Set.copyOf(result);
    }

    private static double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) return 0d;
        var intersection = left.stream().filter(right::contains).count();
        var union = new HashSet<>(left);
        union.addAll(right);
        return intersection / (double) union.size();
    }
}
