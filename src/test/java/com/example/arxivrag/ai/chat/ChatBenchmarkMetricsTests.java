package com.example.arxivrag.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChatBenchmarkMetricsTests {

    @Test
    void ndcgRewardsRelevantPapersNearTheTop() {
        var ideal = ChatBenchmarkCommandLineRunner.normalizedDiscountedCumulativeGain(
                List.of("a", "b", "noise"), List.of("a", "b"));
        var delayed = ChatBenchmarkCommandLineRunner.normalizedDiscountedCumulativeGain(
                List.of("noise", "a", "b"), List.of("a", "b"));

        assertThat(ideal).isEqualTo(1d);
        assertThat(delayed).isBetween(0d, ideal);
    }

    @Test
    void noEvidenceRankingIsPerfectOnlyWhenItReturnsNoPapers() {
        assertThat(ChatBenchmarkCommandLineRunner.normalizedDiscountedCumulativeGain(List.of(), List.of()))
                .isEqualTo(1d);
        assertThat(ChatBenchmarkCommandLineRunner.normalizedDiscountedCumulativeGain(
                List.of("invented"), List.of())).isZero();
    }

    @Test
    void duplicateRelevantResultsCannotInflateNdcgAboveOne() {
        assertThat(ChatBenchmarkCommandLineRunner.normalizedDiscountedCumulativeGain(
                List.of("a", "a", "a", "b"), List.of("a", "b")))
                .isBetween(0d, 1d);
    }
}
