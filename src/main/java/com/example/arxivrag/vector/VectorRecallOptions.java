package com.example.arxivrag.vector;

import java.util.Locale;

public record VectorRecallOptions(Integer hnswEfSearch, IterativeScan iterativeScan) {

    public VectorRecallOptions {
        if (hnswEfSearch != null && (hnswEfSearch < 1 || hnswEfSearch > 1000)) {
            throw new IllegalArgumentException("hnswEfSearch must be between 1 and 1000");
        }
        iterativeScan = iterativeScan == null ? IterativeScan.DEFAULT : iterativeScan;
    }

    public static VectorRecallOptions defaults() {
        return new VectorRecallOptions(null, IterativeScan.DEFAULT);
    }

    public static VectorRecallOptions from(Integer hnswEfSearch, String iterativeScan) {
        if (iterativeScan == null || iterativeScan.isBlank()) {
            return new VectorRecallOptions(hnswEfSearch, IterativeScan.DEFAULT);
        }
        try {
            return new VectorRecallOptions(hnswEfSearch,
                    IterativeScan.valueOf(iterativeScan.trim().toUpperCase(Locale.ROOT).replace('-', '_')));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "iterativeScan must be default, off, strict_order, or relaxed_order", exception);
        }
    }

    public enum IterativeScan {
        DEFAULT,
        OFF,
        STRICT_ORDER,
        RELAXED_ORDER
    }
}
