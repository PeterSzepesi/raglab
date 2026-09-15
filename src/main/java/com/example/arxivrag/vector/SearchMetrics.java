package com.example.arxivrag.vector;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class SearchMetrics {

    private final Counter requests;
    private final Counter failures;
    private final Timer latency;
    private final MeterRegistry registry;
    private final io.micrometer.observation.ObservationRegistry observations;
    private final java.util.EnumMap<SearchStage, Timer> stages = new java.util.EnumMap<>(SearchStage.class);

    public SearchMetrics(MeterRegistry registry) {
        this(registry, io.micrometer.observation.ObservationRegistry.NOOP);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public SearchMetrics(MeterRegistry registry, io.micrometer.observation.ObservationRegistry observations) {
        this.registry = registry;
        this.observations = observations;
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

    public void results(String mode, java.util.List<SemanticSearchResult> values) {
        var normalizedMode = safeMode(mode);
        DistributionSummary.builder("rag.search.result.count")
                .tag("mode", normalizedMode).register(registry).record(values.size());
        if (values.isEmpty()) {
            Counter.builder("rag.search.empty").tag("mode", normalizedMode).register(registry).increment();
            return;
        }
        var score = DistributionSummary.builder("rag.search.result.score")
                .tag("mode", normalizedMode).register(registry);
        values.forEach(value -> {
            if (Double.isFinite(value.score())) score.record(value.score());
            Counter.builder("rag.search.result.source")
                    .tag("mode", normalizedMode).tag("source", safeSource(value.retrievalSource()))
                    .register(registry).increment();
        });
    }

    public <T> T measure(SearchStage stage, java.util.function.Supplier<T> supplier) {
        var sample = Timer.start();
        var observation = io.micrometer.observation.Observation.createNotStarted(
                "rag.search.stage", observations).lowCardinalityKeyValue("stage", stage.metricName()).start();
        try {
            return supplier.get();
        } catch (RuntimeException failure) {
            observation.error(failure);
            throw failure;
        } finally {
            observation.stop();
            sample.stop(stages.get(stage));
        }
    }

    private static String safeMode(String value) {
        return java.util.Set.of("semantic", "lexical", "hybrid", "relational").contains(value)
                ? value : "other";
    }

    private static String safeSource(String value) {
        return java.util.Set.of("vector", "lexical", "both", "relational").contains(value)
                ? value : "other";
    }
}
