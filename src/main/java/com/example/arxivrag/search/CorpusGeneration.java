package com.example.arxivrag.search;

import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Monotonic corpus version used in cache keys. Production instances share the
 * PostgreSQL value; the no-argument constructor is an intentionally small
 * in-memory implementation for isolated unit tests.
 */
@Component
public class CorpusGeneration {

    private final JdbcTemplate jdbcTemplate;
    private final AtomicLong testValue;

    public CorpusGeneration() {
        this.jdbcTemplate = null;
        this.testValue = new AtomicLong();
    }

    @Autowired
    public CorpusGeneration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.testValue = null;
    }

    public long current() {
        if (jdbcTemplate == null) {
            return testValue.get();
        }
        var value = jdbcTemplate.queryForObject(
                "select generation from corpus_state where singleton_id = 1", Long.class);
        if (value == null) {
            throw new IllegalStateException("Corpus generation row is missing");
        }
        return value;
    }

    public long increment() {
        if (jdbcTemplate == null) {
            return testValue.incrementAndGet();
        }
        var rows = jdbcTemplate.update(
                "update corpus_state set generation = generation + 1, updated_at = current_timestamp "
                        + "where singleton_id = 1");
        if (rows != 1) {
            throw new IllegalStateException("Corpus generation row is missing");
        }
        return current();
    }
}
