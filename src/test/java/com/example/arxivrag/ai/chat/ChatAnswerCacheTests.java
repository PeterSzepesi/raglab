package com.example.arxivrag.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.arxivrag.search.CorpusGeneration;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatAnswerCacheTests {

    @Test
    void exactAnswerKeyChangesWhenEvidenceOrCorpusChanges() {
        var properties = new ChatProperties();
        properties.setAnswerCacheEnabled(true);
        var corpus = new CorpusGeneration();
        var cache = new ChatAnswerCache(properties, corpus, new SimpleMeterRegistry());
        var intent = new QuestionIntentClassifier().classify("What is RAG?", 4, 400);
        var request = new ChatRequest("  What is RAG?  ");
        var firstContext = new GroundedContext("[{\"id\":1}]", List.of(), List.of("hybrid_search"));
        var secondContext = new GroundedContext("[{\"id\":2}]", List.of(), List.of("hybrid_search"));

        var first = cache.key(request, firstContext, "model", intent);
        var changedEvidence = cache.key(request, secondContext, "model", intent);
        corpus.increment();
        var changedCorpus = cache.key(request, firstContext, "model", intent);

        assertThat(first).isNotEqualTo(changedEvidence).isNotEqualTo(changedCorpus);
    }

    @Test
    void disabledCacheNeverReturnsStoredAnswers() {
        var properties = new ChatProperties();
        properties.setAnswerCacheEnabled(false);
        var cache = new ChatAnswerCache(properties, new CorpusGeneration(), new SimpleMeterRegistry());
        var context = new GroundedContext("[]", List.of(), List.of());
        var intent = new QuestionIntentClassifier().classify("What is RAG?", 4, 400);
        var key = cache.key(new ChatRequest("What is RAG?"), context, "model", intent);

        cache.put(key, new GroundedChatResponse("answer", "model", List.of(), List.of()));

        assertThat(cache.get(key)).isEmpty();
    }
}
