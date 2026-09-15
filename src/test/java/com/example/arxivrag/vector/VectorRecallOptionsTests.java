package com.example.arxivrag.vector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class VectorRecallOptionsTests {

    @Test
    void parsesSafeTransactionLocalHnswSettings() {
        var options = VectorRecallOptions.from(80, "relaxed-order");

        assertThat(options.hnswEfSearch()).isEqualTo(80);
        assertThat(options.iterativeScan()).isEqualTo(VectorRecallOptions.IterativeScan.RELAXED_ORDER);
    }

    @Test
    void rejectsOutOfRangeOrUnknownSettings() {
        assertThatThrownBy(() -> VectorRecallOptions.from(0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hnswEfSearch");
        assertThatThrownBy(() -> VectorRecallOptions.from(40, "drop table"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("iterativeScan");
    }
}
