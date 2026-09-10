package com.example.arxivrag.embedding;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class EmbeddingIngestionCommandLineRunner implements ApplicationRunner {

    private final EmbeddingIngestionService embeddingIngestionService;

    private final ApplicationContext applicationContext;

    public EmbeddingIngestionCommandLineRunner(EmbeddingIngestionService embeddingIngestionService, ApplicationContext applicationContext) {
        this.embeddingIngestionService = embeddingIngestionService;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        var commandArgs = args.getNonOptionArgs();
        if (commandArgs.isEmpty() || !"embed-papers".equals(commandArgs.get(0))) {
            return;
        }

        if (commandArgs.size() != 1) {
            System.err.println("Usage: embed-papers");
            System.exit(SpringApplication.exit(applicationContext, () -> 2));
        }

        try {
            var result = embeddingIngestionService.ingestAll();
            System.out.print(result.formatSummary());
            System.out.flush();
            System.exit(SpringApplication.exit(applicationContext, () -> 0));
        } catch (RuntimeException exception) {
            System.err.println("Embedding ingestion failed: " + exception.getMessage());
            System.err.flush();
            System.exit(SpringApplication.exit(applicationContext, () -> 1));
        }
    }
}
