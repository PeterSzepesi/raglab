package com.example.arxivrag.ai;

/**
 * Application-owned boundary for text generation.
 *
 * The chat orchestration layer depends on this interface rather than on a
 * provider SDK. Step 6B uses it for a direct AI Foundry model smoke test;
 * grounded tool-calling is implemented in a later step.
 */
public interface GenerationGateway {

    String modelName();

    String generate(String systemPrompt, String userMessage);
}
