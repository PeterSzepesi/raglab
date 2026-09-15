package com.example.arxivrag.ai.chat;

import com.example.arxivrag.search.SearchMode;
import java.time.LocalDate;

/**
 * One immutable decision describing how a chat question is retrieved and grounded.
 * Keeping these values together prevents routing, ranking, prompt, and cache behavior
 * from silently drifting apart.
 */
public record RetrievalPolicy(
        String version,
        QuestionIntentClassifier.QuestionIntent intent,
        SearchMode mode,
        double vectorWeight,
        double lexicalWeight,
        int topK,
        double minimumScore,
        Filters filters,
        int maxContextCharacters,
        int maxContextCharactersPerPaper,
        int maxCompletionTokens,
        double duplicateTitleJaccardThreshold,
        boolean relationalOnly
) {
    public RetrievalPolicy {
        if (version == null || version.isBlank()) throw new IllegalArgumentException("policy version must not be blank");
        if (intent == null || mode == null || filters == null) {
            throw new IllegalArgumentException("policy intent, mode, and filters must not be null");
        }
        if (topK < 1 || maxContextCharacters < 100 || maxContextCharactersPerPaper < 100
                || maxContextCharactersPerPaper > maxContextCharacters || maxCompletionTokens < 1) {
            throw new IllegalArgumentException("retrieval policy budgets are invalid");
        }
        if (!Double.isFinite(vectorWeight) || !Double.isFinite(lexicalWeight)
                || vectorWeight < 0d || lexicalWeight < 0d || vectorWeight + lexicalWeight <= 0d) {
            throw new IllegalArgumentException("retrieval policy weights are invalid");
        }
        if (!Double.isFinite(minimumScore) || !Double.isFinite(duplicateTitleJaccardThreshold)
                || duplicateTitleJaccardThreshold <= 0d || duplicateTitleJaccardThreshold > 1d) {
            throw new IllegalArgumentException("retrieval policy thresholds are invalid");
        }
    }

    public static RetrievalPolicy legacyDefaults() {
        return new RetrievalPolicy("legacy", QuestionIntentClassifier.QuestionIntent.DEFINITION_OR_FACT,
                SearchMode.SEMANTIC, 0.7d, 0.3d, 4, 0d, Filters.none(),
                4000, 800, 400, 0.8d, false);
    }

    public record Filters(String category, LocalDate fromDate, LocalDate toDate) {
        public static Filters none() {
            return new Filters(null, null, null);
        }

        public boolean present() {
            return category != null || fromDate != null || toDate != null;
        }
    }
}
