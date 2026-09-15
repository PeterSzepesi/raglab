package com.example.arxivrag.ai.chat;

import com.example.arxivrag.search.SearchMode;
import com.example.arxivrag.search.SearchRetrievalProperties;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Creates transparent intent-specific retrieval, context, and output budgets. */
@Component
public class RetrievalPolicyFactory {

    private final ChatProperties chat;
    private final SearchRetrievalProperties search;
    private final QuestionIntentClassifier classifier;

    public RetrievalPolicyFactory(ChatProperties chat, SearchRetrievalProperties search,
                                  QuestionIntentClassifier classifier) {
        chat.validate();
        search.validate();
        this.chat = chat;
        this.search = search;
        this.classifier = classifier;
    }

    public RetrievalPolicy create(ChatRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        var plan = classifier.classify(request.message(), chat.getRetrievalTopK(), chat.getMaxCompletionTokens());
        var filters = new RetrievalPolicy.Filters(normalize(request.category()), request.fromDate(), request.toDate());
        var structuredOnly = (request.retrievalMode() == null || request.retrievalMode().isBlank())
                && filters.present();
        var mode = structuredOnly ? SearchMode.LEXICAL : requestedOrDefault(request, plan.intent());
        var totalContext = Math.min(chat.getMaxContextCharactersTotal(), contextBudget(plan.intent()));
        var perPaper = Math.min(chat.getMaxContextCharactersPerPaper(),
                Math.max(100, totalContext / Math.max(1, Math.min(plan.retrievalTopK(), 4))));
        var minimumScore = switch (mode) {
            case SEMANTIC -> search.getSemanticMinimumSimilarity();
            case HYBRID -> search.getHybridMinimumScore();
            case AUTO, LEXICAL -> 0d;
        };
        return new RetrievalPolicy(chat.getRetrievalPolicyVersion(), plan.intent(), mode,
                search.getHybridVectorWeight(), search.getHybridLexicalWeight(), plan.retrievalTopK(), minimumScore,
                filters, totalContext, perPaper, plan.maxCompletionTokens(),
                search.getDuplicateTitleJaccardThreshold(), structuredOnly);
    }

    private SearchMode requestedOrDefault(ChatRequest request, QuestionIntentClassifier.QuestionIntent intent) {
        if (request.retrievalMode() != null && !request.retrievalMode().isBlank()) {
            var requested = SearchMode.parse(request.retrievalMode());
            return requested == SearchMode.AUTO ? defaultMode(request.message(), intent) : requested;
        }
        return defaultMode(request.message(), intent);
    }

    private static SearchMode defaultMode(String question, QuestionIntentClassifier.QuestionIntent intent) {
        var value = question == null ? "" : question.toLowerCase(Locale.ROOT);
        if (intent == QuestionIntentClassifier.QuestionIntent.EXACT_PAPER
                || value.matches(".*(?:title|author):.+") || value.matches(".*\"[^\"]{4,}\".*")) {
            return SearchMode.LEXICAL;
        }
        return SearchMode.HYBRID;
    }

    private int contextBudget(QuestionIntentClassifier.QuestionIntent intent) {
        return switch (intent) {
            case EXACT_PAPER -> 1400;
            case DEFINITION_OR_FACT -> 2600;
            case COMPARISON -> 3600;
            case LITERATURE_SURVEY -> chat.getMaxContextCharactersTotal();
        };
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
