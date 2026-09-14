package com.example.arxivrag.search;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Process-local generation used only for cache invalidation. Persistent data
 * remains the source of truth; a restart naturally starts with empty caches.
 */
@Component
public class CorpusGeneration {

    private final AtomicLong value = new AtomicLong();

    public long current() {
        return value.get();
    }

    public long increment() {
        return value.incrementAndGet();
    }
}
