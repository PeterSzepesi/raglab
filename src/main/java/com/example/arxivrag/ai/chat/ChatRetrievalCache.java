package com.example.arxivrag.ai.chat;

import com.example.arxivrag.config.CacheConfiguration;
import com.github.benmanes.caffeine.cache.Cache;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class ChatRetrievalCache {

    private final Cache<Key, CachedRetrieval> cache;
    private final ChatProperties properties;
    private final ChatMetrics metrics;

    public ChatRetrievalCache(ChatProperties properties, ChatMetrics metrics,
                              io.micrometer.core.instrument.MeterRegistry registry) {
        properties.validate();
        this.properties = properties;
        this.metrics = metrics;
        cache = CacheConfiguration.variable(properties.getCacheMaximumSize(), value ->
                value.negative() ? properties.getNegativeCacheTtl() : properties.getCacheTtl());
        io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics.monitor(
                registry, cache, "rag.chat.retrieval.cache");
    }

    public ChatRetrieval getOrLoad(Key key, Supplier<ChatRetrieval> loader) {
        if (key == null || loader == null) {
            throw new IllegalArgumentException("cache key and loader must not be null");
        }
        if (!properties.isCacheEnabled()) {
            return load(loader);
        }
        var loaded = new AtomicBoolean();
        var value = cache.get(key, ignored -> {
            loaded.set(true);
            var retrieval = load(loader);
            return new CachedRetrieval(retrieval, retrieval.results().isEmpty()
                    || retrieval.outcome() == ChatRetrievalOutcome.DEGRADED_LEXICAL);
        });
        if (loaded.get()) {
            metrics.cacheMiss();
        } else {
            metrics.cacheHit();
        }
        return value.retrieval();
    }

    private ChatRetrieval load(Supplier<ChatRetrieval> loader) {
        var started = System.nanoTime();
        try {
            var retrieval = loader.get();
            if (retrieval == null) {
                throw new IllegalStateException("retrieval cache loader returned null");
            }
            return retrieval;
        } finally {
            metrics.recordCacheLoad(Duration.ofNanos(System.nanoTime() - started));
        }
    }

    public com.github.benmanes.caffeine.cache.stats.CacheStats stats() {
        return cache.stats();
    }

    public void clear() { cache.invalidateAll(); }

    public record Key(
            String query,
            String mode,
            String category,
            String fromDate,
            String toDate,
            int topK,
            String embeddingModel,
            long corpusGeneration,
            String retrievalPolicyVersion,
            String documentSchemaVersion
    ) {
    }

    private record CachedRetrieval(ChatRetrieval retrieval, boolean negative) {
    }
}
