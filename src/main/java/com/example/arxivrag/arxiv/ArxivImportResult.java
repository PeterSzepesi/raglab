package com.example.arxivrag.arxiv;

import java.time.Duration;

public record ArxivImportResult(
        long recordsRead,
        long accepted,
        long imported,
        long updated,
        long skipped,
        long invalidRecords,
        Duration elapsed
) {

    public String formatSummary() {
        return """
                arXiv import complete

                Records read:        %d
                Accepted:            %d
                Imported:            %d
                Updated:             %d
                Skipped:             %d
                Invalid records:     %d
                Elapsed:             %.3f s
                """.formatted(
                recordsRead,
                accepted,
                imported,
                updated,
                skipped,
                invalidRecords,
                elapsed.toMillis() / 1000.0
        );
    }
}
