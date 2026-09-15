package com.example.arxivrag.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChatPromptFactoryTests {

    @Test
    void placesStableInstructionsBeforeDynamicEvidenceAndAppliesIntentBudget() {
        var properties = new ChatProperties();
        properties.setProviderPromptCacheEnabled(true);
        properties.setPromptVersion("prompt-v7");
        var prompts = new ChatPromptFactory(properties, new QuestionIntentClassifier());

        var messages = prompts.answerMessages("Compare these papers", "[{\"arxivId\":\"2401.1\"}]");
        var options = prompts.options("Compare these papers");

        assertThat(messages.getFirst().get("role")).isEqualTo("system");
        assertThat(messages.getLast().get("content").toString()).contains("Compare these papers", "EVIDENCE");
        assertThat(options.maxCompletionTokens()).isEqualTo(400);
        assertThat(options.promptCacheKey()).isEqualTo("prompt-v7");
    }
}
