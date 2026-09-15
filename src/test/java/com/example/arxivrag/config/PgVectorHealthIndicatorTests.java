package com.example.arxivrag.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

class PgVectorHealthIndicatorTests {

    @Test
    void inMemoryProfileIsReadyWithoutRunningPostgresql() {
        var environment = mock(Environment.class);
        org.mockito.Mockito.when(environment.getProperty("vector.store.type", "in-memory"))
                .thenReturn("in-memory");

        var health = new PgVectorHealthIndicator(mock(JdbcTemplate.class), environment).health();

        assertThat(health.getStatus().getCode()).isEqualTo("UP");
        assertThat(health.getDetails()).containsEntry("status", "not-applicable");
    }
}
