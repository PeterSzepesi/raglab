package com.example.arxivrag.search;

import com.example.arxivrag.config.CacheConfiguration;
import com.github.benmanes.caffeine.cache.Cache;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class QueryEmbeddingCache {
    private final Cache<Key, List<Double>> cache;
    private final boolean enabled;

    public QueryEmbeddingCache(SearchCacheProperties properties, MeterRegistry registry) {
        this.enabled = properties.isEnabled();
        this.cache = CacheConfiguration.fixed(properties.getQueryEmbeddingMaximumSize(),
                properties.getQueryEmbeddingTtl());
        io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics.monitor(
                registry, cache, "rag.search.query.embedding.cache");
    }

    public List<Double> getOrLoad(Key key, Supplier<List<Double>> loader) {
        return enabled ? cache.get(key, ignored -> loader.get()) : loader.get();
    }

    public com.github.benmanes.caffeine.cache.stats.CacheStats stats() { return cache.stats(); }
    public void clear() { cache.invalidateAll(); }

    public record Key(String normalizedQuery, String model, int dimensions, String schemaVersion) { }
}
