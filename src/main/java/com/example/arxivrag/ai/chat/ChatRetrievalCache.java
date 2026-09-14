package com.example.arxivrag.ai.chat;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ChatRetrievalCache {

    private final Cache<Key, ChatRetrieval> cache;
    private final ChatProperties properties;
    private final ChatMetrics metrics;

    public ChatRetrievalCache(ChatProperties properties, ChatMetrics metrics,
                              io.micrometer.core.instrument.MeterRegistry registry) {
        properties.validate();
        this.properties = properties;
        this.metrics = metrics;
        cache = Caffeine.newBuilder()
                .maximumSize(properties.getCacheMaximumSize())
                .expireAfterWrite(properties.getCacheTtl())
                .recordStats()
                .build();
        io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics.monitor(
                registry, cache, "rag.chat.retrieval.cache");
    }

    public Optional<ChatRetrieval> get(Key key) {
        if (!properties.isCacheEnabled()) {
            return Optional.empty();
        }
        var value = cache.getIfPresent(key);
        if (value == null) {
            metrics.cacheMiss();
            return Optional.empty();
        }
        metrics.cacheHit();
        return Optional.of(value);
    }

    public void put(Key key, ChatRetrieval retrieval) {
        if (properties.isCacheEnabled() && retrieval != null && !retrieval.results().isEmpty()) {
            cache.put(key, retrieval);
        }
    }

    public void recordLoad(java.time.Duration duration) {
        metrics.recordCacheLoad(duration);
    }

    public com.github.benmanes.caffeine.cache.stats.CacheStats stats() {
        return cache.stats();
    }

    public record Key(
            String query,
            String mode,
            String category,
            String fromDate,
            String toDate,
            int topK,
            String embeddingModel,
            long corpusGeneration
    ) {
    }
}
