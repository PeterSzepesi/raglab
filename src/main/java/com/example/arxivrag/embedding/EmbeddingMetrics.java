package com.example.arxivrag.embedding;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class EmbeddingMetrics {

    private final Counter embedded;
    private final Counter skipped;
    private final Counter providerRequests;
    private final Counter providerFailures;
    private final Timer providerLatency;
    private final Timer vectorWriteLatency;

    public EmbeddingMetrics(MeterRegistry registry) {
        embedded = Counter.builder("rag.embedding.documents")
                .tag("outcome", "embedded").description("Documents written to the vector store").register(registry);
        skipped = Counter.builder("rag.embedding.documents")
                .tag("outcome", "skipped").description("Documents skipped by checkpoint").register(registry);
        providerRequests = Counter.builder("rag.embedding.provider.requests")
                .description("Embedding provider requests").register(registry);
        providerFailures = Counter.builder("rag.embedding.provider.failures")
                .description("Embedding provider request failures").register(registry);
        providerLatency = Timer.builder("rag.embedding.provider.latency")
                .description("Embedding provider latency").register(registry);
        vectorWriteLatency = Timer.builder("rag.embedding.vector.write.latency")
                .description("Vector and checkpoint write latency").register(registry);
    }

    public void embedded(long count) {
        embedded.increment(count);
    }

    public void skipped(long count) {
        skipped.increment(count);
    }

    public void providerRequest(Duration latency) {
        providerRequests.increment();
        providerLatency.record(latency);
    }

    public void providerFailure() {
        providerFailures.increment();
    }

    public Timer.Sample startVectorWrite() {
        return Timer.start();
    }

    public void stopVectorWrite(Timer.Sample sample) {
        sample.stop(vectorWriteLatency);
    }
}
