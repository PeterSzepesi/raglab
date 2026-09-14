package com.example.arxivrag.ai.foundry;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class FoundrySseParserTests {

    @Test
    void assemblesSplitFramesAndReadsFinalUsage() {
        var deltas = new ArrayList<String>();
        var parser = new FoundrySseParser(new ObjectMapper(), deltas::add);

        parser.acceptLine("data: {\"choices\":[{\"delta\":{\"content\":\"Hel\"}}]}");
        parser.acceptLine("");
        parser.acceptLine("data: {\"choices\":[{\"delta\":{\"content\":\"lo\"}}],");
        parser.acceptLine("data: \"usage\":{\"prompt_tokens\":12,\"completion_tokens\":2,\"total_tokens\":14,"
                + "\"prompt_tokens_details\":{\"cached_tokens\":3},"
                + "\"completion_tokens_details\":{\"reasoning_tokens\":1}}}");
        parser.acceptLine("");
        parser.acceptLine("data: [DONE]");
        parser.finish();

        assertThat(deltas).containsExactly("Hel", "lo");
        assertThat(parser.content()).isEqualTo("Hello");
        assertThat(parser.promptTokens()).isEqualTo(12);
        assertThat(parser.completionTokens()).isEqualTo(2);
        assertThat(parser.reasoningTokens()).isEqualTo(1);
        assertThat(parser.cachedPromptTokens()).isEqualTo(3);
    }
}
