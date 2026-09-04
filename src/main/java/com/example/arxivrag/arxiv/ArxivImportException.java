package com.example.arxivrag.arxiv;

public class ArxivImportException extends RuntimeException {

    public ArxivImportException(String message) {
        super(message);
    }

    public ArxivImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
