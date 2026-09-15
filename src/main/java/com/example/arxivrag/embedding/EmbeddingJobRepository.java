package com.example.arxivrag.embedding;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EmbeddingJobRepository {

    private static final Duration LEASE_DURATION = Duration.ofMinutes(2);
    private final JdbcTemplate jdbcTemplate;

    public EmbeddingJobRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UUID createQueued(EmbeddingIngestionRequest request, String model, int dimension,
                             String documentSchemaVersion) {
        var jobId = UUID.randomUUID();
        jdbcTemplate.update("""
                        insert into embedding_jobs (
                            job_id, status, embedding_model, embedding_dimension, document_schema_version,
                            total_papers, started_at, updated_at, max_papers, from_paper_id, to_paper_id,
                            category, from_date, to_date, dry_run
                        ) values (?, 'QUEUED', ?, ?, ?, 0, current_timestamp, current_timestamp,
                                  ?, ?, ?, ?, ?, ?, ?)
                        """,
                jobId.toString(), model, dimension, documentSchemaVersion,
                request.maxPapers(), request.fromPaperId(), request.toPaperId(), request.category(),
                date(request.fromDate()), date(request.toDate()), request.dryRun());
        return jobId;
    }

    public boolean claim(UUID jobId, long totalPapers, String ownerId) {
        var now = Instant.now();
        return jdbcTemplate.update("""
                        update embedding_jobs set status = 'RUNNING', total_papers = ?, owner_id = ?,
                            cancel_requested = false, lease_expires_at = ?, started_at = current_timestamp,
                            updated_at = current_timestamp, completed_at = null, last_error = null
                        where job_id = ? and status in ('QUEUED', 'INTERRUPTED', 'FAILED', 'CANCELLED')
                        """,
                totalPapers, ownerId, Timestamp.from(now.plus(LEASE_DURATION)), jobId.toString()) == 1;
    }

    public void updateProgress(UUID jobId, String ownerId, EmbeddingIngestionProgress.Snapshot progress,
                               Long lastPaperId) {
        jdbcTemplate.update("""
                        update embedding_jobs set
                            papers_scanned = ?, documents_prepared = ?, embedded = ?, skipped = ?,
                            failures = ?, provider_requests = ?, last_paper_id = ?, updated_at = current_timestamp,
                            lease_expires_at = ?
                        where job_id = ? and status in ('RUNNING', 'WAITING_FOR_PROVIDER_CAPACITY') and owner_id = ?
                        """,
                progress.papersScanned(), progress.documentsPrepared(), progress.embedded(), progress.skipped(),
                progress.failures(), progress.providerRequests(), lastPaperId,
                Timestamp.from(Instant.now().plus(LEASE_DURATION)), jobId.toString(), ownerId);
    }

    public void markWaitingForProvider(UUID jobId, String ownerId,
                                       EmbeddingIngestionProgress.Snapshot progress) {
        jdbcTemplate.update("""
                update embedding_jobs set status = 'WAITING_FOR_PROVIDER_CAPACITY', provider_requests = ?,
                    updated_at = current_timestamp, lease_expires_at = ?
                where job_id = ? and status = 'RUNNING' and owner_id = ?
                """, progress.providerRequests(), Timestamp.from(Instant.now().plus(LEASE_DURATION)),
                jobId.toString(), ownerId);
    }

    public void markRunning(UUID jobId, String ownerId, EmbeddingIngestionProgress.Snapshot progress) {
        jdbcTemplate.update("""
                update embedding_jobs set status = 'RUNNING', provider_requests = ?,
                    updated_at = current_timestamp, lease_expires_at = ?
                where job_id = ? and status = 'WAITING_FOR_PROVIDER_CAPACITY' and owner_id = ?
                """, progress.providerRequests(), Timestamp.from(Instant.now().plus(LEASE_DURATION)),
                jobId.toString(), ownerId);
    }

    public void complete(UUID jobId, String ownerId, EmbeddingIngestionProgress.Snapshot progress) {
        finish(jobId, ownerId, "COMPLETED", progress, null);
    }

    public void fail(UUID jobId, String ownerId, EmbeddingIngestionProgress.Snapshot progress, Throwable exception) {
        finish(jobId, ownerId, "FAILED", progress, safeError(exception));
    }

    public void cancel(UUID jobId, String ownerId, EmbeddingIngestionProgress.Snapshot progress) {
        finish(jobId, ownerId, "CANCELLED", progress, null);
    }

    private void finish(UUID jobId, String ownerId, String status, EmbeddingIngestionProgress.Snapshot progress,
                        String error) {
        jdbcTemplate.update("""
                        update embedding_jobs set status = ?, papers_scanned = ?, documents_prepared = ?,
                            embedded = ?, skipped = ?, failures = ?, provider_requests = ?, last_error = ?,
                            updated_at = current_timestamp, completed_at = current_timestamp,
                            lease_expires_at = null, owner_id = null
                        where job_id = ? and owner_id = ?
                        """,
                status, progress.papersScanned(), progress.documentsPrepared(), progress.embedded(),
                progress.skipped(), progress.failures(), progress.providerRequests(), error, jobId.toString(), ownerId);
    }

    public boolean requestCancellation(UUID jobId) {
        return jdbcTemplate.update("""
                update embedding_jobs set cancel_requested = true, updated_at = current_timestamp
                where job_id = ? and status in ('QUEUED', 'RUNNING', 'WAITING_FOR_PROVIDER_CAPACITY')
                """, jobId.toString()) == 1;
    }

    public boolean cancelIfQueued(UUID jobId) {
        return jdbcTemplate.update("""
                update embedding_jobs set status = 'CANCELLED', completed_at = current_timestamp,
                    updated_at = current_timestamp, owner_id = null, lease_expires_at = null
                where job_id = ? and status = 'QUEUED'
                """, jobId.toString()) == 1;
    }

    public boolean isCancellationRequested(UUID jobId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "select cancel_requested from embedding_jobs where job_id = ?", Boolean.class, jobId.toString()));
    }

    public int recoverAbandonedJobs() {
        return jdbcTemplate.update("""
                update embedding_jobs set status = 'INTERRUPTED', owner_id = null, lease_expires_at = null,
                    last_error = 'Application stopped before the job completed', updated_at = current_timestamp
                where status in ('RUNNING', 'WAITING_FOR_PROVIDER_CAPACITY')
                  and (lease_expires_at is null or lease_expires_at < current_timestamp)
                """);
    }

    public Optional<EmbeddingIngestionRequest> requestFor(UUID jobId) {
        return jdbcTemplate.query("select * from embedding_jobs where job_id = ?", (resultSet, row) ->
                new EmbeddingIngestionRequest(
                        resultSet.getObject("max_papers", Integer.class),
                        resultSet.getObject("from_paper_id", Long.class),
                        resultSet.getObject("to_paper_id", Long.class),
                        resultSet.getString("category"),
                        localDate(resultSet.getDate("from_date")),
                        localDate(resultSet.getDate("to_date")),
                        resultSet.getBoolean("dry_run")), jobId.toString()).stream().findFirst();
    }

    public Optional<EmbeddingJob> findById(UUID jobId) {
        return jdbcTemplate.query("select * from embedding_jobs where job_id = ?", this::map, jobId.toString())
                .stream().findFirst();
    }

    public Optional<EmbeddingJob> findLatest() {
        return jdbcTemplate.query("select * from embedding_jobs order by started_at desc limit 1", this::map)
                .stream().findFirst();
    }

    public List<EmbeddingJob> findRecent(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        return jdbcTemplate.query("select * from embedding_jobs order by started_at desc limit ?", this::map, limit);
    }

    private EmbeddingJob map(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
        return new EmbeddingJob(
                UUID.fromString(resultSet.getString("job_id")), resultSet.getString("status"),
                resultSet.getString("embedding_model"), resultSet.getInt("embedding_dimension"),
                resultSet.getString("document_schema_version"), resultSet.getLong("total_papers"),
                resultSet.getLong("papers_scanned"), resultSet.getLong("documents_prepared"),
                resultSet.getLong("embedded"), resultSet.getLong("skipped"), resultSet.getLong("failures"),
                resultSet.getLong("provider_requests"), resultSet.getObject("last_paper_id", Long.class),
                instant(resultSet.getTimestamp("started_at")), instant(resultSet.getTimestamp("updated_at")),
                instant(resultSet.getTimestamp("completed_at")), resultSet.getString("last_error"),
                resultSet.getBoolean("cancel_requested"), resultSet.getObject("from_paper_id", Long.class),
                resultSet.getObject("to_paper_id", Long.class), resultSet.getString("category"),
                localDate(resultSet.getDate("from_date")), localDate(resultSet.getDate("to_date")),
                resultSet.getObject("max_papers", Integer.class), resultSet.getBoolean("dry_run"));
    }

    private static Date date(java.time.LocalDate value) {
        return value == null ? null : Date.valueOf(value);
    }

    private static java.time.LocalDate localDate(Date value) {
        return value == null ? null : value.toLocalDate();
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static String safeError(Throwable exception) {
        var message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000) + "...";
    }
}
