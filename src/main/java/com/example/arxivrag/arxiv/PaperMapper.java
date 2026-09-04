package com.example.arxivrag.arxiv;

import com.example.arxivrag.paper.Paper;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class PaperMapper {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private static final DateTimeFormatter ARXIV_VERSION_DATE_FORMATTER = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("EEE, d MMM yyyy")
            .optionalStart()
            .appendPattern(" HH:mm:ss")
            .optionalEnd()
            .optionalStart()
            .appendPattern(" z")
            .optionalEnd()
            .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
            .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
            .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
            .toFormatter(Locale.ENGLISH);

    public Paper toPaper(ArxivRecord record) {
        return new Paper(
                requireText(record.id(), "id"),
                requireText(normalizeWhitespace(record.title()), "title"),
                requireText(normalizeWhitespace(record.abstractText()), "abstract"),
                splitAuthors(record.authors()),
                splitSpaceSeparated(record.categories()),
                submittedDate(record),
                record.updateDate(),
                blankToNull(record.doi()),
                blankToNull(record.journalRef())
        );
    }

    static LocalDate submittedDate(ArxivRecord record) {
        if (record.versions() != null) {
            for (var version : record.versions()) {
                if ("v1".equals(version.version()) && version.created() != null && !version.created().isBlank()) {
                    return parseVersionDate(version.created());
                }
            }
        }
        return record.updateDate();
    }

    static List<String> splitSpaceSeparated(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.trim().split("\\s+"))
                .filter(part -> !part.isBlank())
                .toList();
    }

    private static List<String> splitAuthors(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(PaperMapper::normalizeWhitespace)
                .filter(author -> !author.isBlank())
                .toList();
    }

    private static LocalDate parseVersionDate(String value) {
        try {
            return LocalDate.parse(value, ARXIV_VERSION_DATE_FORMATTER);
        } catch (DateTimeParseException exception) {
            throw new ArxivImportException("Unsupported arXiv version date: " + value, exception);
        }
    }

    private static String normalizeWhitespace(String value) {
        if (value == null) {
            return "";
        }
        return WHITESPACE.matcher(value).replaceAll(" ").trim();
    }

    private static String blankToNull(String value) {
        var normalized = normalizeWhitespace(value);
        return normalized.isBlank() ? null : normalized;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ArxivImportException("arXiv record is missing required field: " + fieldName);
        }
        return value;
    }
}
