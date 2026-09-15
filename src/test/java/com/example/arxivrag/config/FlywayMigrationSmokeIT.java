package com.example.arxivrag.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.arxivrag.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class FlywayMigrationSmokeIT extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayRunsAgainstPostgres() {
        var migrationCount = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success = true",
                Integer.class
        );

        assertThat(migrationCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    void hibernateDoesNotCreateApplicationTables() {
        var applicationTableCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.tables
                where table_schema = 'public'
                  and table_name <> 'flyway_schema_history'
                """, Integer.class);

        assertThat(applicationTableCount).isZero();
    }
}
