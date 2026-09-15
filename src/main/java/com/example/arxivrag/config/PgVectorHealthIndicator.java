package com.example.arxivrag.config;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Readiness check only; it is deliberately not part of process liveness. */
@Component("pgVector")
public class PgVectorHealthIndicator implements HealthIndicator {

    private final JdbcTemplate jdbcTemplate;
    private final Environment environment;

    public PgVectorHealthIndicator(JdbcTemplate jdbcTemplate, Environment environment) {
        this.jdbcTemplate = jdbcTemplate;
        this.environment = environment;
    }

    @Override
    public Health health() {
        if (!"pgvector".equalsIgnoreCase(environment.getProperty("vector.store.type", "in-memory"))) {
            return Health.up().withDetail("status", "not-applicable").build();
        }
        try {
            var installed = Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                    "select exists(select 1 from pg_extension where extname = 'vector')", Boolean.class));
            return installed ? Health.up().withDetail("extension", "vector").build()
                    : Health.down().withDetail("extension", "vector is not installed").build();
        } catch (RuntimeException failure) {
            return Health.down().withDetail("extension", "vector status unavailable").build();
        }
    }
}
