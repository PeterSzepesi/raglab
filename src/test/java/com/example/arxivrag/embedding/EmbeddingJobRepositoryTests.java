package com.example.arxivrag.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class EmbeddingJobRepositoryTests {

    @Autowired
    private EmbeddingJobRepository jobs;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clearJobs() {
        jdbc.update("delete from embedding_jobs");
    }

    @Test
    void onlyTheLeaseOwnerCanUpdateOrFinishAClaimedJob() {
        var request = new EmbeddingIngestionRequest(10, null, null, null, null, null, false);
        var id = jobs.createQueued(request, "test-model", 1536, "document-v1");
        assertThat(jobs.claim(id, 10, "worker-a")).isTrue();
        assertThat(jobs.claim(id, 10, "worker-b")).isFalse();

        var progress = new EmbeddingIngestionProgress();
        progress.start(id, 10, "test-model", 1536, 2);
        progress.markScanned();
        jobs.updateProgress(id, "worker-b", progress.snapshot(), 99L);
        assertThat(jobs.findById(id).orElseThrow().papersScanned()).isZero();

        jobs.updateProgress(id, "worker-a", progress.snapshot(), 1L);
        assertThat(jobs.findById(id).orElseThrow().papersScanned()).isEqualTo(1);
        jobs.complete(id, "worker-b", progress.snapshot());
        assertThat(jobs.findById(id).orElseThrow().status()).isEqualTo("RUNNING");

        jobs.complete(id, "worker-a", progress.snapshot());
        assertThat(jobs.findById(id).orElseThrow().status()).isEqualTo("COMPLETED");
    }

    @Test
    void expiredLeaseCanBeRecoveredAndReclaimedByAnotherWorker() {
        var request = new EmbeddingIngestionRequest(null, null, null, null, null, null, false);
        var id = jobs.createQueued(request, "test-model", 1536, "document-v1");
        assertThat(jobs.claim(id, 4, "worker-a")).isTrue();
        assertThat(jobs.recoverAbandonedJobs()).isZero();

        jdbc.update("update embedding_jobs set lease_expires_at=? where job_id=?",
                Timestamp.from(Instant.now().minusSeconds(1)), id.toString());

        assertThat(jobs.recoverAbandonedJobs()).isEqualTo(1);
        assertThat(jobs.findById(id).orElseThrow().status()).isEqualTo("INTERRUPTED");
        assertThat(jobs.claim(id, 4, "worker-b")).isTrue();
    }

    @Test
    void providerWaitIsDurableCancellableAndLeaseRecoverable() {
        var request = new EmbeddingIngestionRequest(null, null, null, null, null, null, false);
        var id = jobs.createQueued(request, "test-model", 1536, "document-v1");
        assertThat(jobs.claim(id, 4, "worker-a")).isTrue();
        var progress = new EmbeddingIngestionProgress();
        progress.start(id, 4, "test-model", 1536, 2);
        progress.providerRequestStarted();

        jobs.markWaitingForProvider(id, "worker-a", progress.snapshot());

        assertThat(jobs.findById(id).orElseThrow().status()).isEqualTo("WAITING_FOR_PROVIDER_CAPACITY");
        assertThat(jobs.requestCancellation(id)).isTrue();
        jdbc.update("update embedding_jobs set lease_expires_at=? where job_id=?",
                Timestamp.from(Instant.now().minusSeconds(1)), id.toString());
        assertThat(jobs.recoverAbandonedJobs()).isEqualTo(1);
        assertThat(jobs.findById(id).orElseThrow().status()).isEqualTo("INTERRUPTED");
    }
}
