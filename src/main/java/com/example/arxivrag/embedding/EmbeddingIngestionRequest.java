package com.example.arxivrag.embedding;

import org.springframework.boot.ApplicationArguments;

import java.time.LocalDate;

/** Immutable controls for a bounded, repeatable embedding run. */
public record EmbeddingIngestionRequest(
        Integer maxPapers,
        Long fromPaperId,
        Long toPaperId,
        String category,
        LocalDate fromDate,
        LocalDate toDate,
        boolean dryRun
) {

    public EmbeddingIngestionRequest {
        if (maxPapers != null && (maxPapers <= 0 || maxPapers > 50_000)) {
            throw new IllegalArgumentException("max-papers must be between 1 and 50000");
        }
        if (fromPaperId != null && fromPaperId <= 0) {
            throw new IllegalArgumentException("from-paper-id must be greater than zero");
        }
        if (toPaperId != null && toPaperId <= 0) {
            throw new IllegalArgumentException("to-paper-id must be greater than zero");
        }
        if (fromPaperId != null && toPaperId != null && fromPaperId > toPaperId) {
            throw new IllegalArgumentException("from-paper-id must not be greater than to-paper-id");
        }
        if (category != null && category.isBlank()) {
            category = null;
        }
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("from-date must not be after to-date");
        }
    }

    public static EmbeddingIngestionRequest from(EmbeddingIngestionProperties properties) {
        return new EmbeddingIngestionRequest(
                properties.getMaxPapers(), properties.getFromPaperId(), properties.getToPaperId(),
                properties.getCategory(), properties.getFromDate(), properties.getToDate(), properties.isDryRun()
        );
    }

    public static EmbeddingIngestionRequest from(EmbeddingIngestionProperties properties, ApplicationArguments args) {
        return new EmbeddingIngestionRequest(
                integerOption(args, "max-papers", "embedding.ingestion.max-papers", properties.getMaxPapers()),
                longOption(args, "from-paper-id", "embedding.ingestion.from-paper-id", properties.getFromPaperId()),
                longOption(args, "to-paper-id", "embedding.ingestion.to-paper-id", properties.getToPaperId()),
                stringOption(args, "category", "embedding.ingestion.category", properties.getCategory()),
                dateOption(args, "from-date", "embedding.ingestion.from-date", properties.getFromDate()),
                dateOption(args, "to-date", "embedding.ingestion.to-date", properties.getToDate()),
                booleanOption(args, "dry-run", "embedding.ingestion.dry-run", properties.isDryRun())
        );
    }

    public String describeFilters() {
        return "maxPapers=" + maxPapers + ", fromPaperId=" + fromPaperId + ", toPaperId=" + toPaperId
                + ", category=" + category + ", fromDate=" + fromDate + ", toDate=" + toDate
                + ", dryRun=" + dryRun;
    }

    private static String stringOption(ApplicationArguments args, String shortName, String longName, String fallback) {
        if (args.containsOption(shortName)) {
            return args.getOptionValues(shortName).stream().findFirst().orElse("true");
        }
        if (args.containsOption(longName)) {
            return args.getOptionValues(longName).stream().findFirst().orElse("true");
        }
        return fallback;
    }

    private static boolean booleanOption(ApplicationArguments args, String shortName, String longName, boolean fallback) {
        return Boolean.parseBoolean(stringOption(args, shortName, longName, Boolean.toString(fallback)));
    }

    private static Integer integerOption(ApplicationArguments args, String shortName, String longName, Integer fallback) {
        var value = stringOption(args, shortName, longName, fallback == null ? null : fallback.toString());
        return value == null ? null : parseInteger(value, shortName);
    }

    private static Long longOption(ApplicationArguments args, String shortName, String longName, Long fallback) {
        var value = stringOption(args, shortName, longName, fallback == null ? null : fallback.toString());
        return value == null ? null : parseLong(value, shortName);
    }

    private static LocalDate dateOption(ApplicationArguments args, String shortName, String longName, LocalDate fallback) {
        var value = stringOption(args, shortName, longName, fallback == null ? null : fallback.toString());
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(shortName + " must use YYYY-MM-DD format", exception);
        }
    }

    private static int parseInteger(String value, String name) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be an integer", exception);
        }
    }

    private static long parseLong(String value, String name) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be an integer", exception);
        }
    }
}
