package com.example.arxivrag.embedding;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class EmbeddingJobRepository {

    private final JdbcTemplate jdbcTemplate;

    public EmbeddingJobRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UUID create(long totalPapers, String model, int dimension, String documentSchemaVersion) {
        var jobId = UUID.randomUUID();
        jdbcTemplate.update("""
                        insert into embedding_jobs (
                            job_id, status, embedding_model, embedding_dimension, document_schema_version,
                            total_papers, started_at, updated_at
                        ) values (?, 'RUNNING', ?, ?, ?, ?, current_timestamp, current_timestamp)
                        """,
                jobId.toString(), model, dimension, documentSchemaVersion, totalPapers);
        return jobId;
    }

    public void updateProgress(UUID jobId, EmbeddingIngestionProgress.Snapshot progress, Long lastPaperId) {
        jdbcTemplate.update("""
                        update embedding_jobs set
                            papers_scanned = ?, documents_prepared = ?, embedded = ?, skipped = ?,
                            failures = ?, provider_requests = ?, last_paper_id = ?, updated_at = current_timestamp
                        where job_id = ?
                        """,
                progress.papersScanned(), progress.documentsPrepared(), progress.embedded(), progress.skipped(),
                progress.failures(), progress.providerRequests(), lastPaperId, jobId.toString());
    }

    public void complete(UUID jobId, EmbeddingIngestionProgress.Snapshot progress) {
        jdbcTemplate.update("""
                        update embedding_jobs set status = 'COMPLETED', papers_scanned = ?, documents_prepared = ?,
                            embedded = ?, skipped = ?, failures = ?, provider_requests = ?,
                            updated_at = current_timestamp, completed_at = current_timestamp
                        where job_id = ?
                        """,
                progress.papersScanned(), progress.documentsPrepared(), progress.embedded(), progress.skipped(),
                progress.failures(), progress.providerRequests(), jobId.toString());
    }

    public void fail(UUID jobId, EmbeddingIngestionProgress.Snapshot progress, Throwable exception) {
        jdbcTemplate.update("""
                        update embedding_jobs set status = 'FAILED', papers_scanned = ?, documents_prepared = ?,
                            embedded = ?, skipped = ?, failures = ?, provider_requests = ?, last_error = ?,
                            updated_at = current_timestamp, completed_at = current_timestamp
                        where job_id = ?
                        """,
                progress.papersScanned(), progress.documentsPrepared(), progress.embedded(), progress.skipped(),
                progress.failures(), progress.providerRequests(), safeError(exception), jobId.toString());
    }

    public Optional<EmbeddingJob> findById(UUID jobId) {
        return jdbcTemplate.query("select * from embedding_jobs where job_id = ?", this::map, jobId.toString())
                .stream().findFirst();
    }

    public Optional<EmbeddingJob> findLatest() {
        return jdbcTemplate.query("select * from embedding_jobs order by started_at desc limit 1", this::map)
                .stream().findFirst();
    }

    private EmbeddingJob map(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
        return new EmbeddingJob(
                UUID.fromString(resultSet.getString("job_id")),
                resultSet.getString("status"),
                resultSet.getString("embedding_model"),
                resultSet.getInt("embedding_dimension"),
                resultSet.getString("document_schema_version"),
                resultSet.getLong("total_papers"),
                resultSet.getLong("papers_scanned"),
                resultSet.getLong("documents_prepared"),
                resultSet.getLong("embedded"),
                resultSet.getLong("skipped"),
                resultSet.getLong("failures"),
                resultSet.getLong("provider_requests"),
                resultSet.getObject("last_paper_id", Long.class),
                instant(resultSet.getTimestamp("started_at")),
                instant(resultSet.getTimestamp("updated_at")),
                instant(resultSet.getTimestamp("completed_at")),
                resultSet.getString("last_error")
        );
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
