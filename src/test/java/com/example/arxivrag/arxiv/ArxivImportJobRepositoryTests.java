package com.example.arxivrag.arxiv;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class ArxivImportJobRepositoryTests {

    @Autowired
    private ArxivImportJobRepository jobs;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clearJobs() {
        jdbc.update("delete from import_jobs");
    }

    @Test
    void leasePreventsTwoWorkersFromClaimingTheSameImportAndAllowsExpiredRecovery() {
        var id = jobs.create("C:/datasets/arxiv.jsonl", new ArxivImportOptions(List.of("cs.AI"), null, 25));

        assertThat(jobs.claim(id, "worker-a")).isTrue();
        assertThat(jobs.claim(id, "worker-b")).isFalse();

        jobs.progress(id, "worker-b", new ArxivImportService.ImportProgress(99, 99, 99, 0, 0, 0));
        jobs.progress(id, "worker-a", new ArxivImportService.ImportProgress(10, 8, 6, 1, 2, 1));

        var running = jobs.find(id).orElseThrow();
        assertThat(running.status()).isEqualTo("RUNNING");
        assertThat(running.recordsRead()).isEqualTo(10);
        assertThat(jobs.recoverExpired()).isZero();

        jdbc.update("update import_jobs set lease_expires_at=? where job_id=?",
                Timestamp.from(Instant.now().minusSeconds(1)), id.toString());

        assertThat(jobs.recoverExpired()).isEqualTo(1);
        assertThat(jobs.find(id).orElseThrow().status()).isEqualTo("INTERRUPTED");
        assertThat(jobs.claim(id, "worker-b")).isTrue();

        jobs.complete(id, "worker-b", new ArxivImportResult(10, 8, 6, 1, 2, 1, Duration.ofMillis(500)),
                "abc123", false, List.of(4L));

        var completed = jobs.find(id).orElseThrow();
        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(completed.sourceSha256()).isEqualTo("abc123");
        assertThat(completed.malformedLineSamples()).containsExactly(4L);
        assertThat(completed.completedAt()).isNotNull();
    }

    @Test
    void queuedImportCanBeCancelledDurably() {
        var id = jobs.create("C:/datasets/arxiv.jsonl", new ArxivImportOptions(List.of(), null, null));

        assertThat(jobs.requestCancellation(id)).isTrue();
        assertThat(jobs.cancelQueued(id)).isTrue();

        var cancelled = jobs.find(id).orElseThrow();
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(cancelled.cancelRequested()).isTrue();
    }
}
