package com.example.arxivrag.ai.chat;

import java.util.List;

public interface ChatStreamListener {
    default void retrievalStarted() { }
    default void citations(List<ChatCitation> citations) { }
    void delta(String text);
    default void complete(GroundedChatResponse response, ChatTiming timing) { }
    default void error(Throwable failure) { }
}
