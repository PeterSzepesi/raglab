package com.example.arxivrag.ai.chat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GroundedAnswerSanitizerTests {

    @Test
    void removesInlineCitationsThatWereNotRetrieved() {
        var answer = new GroundedAnswerSanitizer().sanitize(
                "Supported [arXiv:2401.00001], invented [arXiv:9999.99999].",
                List.of(new ChatCitation(1L, "2401.00001", "Supported", 0.9)));

        assertThat(answer).contains("[arXiv:2401.00001]")
                .doesNotContain("[arXiv:9999.99999]")
                .contains("unsupported citation removed");
    }
}
