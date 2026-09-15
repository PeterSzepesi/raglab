package com.example.arxivrag.search;

import com.example.arxivrag.embedding.RetrievalDocumentFactory;
import com.example.arxivrag.paper.Paper;
import com.example.arxivrag.paper.PaperRepository;
import com.example.arxivrag.vector.SemanticSearchResult;
import com.example.arxivrag.vector.SearchMetrics;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

@Service
public class LexicalSearchService {

    private final PaperRepository paperRepository;
    private final RetrievalDocumentFactory retrievalDocumentFactory;
    private final SearchMetrics metrics;

    public LexicalSearchService(PaperRepository paperRepository, RetrievalDocumentFactory retrievalDocumentFactory,
                                SearchMetrics metrics) {
        this.paperRepository = paperRepository;
        this.retrievalDocumentFactory = retrievalDocumentFactory;
        this.metrics = metrics;
    }

    public java.util.List<SemanticSearchResult> search(
            String query, int topK, String category, LocalDate fromDate, LocalDate toDate) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        var papers = paperRepository.searchLexical(query.trim(), category, fromDate, toDate, topK);
        var results = java.util.stream.IntStream.range(0, papers.size())
                .mapToObj(index -> toResult(papers.get(index), 1.0d / (index + 1)))
                .toList();
        metrics.results("lexical", results);
        return results;
    }

    private SemanticSearchResult toResult(Paper paper, double score) {
        return new SemanticSearchResult(
                paper.getId(), paper.getArxivId(), paper.getTitle(), paper.getAbstractText(), paper.getAuthors(),
                paper.getCategories(), paper.getSubmittedDate(), score, retrievalDocumentFactory.fromPaper(paper).text(),
                "lexical"
        );
    }
}
