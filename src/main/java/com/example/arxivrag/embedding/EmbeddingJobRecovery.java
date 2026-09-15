package com.example.arxivrag.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EmbeddingJobRecovery {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmbeddingJobRecovery.class);
    private final EmbeddingJobRepository repository;

    public EmbeddingJobRecovery(EmbeddingJobRepository repository) {
        this.repository = repository;
    }

    @Scheduled(fixedDelayString = "${embedding.jobs.recovery-interval:60s}")
    public void recoverExpiredLeases() {
        var recovered = repository.recoverAbandonedJobs();
        if (recovered > 0) {
            LOGGER.warn("Marked {} abandoned embedding job(s) as INTERRUPTED; they can be resumed safely", recovered);
        }
    }
}
