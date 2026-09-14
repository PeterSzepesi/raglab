package com.example.arxivrag.vector;

import java.util.function.Supplier;

public interface SearchStageObserver {
    <T> T measure(SearchStage stage, Supplier<T> supplier);
}
