package com.example.arxivrag.ai.foundry;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Independent local circuit per Foundry operation and deployment. */
@Component
@Profile("foundry")
public class ProviderCircuitBreaker {
    private static final int FAILURE_THRESHOLD = 5;
    private static final Duration OPEN_DURATION = Duration.ofSeconds(30);
    private final MeterRegistry registry;
    private final ConcurrentHashMap<Key, Circuit> circuits = new ConcurrentHashMap<>();

    public ProviderCircuitBreaker(MeterRegistry registry) {
        this.registry = registry;
    }

    public Permit acquire() {
        return acquire("default", FoundryOperation.CHAT);
    }

    public Permit acquire(String deployment, FoundryOperation operation) {
        var key = new Key(safeDeployment(deployment), operation);
        var circuit = circuits.computeIfAbsent(key, this::create);
        circuit.acquire();
        return new Permit(circuit);
    }

    public State state() {
        return circuits.values().stream().map(Circuit::state).max(java.util.Comparator.comparingDouble(
                State::metricValue)).orElse(State.CLOSED);
    }

    public Map<String, State> states() {
        return circuits.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                entry -> entry.getKey().operation().metricName() + ":" + entry.getKey().deployment(),
                entry -> entry.getValue().state()));
    }

    private Circuit create(Key key) {
        var circuit = new Circuit();
        Gauge.builder("rag.foundry.circuit.state", circuit, value -> value.state().metricValue())
                .tag("operation", key.operation().metricName()).tag("deployment", key.deployment())
                .description("Foundry circuit state: 0 closed, 0.5 half-open, 1 open")
                .register(registry);
        return circuit;
    }

    private static String safeDeployment(String value) {
        if (value == null || value.isBlank()) return "unknown";
        return value.length() <= 80 ? value : value.substring(0, 80);
    }

    private record Key(String deployment, FoundryOperation operation) { }

    private static final class Circuit {
        private int consecutiveFailures;
        private Instant openUntil;
        private boolean halfOpenProbe;

        synchronized void acquire() {
            var now = Instant.now();
            if (openUntil != null && now.isBefore(openUntil)) {
                throw new CircuitOpenException("Model provider circuit is open until " + openUntil);
            }
            if (openUntil != null) {
                if (halfOpenProbe) throw new CircuitOpenException("Model provider recovery probe is already running");
                halfOpenProbe = true;
            }
        }

        synchronized State state() {
            if (openUntil == null) return State.CLOSED;
            return Instant.now().isBefore(openUntil) ? State.OPEN : State.HALF_OPEN;
        }

        synchronized void success() {
            consecutiveFailures = 0;
            openUntil = null;
            halfOpenProbe = false;
        }

        synchronized void failure() {
            halfOpenProbe = false;
            consecutiveFailures++;
            if (openUntil != null || consecutiveFailures >= FAILURE_THRESHOLD) {
                openUntil = Instant.now().plus(OPEN_DURATION);
            }
        }
    }

    public enum State {
        CLOSED(0), HALF_OPEN(0.5), OPEN(1);
        private final double metricValue;
        State(double metricValue) { this.metricValue = metricValue; }
        public double metricValue() { return metricValue; }
    }

    public static final class Permit implements AutoCloseable {
        private final Circuit circuit;
        private boolean completed;
        private Permit(Circuit circuit) { this.circuit = circuit; }
        public void success() { if (!completed) { completed = true; circuit.success(); } }
        public void failure() { if (!completed) { completed = true; circuit.failure(); } }
        @Override public void close() { if (!completed) failure(); }
    }

    public static final class CircuitOpenException extends RuntimeException {
        public CircuitOpenException(String message) { super(message); }
    }
}
