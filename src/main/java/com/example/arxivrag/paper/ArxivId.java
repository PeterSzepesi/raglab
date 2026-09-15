package com.example.arxivrag.paper;

import java.util.regex.Pattern;

/** Normalized arXiv identifier and the only authority for canonical public URLs. */
public record ArxivId(String value) {
    private static final Pattern FORMAT = Pattern.compile(
            "(?:\\d{4}\\.\\d{4,5}|[A-Za-z][A-Za-z0-9.-]*/\\d{7})(?:v\\d+)?");

    public ArxivId {
        value = value == null ? null : value.trim();
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid arXiv identifier");
        }
    }

    public String abstractUrl() { return "https://arxiv.org/abs/" + value; }
    public String pdfUrl() { return "https://arxiv.org/pdf/" + value; }

    public static String abstractUrlOrNull(String value) {
        try { return new ArxivId(value).abstractUrl(); }
        catch (IllegalArgumentException ignored) { return null; }
    }
}
