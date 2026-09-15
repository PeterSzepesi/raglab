package com.example.arxivrag.paper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.arxivrag.PostgresIntegrationTest;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class PaperSchemaIT extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PaperRepository paperRepository;

    @BeforeEach
    void deleteExistingPapers() {
        paperRepository.deleteAll();
    }

    @Test
    void flywayCreatesPapersTableAndIndexes() {
        assertThat(tableExists("papers")).isTrue();
        assertThat(indexExists("uk_papers_arxiv_id")).isTrue();
        assertThat(indexExists("idx_papers_submitted_date")).isTrue();
        assertThat(indexExists("idx_papers_categories")).isTrue();
    }

    @Test
    void uniqueArxivIdIsEnforced() {
        paperRepository.saveAndFlush(samplePaper("2401.00001", "First"));

        assertThatThrownBy(() -> paperRepository.saveAndFlush(samplePaper("2401.00001", "Duplicate")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void categoryQueryUsesPostgresqlArrayMembership() {
        paperRepository.save(samplePaper("2401.00001", "Language model retrieval"));
        paperRepository.save(new Paper(
                "2401.00002",
                "Vision transformer retrieval",
                "Computer vision abstract.",
                List.of("Grace Hopper"),
                List.of("cs.CV"),
                LocalDate.of(2024, 2, 2),
                LocalDate.of(2024, 2, 3),
                null,
                null
        ));

        var found = paperRepository.findByCategory("cs.CL");

        assertThat(found)
                .extracting(Paper::getArxivId)
                .containsExactly("2401.00001");
    }

    private boolean tableExists(String tableName) {
        var count = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.tables
                where table_schema = 'public'
                  and table_name = ?
                """, Integer.class, tableName);
        return count != null && count == 1;
    }

    private boolean indexExists(String indexName) {
        var count = jdbcTemplate.queryForObject("""
                select count(*)
                from pg_indexes
                where schemaname = 'public'
                  and indexname = ?
                """, Integer.class, indexName);
        return count != null && count == 1;
    }

    private static Paper samplePaper(String arxivId, String title) {
        return new Paper(
                arxivId,
                title,
                "A concise abstract for repository testing.",
                List.of("Ada Lovelace", "Alan Turing"),
                List.of("cs.CL", "cs.AI"),
                LocalDate.of(2024, 1, 2),
                LocalDate.of(2024, 1, 3),
                "10.48550/arXiv." + arxivId,
                "Journal Ref"
        );
    }
}
