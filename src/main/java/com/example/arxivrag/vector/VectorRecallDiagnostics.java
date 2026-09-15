package com.example.arxivrag.vector;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;

public record VectorRecallDiagnostics(
        boolean supported,
        String embeddingModel,
        int topK,
        String category,
        LocalDate fromDate,
        LocalDate toDate,
        Integer hnswEfSearch,
        String iterativeScan,
        List<String> approximateArxivIds,
        List<String> exactArxivIds,
        int overlap,
        double recallAtK,
        long approximateMilliseconds,
        long exactMilliseconds,
        boolean meetsRecallTarget,
        String note
) {

    public static VectorRecallDiagnostics compare(
            VectorSearchRequest request,
            VectorRecallOptions options,
            List<VectorSearchResult> approximate,
            List<VectorSearchResult> exact,
            long approximateMilliseconds,
            long exactMilliseconds
    ) {
        var approximateIds = uniqueArxivIds(approximate);
        var exactIds = uniqueArxivIds(exact);
        var overlap = approximateIds.stream().filter(exactIds::contains).count();
        var recall = exactIds.isEmpty() ? (approximateIds.isEmpty() ? 1d : 0d)
                : overlap / (double) exactIds.size();
        return new VectorRecallDiagnostics(true, request.embeddingModel(), request.topK(), request.category(),
                request.fromDate(), request.toDate(), options.hnswEfSearch(),
                options.iterativeScan().name().toLowerCase(java.util.Locale.ROOT),
                approximateIds, exactIds, Math.toIntExact(overlap), recall,
                approximateMilliseconds, exactMilliseconds, recall >= 0.98d,
                "Run representative filtered cases repeatedly; keep tuning only when recall remains at least 0.98");
    }

    public static VectorRecallDiagnostics unsupported(
            VectorSearchRequest request,
            VectorRecallOptions options,
            String note
    ) {
        return new VectorRecallDiagnostics(false, request.embeddingModel(), request.topK(), request.category(),
                request.fromDate(), request.toDate(), options.hnswEfSearch(),
                options.iterativeScan().name().toLowerCase(java.util.Locale.ROOT),
                List.of(), List.of(), 0, 0d, 0L, 0L, false, note);
    }

    private static List<String> uniqueArxivIds(List<VectorSearchResult> values) {
        var result = new LinkedHashSet<String>();
        values.stream().map(VectorSearchResult::arxivId).filter(java.util.Objects::nonNull).forEach(result::add);
        return List.copyOf(result);
    }
}
