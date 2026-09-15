package com.example.arxivrag.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.arxivrag.search.SearchMode;
import com.example.arxivrag.search.SearchRetrievalProperties;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RetrievalPolicyFactoryTests {

    @Test
    void surveyGetsHybridRetrievalAndLargerBudgets() {
        var factory = factory();

        var policy = factory.create(new ChatRequest("Give me a literature survey of RAG evaluation"));

        assertThat(policy.intent()).isEqualTo(QuestionIntentClassifier.QuestionIntent.LITERATURE_SURVEY);
        assertThat(policy.mode()).isEqualTo(SearchMode.HYBRID);
        assertThat(policy.topK()).isEqualTo(8);
        assertThat(policy.maxContextCharacters()).isEqualTo(4000);
        assertThat(policy.maxCompletionTokens()).isEqualTo(400);
        assertThat(policy.version()).isEqualTo("retrieval-v1");
    }

    @Test
    void filtersWithoutExplicitModeChooseRelationalFastPath() {
        var policy = factory().create(new ChatRequest("Recent papers", null, "cs.AI",
                LocalDate.of(2024, 1, 1), null, false));

        assertThat(policy.relationalOnly()).isTrue();
        assertThat(policy.filters().category()).isEqualTo("cs.AI");
    }

    @Test
    void explicitModeAndConfiguredWeightsArePreserved() {
        var search = new SearchRetrievalProperties();
        search.setHybridVectorWeight(0.25d);
        search.setHybridLexicalWeight(0.75d);
        var chat = new ChatProperties();
        var factory = new RetrievalPolicyFactory(chat, search, new QuestionIntentClassifier());

        var policy = factory.create(new ChatRequest("Compare retrieval methods", "hybrid",
                null, null, null, false));

        assertThat(policy.vectorWeight()).isEqualTo(0.25d);
        assertThat(policy.lexicalWeight()).isEqualTo(0.75d);
        assertThat(policy.mode()).isEqualTo(SearchMode.HYBRID);
        assertThat(policy.topK()).isEqualTo(6);
    }

    private static RetrievalPolicyFactory factory() {
        return new RetrievalPolicyFactory(new ChatProperties(), new SearchRetrievalProperties(),
                new QuestionIntentClassifier());
    }
}
