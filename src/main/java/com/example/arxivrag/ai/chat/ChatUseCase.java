package com.example.arxivrag.ai.chat;

/** Provider-neutral application boundary shared by synchronous and streaming web adapters. */
public interface ChatUseCase {

    ChatExecution chatDetailed(ChatRequest request);

    ChatExecution stream(ChatRequest request, ChatStreamListener listener);
}
