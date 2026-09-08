package com.example.arxivrag.embedding;

import java.time.LocalDate;
import java.util.List;

public record RetrievalDocument(
        Long paperId,
        String arxivId,
        String chunkId,
        String text,
        String contentHash,
        List<String> categories,
        LocalDate submittedDate
) {
}
