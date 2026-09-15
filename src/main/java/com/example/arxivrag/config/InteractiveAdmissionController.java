package com.example.arxivrag.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class InteractiveAdmissionController {

    private final Semaphore chats;
    private final Semaphore retrievals;
    private final ExecutionProperties properties;
    private final Counter chatRejected;
    private final Counter retrievalRejected;

    public InteractiveAdmissionController(ExecutionProperties properties, MeterRegistry registry) {
        this.properties = properties;
        this.chats = new Semaphore(properties.getMaxConcurrentChats(), true);
        this.retrievals = new Semaphore(properties.getMaxConcurrentRetrievals(), true);
        this.chatRejected = Counter.builder("rag.chat.admission.rejected").register(registry);
        this.retrievalRejected = Counter.builder("rag.search.admission.rejected").register(registry);
        Gauge.builder("rag.chat.admission.available", chats, Semaphore::availablePermits)
                .register(registry);
        Gauge.builder("rag.search.admission.available", retrievals, Semaphore::availablePermits)
                .register(registry);
    }

    public Permit acquireChat() {
        try {
            if (!chats.tryAcquire(properties.getAdmissionWait().toMillis(), TimeUnit.MILLISECONDS)) {
                chatRejected.increment();
                throw new CapacityExceededException("Chat capacity is full; retry shortly");
            }
            return new Permit(chats);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CapacityExceededException("Chat admission was interrupted");
        }
    }

    public Permit acquireRetrieval() {
        try {
            if (!retrievals.tryAcquire(properties.getAdmissionWait().toMillis(), TimeUnit.MILLISECONDS)) {
                retrievalRejected.increment();
                throw new CapacityExceededException("Search capacity is full; retry shortly");
            }
            return new Permit(retrievals);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CapacityExceededException("Search admission was interrupted");
        }
    }

    public int availableChatPermits() {
        return chats.availablePermits();
    }

    public int availableRetrievalPermits() {
        return retrievals.availablePermits();
    }

    public static final class Permit implements AutoCloseable {
        private final Semaphore semaphore;
        private boolean closed;

        private Permit(Semaphore semaphore) {
            this.semaphore = semaphore;
        }

        @Override
        public synchronized void close() {
            if (!closed) {
                semaphore.release();
                closed = true;
            }
        }
    }
}
