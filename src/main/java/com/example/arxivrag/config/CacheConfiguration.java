package com.example.arxivrag.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;

import java.time.Duration;
import java.util.function.Function;

/** Central construction policy for all in-process caches. Domain classes still own their typed keys. */
public final class CacheConfiguration {
    private CacheConfiguration() { }

    public static <K, V> Cache<K, V> fixed(long maximumSize, Duration ttl) {
        return Caffeine.newBuilder().maximumSize(maximumSize).expireAfterWrite(ttl).recordStats().build();
    }

    public static <K, V> Cache<K, V> variable(long maximumSize, Function<V, Duration> ttl) {
        return Caffeine.newBuilder().maximumSize(maximumSize).expireAfter(new Expiry<K, V>() {
            @Override public long expireAfterCreate(K key, V value, long currentTime) {
                return ttl.apply(value).toNanos();
            }
            @Override public long expireAfterUpdate(K key, V value, long currentTime, long currentDuration) {
                return ttl.apply(value).toNanos();
            }
            @Override public long expireAfterRead(K key, V value, long currentTime, long currentDuration) {
                return currentDuration;
            }
        }).recordStats().build();
    }
}
