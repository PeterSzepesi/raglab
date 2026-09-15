package com.example.arxivrag.ai.foundry;

import java.net.http.HttpRequest;

@FunctionalInterface
interface FoundryAuthentication {
    void apply(HttpRequest.Builder request);
}
