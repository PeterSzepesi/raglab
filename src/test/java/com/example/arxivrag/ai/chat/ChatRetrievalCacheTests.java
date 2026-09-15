package com.example.arxivrag.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ChatRetrievalCacheTests {

    @Test
    void concurrentIdenticalLoadsAreSingleFlight() throws Exception {
        var cache = cache(new ChatProperties());
        var key = key("same question");
        var loads = new AtomicInteger();
        var loaderEntered = new CountDownLatch(1);
        var releaseLoader = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(20);
        try {
            var tasks = java.util.stream.IntStream.range(0, 20)
                    .mapToObj(index -> executor.submit(() -> cache.getOrLoad(key, () -> {
                        loads.incrementAndGet();
                        loaderEntered.countDown();
                        await(releaseLoader);
                        return new ChatRetrieval(List.of(), List.of(), ChatRetrievalOutcome.NO_EVIDENCE);
                    })))
                    .toList();

            assertThat(loaderEntered.await(2, TimeUnit.SECONDS)).isTrue();
            releaseLoader.countDown();
            for (var task : tasks) {
                assertThat(task.get(2, TimeUnit.SECONDS).outcome()).isEqualTo(ChatRetrievalOutcome.NO_EVIDENCE);
            }
            assertThat(loads).hasValue(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void emptyResultIsCachedWithTheNegativePolicy() {
        var cache = cache(new ChatProperties());
        var loads = new AtomicInteger();

        cache.getOrLoad(key("missing"), () -> missing(loads));
        cache.getOrLoad(key("missing"), () -> missing(loads));

        assertThat(loads).hasValue(1);
        assertThat(cache.stats().hitCount()).isEqualTo(1);
    }

    private static ChatRetrieval missing(AtomicInteger loads) {
        loads.incrementAndGet();
        return new ChatRetrieval(List.of(), List.of(), ChatRetrievalOutcome.NO_EVIDENCE);
    }

    private static ChatRetrievalCache cache(ChatProperties properties) {
        var registry = new SimpleMeterRegistry();
        return new ChatRetrievalCache(properties, new ChatMetrics(registry), registry);
    }

    private static ChatRetrievalCache.Key key(String query) {
        return new ChatRetrievalCache.Key(query, "SEMANTIC", null, null, null, 4,
                "embedding-model", 1, "retrieval-v1", "document-v1");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
