package com.example.arxivrag.ai.chat;

import com.example.arxivrag.paper.Paper;
import com.example.arxivrag.vector.SemanticSearchResult;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.stereotype.Component;

/** Converts retrieval evidence to canonical, server-authoritative arXiv citations. */
@Component
public class CitationResolver {

    public ChatCitation resolve(SemanticSearchResult result) {
        return new ChatCitation(result.paperId(), result.arxivId(), result.title(), result.score(),
                result.retrievalSource());
    }

    public ChatCitation resolve(Paper paper, String retrievalSource) {
        return new ChatCitation(paper.getId(), paper.getArxivId(), paper.getTitle(), null,
                retrievalSource);
    }

    public List<ChatCitation> resolveResults(List<SemanticSearchResult> results) {
        var unique = new LinkedHashMap<String, ChatCitation>();
        results.forEach(result -> unique.putIfAbsent(result.arxivId(), resolve(result)));
        return List.copyOf(unique.values());
    }

    public List<ChatCitation> resolvePapers(List<Paper> papers, String retrievalSource) {
        var unique = new LinkedHashMap<String, ChatCitation>();
        papers.forEach(paper -> unique.putIfAbsent(paper.getArxivId(), resolve(paper, retrievalSource)));
        return List.copyOf(unique.values());
    }
}
