package com.example.arxivrag.paper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class PaperRepositoryTests {

    @Autowired
    private PaperRepository paperRepository;

    @BeforeEach
    void deleteExistingPapers() {
        paperRepository.deleteAll();
    }

    @Test
    void savesAndFindsPaperByArxivId() {
        var paper = paperRepository.save(samplePaper("2401.00001", "Retrieval testing"));

        var found = paperRepository.findByArxivId("2401.00001");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(paper.getId());
        assertThat(found.get().getTitle()).isEqualTo("Retrieval testing");
        assertThat(found.get().getAuthors()).containsExactly("Ada Lovelace", "Alan Turing");
        assertThat(found.get().getCategories()).containsExactly("cs.CL", "cs.AI");
        assertThat(found.get().getCreatedAt()).isNotNull();
        assertThat(found.get().getUpdatedAt()).isNotNull();
    }

    @Test
    void findsPapersSubmittedOnOrAfterDate() {
        paperRepository.save(samplePaper("2401.00001", "Included"));
        paperRepository.save(new Paper(
                "2101.00001",
                "Excluded",
                "Older abstract",
                List.of("Grace Hopper"),
                List.of("cs.LG"),
                LocalDate.of(2021, 1, 1),
                LocalDate.of(2021, 1, 2),
                null,
                null
        ));

        var found = paperRepository.findBySubmittedDateGreaterThanEqual(LocalDate.of(2022, 1, 1));

        assertThat(found)
                .extracting(Paper::getArxivId)
                .containsExactly("2401.00001");
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
