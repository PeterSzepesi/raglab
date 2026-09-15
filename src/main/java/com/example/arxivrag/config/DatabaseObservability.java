package com.example.arxivrag.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Read-only PostgreSQL operational statistics; never exposes SQL text or parameters. */
@Component
public class DatabaseObservability {

    private final JdbcTemplate jdbcTemplate;
    private final AtomicLong databaseBytes = new AtomicLong(-1L);
    private final AtomicLong deadTuples = new AtomicLong(-1L);
    private final AtomicLong slowStatements = new AtomicLong(-1L);

    public DatabaseObservability(JdbcTemplate jdbcTemplate, MeterRegistry registry) {
        this.jdbcTemplate = jdbcTemplate;
        Gauge.builder("rag.database.size.bytes", databaseBytes, AtomicLong::get).register(registry);
        Gauge.builder("rag.database.dead.tuples", deadTuples, AtomicLong::get).register(registry);
        Gauge.builder("rag.database.slow.statements", slowStatements, AtomicLong::get).register(registry);
    }

    public Snapshot snapshot(String product) {
        if (product == null || !product.toLowerCase(java.util.Locale.ROOT).contains("postgresql")) {
            return Snapshot.notApplicable();
        }
        try {
            var bytes = number("select pg_database_size(current_database())");
            var dead = number("select coalesce(sum(n_dead_tup), 0) from pg_stat_user_tables");
            var extension = Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                    "select exists(select 1 from pg_extension where extname = 'pg_stat_statements')", Boolean.class));
            var statementStats = extension ? statementStats() : StatementStats.unavailable();
            databaseBytes.set(bytes);
            deadTuples.set(dead);
            slowStatements.set(statementStats.slowStatementCount());
            return new Snapshot(true, extension, bytes, dead, statementStats.p95MeanExecutionMilliseconds(),
                    statementStats.slowStatementCount(), statementStats.topStatements());
        } catch (RuntimeException failure) {
            return new Snapshot(false, false, value(databaseBytes), value(deadTuples), null,
                    value(slowStatements), List.of());
        }
    }

    private StatementStats statementStats() {
        var aggregate = jdbcTemplate.queryForMap("""
                select percentile_cont(0.95) within group (order by mean_exec_time) as p95,
                       count(*) filter (where mean_exec_time >= 100) as slow_count
                from pg_stat_statements
                where dbid = (select oid from pg_database where datname = current_database())
                """);
        var top = jdbcTemplate.query("""
                select queryid::text, calls, total_exec_time, mean_exec_time, rows
                from pg_stat_statements
                where dbid = (select oid from pg_database where datname = current_database())
                order by total_exec_time desc
                limit 5
                """, (row, index) -> new StatementSummary(row.getString(1), row.getLong(2),
                row.getDouble(3), row.getDouble(4), row.getLong(5)));
        var p95Value = aggregate.get("p95");
        var slowValue = aggregate.get("slow_count");
        return new StatementStats(p95Value instanceof Number number ? number.doubleValue() : null,
                slowValue instanceof Number number ? number.longValue() : 0L, List.copyOf(top));
    }

    private long number(String sql) {
        var value = jdbcTemplate.queryForObject(sql, Number.class);
        return value == null ? 0L : value.longValue();
    }

    private static Long value(AtomicLong value) {
        return value.get() < 0 ? null : value.get();
    }

    public record Snapshot(boolean available, boolean pgStatStatementsInstalled, Long databaseBytes,
                           Long estimatedDeadTuples, Double normalizedStatementP95Milliseconds,
                           Long slowStatementCount, List<StatementSummary> topStatements) {
        static Snapshot notApplicable() {
            return new Snapshot(false, false, null, null, null, null, List.of());
        }
    }

    public record StatementSummary(String queryId, long calls, double totalExecutionMilliseconds,
                                   double meanExecutionMilliseconds, long rows) { }

    private record StatementStats(Double p95MeanExecutionMilliseconds, long slowStatementCount,
                                  List<StatementSummary> topStatements) {
        static StatementStats unavailable() {
            return new StatementStats(null, 0L, List.of());
        }
    }
}
