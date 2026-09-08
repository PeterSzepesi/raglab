package com.example.arxivrag.vector;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class VectorSearchCommandLineRunner implements ApplicationRunner {

    private final SemanticSearchService semanticSearchService;

    private final ApplicationContext applicationContext;

    public VectorSearchCommandLineRunner(
            SemanticSearchService semanticSearchService,
            ApplicationContext applicationContext
    ) {
        this.semanticSearchService = semanticSearchService;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        var sourceArgs = args.getSourceArgs();
        if (sourceArgs.length == 0 || !"search-vector".equals(sourceArgs[0])) {
            return;
        }

        if (sourceArgs.length < 2) {
            System.err.println("Usage: search-vector <query>");
            System.exit(SpringApplication.exit(applicationContext, () -> 2));
        }

        var query = String.join(" ", Arrays.copyOfRange(sourceArgs, 1, sourceArgs.length));
        try {
            var results = semanticSearchService.search(query);
            System.out.printf("Semantic search complete%n%nQuery: %s%nEmbedding model: %s%nResults: %d%n%n",
                    query,
                    semanticSearchService.embeddingModelName(),
                    results.size());
            for (var index = 0; index < results.size(); index++) {
                var result = results.get(index);
                System.out.printf("%d. [%.6f] %s (%s)%n   %s%n%n",
                        index + 1,
                        result.score(),
                        result.title(),
                        result.arxivId(),
                        result.abstractText());
            }
            System.exit(SpringApplication.exit(applicationContext, () -> 0));
        } catch (RuntimeException exception) {
            System.err.println(exception.getMessage());
            System.exit(SpringApplication.exit(applicationContext, () -> 1));
        }
    }
}
