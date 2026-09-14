package com.example.arxivrag.ai.chat;

import com.example.arxivrag.ai.foundry.FoundryClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class GroundedContextBuilder {

    private final ChatProperties properties;
    private final ObjectMapper objectMapper;

    public GroundedContextBuilder(ChatProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public GroundedContext build(ChatRetrieval retrieval) {
        var unique = new LinkedHashMap<String, com.example.arxivrag.vector.SemanticSearchResult>();
        retrieval.results().stream()
                .sorted(java.util.Comparator.comparingDouble(
                        com.example.arxivrag.vector.SemanticSearchResult::score).reversed())
                .forEach(result -> unique.putIfAbsent(result.arxivId(), result));

        var rows = new ArrayList<Map<String, Object>>();
        var citations = new ArrayList<ChatCitation>();
        var used = 2;
        for (var result : unique.values()) {
            var remaining = properties.getMaxContextCharactersTotal() - used;
            if (remaining < 100) {
                break;
            }
            var excerpt = truncate(result.abstractText(),
                    Math.min(properties.getMaxContextCharactersPerPaper(), Math.max(0, remaining - 180)));
            var row = new LinkedHashMap<String, Object>();
            row.put("arxivId", result.arxivId());
            row.put("url", ChatCitation.arxivUrlFor(result.arxivId()));
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
            if (json.length() > properties.getMaxContextCharactersTotal()) {
                break;
            }
            rows.add(row);
            used = json.length();
            citations.add(new ChatCitation(result.paperId(), result.arxivId(), result.title(), result.score()));
        }
        return new GroundedContext(json(rows), citations, retrieval.toolsUsed());
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException exception) {
            throw new FoundryClient.FoundryProviderException("Could not serialize grounded context", exception);
        }
    }

    private static String truncate(String value, int limit) {
        if (value == null || limit <= 0) {
            return "";
        }
        if (value.length() <= limit) {
            return value;
        }
        return value.substring(0, Math.max(0, limit - 3)) + "...";
    }
}
