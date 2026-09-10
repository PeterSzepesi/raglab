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

    private final EmbeddingIngestionProperties properties;

    public EmbeddingIngestionCommandLineRunner(
            EmbeddingIngestionService embeddingIngestionService,
            ApplicationContext applicationContext,
            EmbeddingIngestionProperties properties
    ) {
        this.embeddingIngestionService = embeddingIngestionService;
        this.applicationContext = applicationContext;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        var commandArgs = args.getNonOptionArgs();
        if (commandArgs.isEmpty() || !"embed-papers".equals(commandArgs.get(0))) {
            return;
        }

        if (commandArgs.size() != 1) {
            System.err.println("Usage: embed-papers [--max-papers=N] [--from-paper-id=N] [--to-paper-id=N]"
                    + " [--category=cs.AI] [--from-date=YYYY-MM-DD] [--to-date=YYYY-MM-DD] [--dry-run]");
            System.exit(SpringApplication.exit(applicationContext, () -> 2));
        }

        try {
            var result = embeddingIngestionService.ingest(EmbeddingIngestionRequest.from(
                    properties, args));
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
