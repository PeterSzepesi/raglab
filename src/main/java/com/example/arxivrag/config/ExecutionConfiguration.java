package com.example.arxivrag.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ExecutionConfiguration {

    @Bean(name = "chatStreamExecutor", destroyMethod = "shutdownNow")
    ExecutorService chatStreamExecutor() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("chat-stream-", 0).factory());
    }

    @Bean(name = "chatToolExecutor", destroyMethod = "shutdownNow")
    ExecutorService chatToolExecutor() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("chat-tool-", 0).factory());
    }

    @Bean(name = "hybridSearchExecutor", destroyMethod = "shutdownNow")
    ExecutorService hybridSearchExecutor() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("hybrid-search-", 0).factory());
    }

    @Bean(name = "interactiveRetrievalExecutor", destroyMethod = "shutdownNow")
    ExecutorService interactiveRetrievalExecutor() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("interactive-retrieval-", 0).factory());
    }

    @Bean(name = "embeddingJobExecutor", destroyMethod = "shutdownNow")
    ExecutorService embeddingJobExecutor() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("embedding-job-", 0).factory());
    }

    @Bean(name = "importJobExecutor", destroyMethod = "shutdownNow")
    ExecutorService importJobExecutor() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("import-job-", 0).factory());
    }
}
