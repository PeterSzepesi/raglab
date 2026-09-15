package com.example.arxivrag.ai.chat;

import com.example.arxivrag.config.CacheConfiguration;
import com.example.arxivrag.search.CorpusGeneration;
import com.github.benmanes.caffeine.cache.Cache;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ChatAnswerCache {
    private final Cache<Key, GroundedChatResponse> cache;
    private final ChatProperties properties;
    private final CorpusGeneration corpusGeneration;

    public ChatAnswerCache(ChatProperties properties, CorpusGeneration corpusGeneration, MeterRegistry registry) {
        this.properties = properties;
        this.corpusGeneration = corpusGeneration;
        this.cache = CacheConfiguration.fixed(properties.getAnswerCacheMaximumSize(), properties.getAnswerCacheTtl());
        io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics.monitor(
                registry, cache, "rag.chat.answer.cache");
    }

    public boolean enabled() { return properties.isAnswerCacheEnabled(); }

    public Key key(ChatRequest request, GroundedContext context, String deployment,
                   QuestionIntentClassifier.IntentPlan intent) {
        return new Key(request.message().trim().toLowerCase(Locale.ROOT),
                text(request.retrievalMode()), text(request.category()), text(request.fromDate()), text(request.toDate()),
                hash(context.evidenceJson()), deployment, properties.getPromptVersion(),
                properties.getRetrievalPolicyVersion(), properties.getReasoningEffort(),
                intent.maxCompletionTokens(), corpusGeneration.current());
    }

    public Optional<GroundedChatResponse> get(Key key) {
        return enabled() ? Optional.ofNullable(cache.getIfPresent(key)) : Optional.empty();
    }

    public void put(Key key, GroundedChatResponse response) {
        if (enabled()) cache.put(key, response);
    }

    public com.github.benmanes.caffeine.cache.stats.CacheStats stats() { return cache.stats(); }
    public void clear() { cache.invalidateAll(); }

    private static String text(Object value) { return value == null ? null : value.toString(); }
    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record Key(String question, String mode, String category, String fromDate, String toDate,
                      String evidenceHash, String deployment, String promptVersion, String retrievalVersion,
                      String reasoningEffort, int outputBudget, long corpusGeneration) { }
}
