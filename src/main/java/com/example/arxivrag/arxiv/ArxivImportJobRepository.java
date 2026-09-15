package com.example.arxivrag.arxiv;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ArxivImportJobRepository {
    private static final Duration LEASE_DURATION = Duration.ofMinutes(2);
    private final JdbcTemplate jdbc;

    public ArxivImportJobRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public UUID create(String sourcePath, ArxivImportOptions options) {
        var id = UUID.randomUUID();
        jdbc.update("""
                insert into import_jobs(job_id, status, source_path, categories, from_date, max_records)
                values (?, 'QUEUED', ?, ?, ?, ?)
                """, id.toString(), sourcePath, String.join(",", options.categories()),
                date(options.fromDate()), options.maxRecords());
        return id;
    }

    public boolean claim(UUID id, String ownerId) {
        return jdbc.update("""
                update import_jobs set status='RUNNING', cancel_requested=false, owner_id=?, lease_expires_at=?,
                    started_at=current_timestamp, updated_at=current_timestamp, completed_at=null, last_error=null
                where job_id=? and status in ('QUEUED','FAILED','CANCELLED','INTERRUPTED')
                """, ownerId, Timestamp.from(Instant.now().plus(LEASE_DURATION)), id.toString()) == 1;
    }

    public void progress(UUID id, String ownerId, ArxivImportService.ImportProgress value) {
        jdbc.update("""
                update import_jobs set records_read=?, accepted=?, imported=?, updated=?, skipped=?,
                    invalid_records=?, updated_at=current_timestamp, lease_expires_at=?
                where job_id=? and status='RUNNING' and owner_id=?
                """, value.recordsRead(), value.accepted(), value.imported(), value.updated(), value.skipped(),
                value.invalidRecords(), Timestamp.from(Instant.now().plus(LEASE_DURATION)), id.toString(), ownerId);
    }

    public void complete(UUID id, String ownerId, ArxivImportResult result, String checksum, boolean cancelled,
                         List<Long> malformedLineSamples) {
        jdbc.update("""
                update import_jobs set status=?, source_sha256=?, records_read=?, accepted=?, imported=?, updated=?,
                    skipped=?, invalid_records=?, updated_at=current_timestamp, completed_at=current_timestamp,
                    owner_id=null, lease_expires_at=null, malformed_line_samples=?
                where job_id=? and owner_id=?
                """, cancelled ? "CANCELLED" : "COMPLETED", checksum, result.recordsRead(), result.accepted(),
                result.imported(), result.updated(), result.skipped(), result.invalidRecords(),
                malformedLineSamples == null ? null : malformedLineSamples.stream().map(String::valueOf)
                        .collect(java.util.stream.Collectors.joining(",")), id.toString(), ownerId);
    }

    public void fail(UUID id, String ownerId, Throwable failure) {
        var message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        jdbc.update("""
                update import_jobs set status='FAILED', last_error=?, updated_at=current_timestamp,
                    completed_at=current_timestamp, owner_id=null, lease_expires_at=null
                where job_id=? and owner_id=?
                """, message.substring(0, Math.min(1000, message.length())), id.toString(), ownerId);
    }

    public boolean requestCancellation(UUID id) {
        return jdbc.update("""
                update import_jobs set cancel_requested=true, updated_at=current_timestamp
                where job_id=? and status in ('QUEUED','RUNNING')
                """, id.toString()) == 1;
    }

    public boolean cancelQueued(UUID id) {
        return jdbc.update("""
                update import_jobs set status='CANCELLED', completed_at=current_timestamp, updated_at=current_timestamp
                where job_id=? and status='QUEUED'
                """, id.toString()) == 1;
    }

    public boolean cancellationRequested(UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select cancel_requested from import_jobs where job_id=?", Boolean.class, id.toString()));
    }

    public Optional<ArxivImportJob> find(UUID id) {
        return jdbc.query("select * from import_jobs where job_id=?", this::map, id.toString()).stream().findFirst();
    }

    public List<ArxivImportJob> recent(int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
        return jdbc.query("select * from import_jobs order by started_at desc limit ?", this::map, limit);
    }

    public int recoverExpired() {
        return jdbc.update("""
                update import_jobs set status='INTERRUPTED', last_error='Worker lease expired before completion',
                    updated_at=current_timestamp, owner_id=null, lease_expires_at=null
                where status='RUNNING' and (lease_expires_at is null or lease_expires_at < current_timestamp)
                """);
    }

    private ArxivImportJob map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        var categoryText = rs.getString("categories");
        return new ArxivImportJob(UUID.fromString(rs.getString("job_id")), rs.getString("status"),
                rs.getString("source_path"), rs.getString("source_sha256"),
                categoryText == null || categoryText.isBlank() ? List.of() : Arrays.asList(categoryText.split(",")),
                localDate(rs.getDate("from_date")), rs.getObject("max_records", Integer.class),
                rs.getLong("records_read"), rs.getLong("accepted"), rs.getLong("imported"), rs.getLong("updated"),
                rs.getLong("skipped"), rs.getLong("invalid_records"), rs.getBoolean("cancel_requested"),
                instant(rs.getTimestamp("started_at")), instant(rs.getTimestamp("updated_at")),
                instant(rs.getTimestamp("completed_at")), rs.getString("last_error"),
                lineSamples(rs.getString("malformed_line_samples")));
    }

    private static Date date(java.time.LocalDate value) { return value == null ? null : Date.valueOf(value); }
    private static java.time.LocalDate localDate(Date value) { return value == null ? null : value.toLocalDate(); }
    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
    private static List<Long> lineSamples(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(",")).map(Long::valueOf).toList();
    }
}
