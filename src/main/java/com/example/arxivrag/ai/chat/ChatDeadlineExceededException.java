package com.example.arxivrag.ai.chat;

/** Raised when the complete synchronous chat use case outlives its configured deadline. */
public final class ChatDeadlineExceededException extends RuntimeException {
    public ChatDeadlineExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}
