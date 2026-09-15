package com.example.arxivrag.ai.chat;

import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Transparent, deterministic routing rules; this deliberately makes no extra model call. */
@Component
public class QuestionIntentClassifier {

    private static final Pattern ARXIV_ID = Pattern.compile(
            "(?i)(?:\\d{4}\\.\\d{4,5}|[a-z][a-z0-9.-]*/\\d{7})(?:v\\d+)?");

    public IntentPlan classify(String question, int configuredTopK, Integer configuredOutputTokens) {
        var normalized = question == null ? "" : question.toLowerCase(Locale.ROOT);
        var intent = ARXIV_ID.matcher(normalized).find() ? QuestionIntent.EXACT_PAPER
                : containsAny(normalized, "compare", "difference", "versus", " vs ") ? QuestionIntent.COMPARISON
                : containsAny(normalized, "survey", "literature", "overview", "state of the art", "list papers")
                ? QuestionIntent.LITERATURE_SURVEY
                : QuestionIntent.DEFINITION_OR_FACT;
        var topK = switch (intent) {
            case EXACT_PAPER -> 2;
            case DEFINITION_OR_FACT -> Math.min(configuredTopK, 3);
            case COMPARISON -> Math.max(configuredTopK, 6);
            case LITERATURE_SURVEY -> Math.max(configuredTopK, 8);
        };
        topK = Math.max(1, Math.min(20, topK));
        var recommendedOutput = switch (intent) {
            case EXACT_PAPER -> 180;
            case DEFINITION_OR_FACT -> 250;
            case COMPARISON -> 400;
            case LITERATURE_SURVEY -> 600;
        };
        var output = configuredOutputTokens == null ? recommendedOutput
                : Math.min(configuredOutputTokens, recommendedOutput);
        return new IntentPlan(intent, topK, output);
    }

    private static boolean containsAny(String value, String... candidates) {
        return java.util.Arrays.stream(candidates).anyMatch(value::contains);
    }

    public enum QuestionIntent {
        EXACT_PAPER,
        DEFINITION_OR_FACT,
        COMPARISON,
        LITERATURE_SURVEY
    }

    public record IntentPlan(QuestionIntent intent, int retrievalTopK, int maxCompletionTokens) { }
}
