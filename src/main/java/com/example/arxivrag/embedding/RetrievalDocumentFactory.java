package com.example.arxivrag.embedding;

import com.example.arxivrag.paper.Paper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class RetrievalDocumentFactory {

    public static final String DOCUMENT_SCHEMA_VERSION = "title-abstract-v1";

    public static final String CHUNK_ID = "title-abstract";

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    public RetrievalDocument fromPaper(Paper paper) {
        return create(
                paper.getId(),
                paper.getArxivId(),
                paper.getTitle(),
                paper.getAbstractText(),
                paper.getCategories(),
                paper.getSubmittedDate()
        );
    }

    public RetrievalDocument preview(String arxivId, String title, String abstractText) {
        return create(null, arxivId, title, abstractText, List.of(), null);
    }

    public RetrievalDocument forQuery(String query) {
        var text = normalizeRequired(query, "query");
        return new RetrievalDocument(
                null,
                "__query__",
                "query",
                text,
                sha256Hex(text),
                List.of(),
                null
        );
    }

    private RetrievalDocument create(
            Long paperId,
            String arxivId,
            String title,
            String abstractText,
            List<String> categories,
            java.time.LocalDate submittedDate
    ) {
        var text = formatText(title, abstractText);
        return new RetrievalDocument(
                paperId,
                normalizeRequired(arxivId, "arxivId"),
                CHUNK_ID,
                text,
                sha256Hex(text),
                List.copyOf(categories),
                submittedDate
        );
    }

    private static String formatText(String title, String abstractText) {
        return """
                Title: %s

                Abstract:
                %s""".formatted(
                normalizeRequired(title, "title"),
                normalizeRequired(abstractText, "abstractText")
        );
    }

    private static String normalizeRequired(String value, String fieldName) {
        var normalized = normalizeWhitespace(value);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    private static String normalizeWhitespace(String value) {
        if (value == null) {
            return "";
        }
        return WHITESPACE.matcher(value).replaceAll(" ").trim();
    }

    private static String sha256Hex(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is not available", exception);
        }
    }
}
