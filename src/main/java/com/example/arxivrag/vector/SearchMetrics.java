package com.example.arxivrag.vector;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class SearchMetrics {

    private final Counter requests;
    private final Counter failures;
    private final Timer latency;
    private final java.util.EnumMap<SearchStage, Timer> stages = new java.util.EnumMap<>(SearchStage.class);

    public SearchMetrics(MeterRegistry registry) {
        requests = Counter.builder("rag.search.requests").description("Semantic search requests").register(registry);
        failures = Counter.builder("rag.search.failures").description("Search failures").register(registry);
        latency = Timer.builder("rag.search.latency").description("Search service latency").register(registry);
        for (var stage : SearchStage.values()) {
            stages.put(stage, Timer.builder("rag.search.stage.latency")
                    .tag("stage", stage.metricName()).register(registry));
        }
    }

    public Timer.Sample start() {
        requests.increment();
        return Timer.start();
    }

    public void stop(Timer.Sample sample) {
        sample.stop(latency);
    }

    public void failure() {
        failures.increment();
    }

    public <T> T measure(SearchStage stage, java.util.function.Supplier<T> supplier) {
        var sample = Timer.start();
        try {
            return supplier.get();
        } finally {
            sample.stop(stages.get(stage));
        }
    }
}
