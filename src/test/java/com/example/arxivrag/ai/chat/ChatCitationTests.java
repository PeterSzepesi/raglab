package com.example.arxivrag.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ChatCitationTests {

    @Test
    void buildsCanonicalArxivLinksForModernAndLegacyIdentifiers() {
        assertThat(new ChatCitation(1L, "2401.00001", "Modern", 0.9d).url())
                .isEqualTo("https://arxiv.org/abs/2401.00001");
        assertThat(new ChatCitation(2L, "hep-th/9901001v2", "Legacy", null).url())
                .isEqualTo("https://arxiv.org/abs/hep-th/9901001v2");
    }

    @Test
    void leavesInvalidIdentifiersNonClickable() {
        assertThat(new ChatCitation(1L, "https://example.com/paper", "Invalid", null).url()).isNull();
    }

    @Test
    void serializesTheCanonicalUrlInTheChatApiContract() {
        var json = new ObjectMapper().writeValueAsString(
                new ChatCitation(1L, "2401.00001", "Paper", 0.9d)
        );

        assertThat(json).contains("\"url\":\"https://arxiv.org/abs/2401.00001\"");
    }
}
