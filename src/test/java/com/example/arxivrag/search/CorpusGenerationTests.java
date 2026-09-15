package com.example.arxivrag.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CorpusGenerationTests {

    @Test
    void inMemoryUnitTestCounterIsMonotonic() {
        var generation = new CorpusGeneration();

        assertThat(generation.current()).isZero();
        assertThat(generation.increment()).isEqualTo(1);
        assertThat(generation.increment()).isEqualTo(2);
    }
}
