package com.example.arxivrag.arxiv;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PaperMapperTests {

    private final PaperMapper paperMapper = new PaperMapper();

    @Test
    void mapsAndNormalizesArxivRecord() {
        var paper = paperMapper.toPaper(new ArxivRecord(
                "2401.00001",
                " Retrieval\n Testing ",
                "Ada Lovelace, Alan Turing",
                "cs.CL cs.AI",
                " ",
                "",
                " Abstract\n text\twith spaces. ",
                LocalDate.of(2024, 1, 3),
                List.of(new ArxivRecord.Version("v1", "Tue, 2 Jan 2024 00:00:00 GMT"))
        ));

        assertThat(paper.getArxivId()).isEqualTo("2401.00001");
        assertThat(paper.getTitle()).isEqualTo("Retrieval Testing");
        assertThat(paper.getAbstractText()).isEqualTo("Abstract text with spaces.");
        assertThat(paper.getAuthors()).containsExactly("Ada Lovelace", "Alan Turing");
        assertThat(paper.getCategories()).containsExactly("cs.CL", "cs.AI");
        assertThat(paper.getSubmittedDate()).isEqualTo(LocalDate.of(2024, 1, 2));
        assertThat(paper.getUpdatedDate()).isEqualTo(LocalDate.of(2024, 1, 3));
        assertThat(paper.getDoi()).isNull();
        assertThat(paper.getJournalRef()).isNull();
    }
}
