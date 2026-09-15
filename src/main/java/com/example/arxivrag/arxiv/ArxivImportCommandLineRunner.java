package com.example.arxivrag.arxiv;

import java.nio.file.Path;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class ArxivImportCommandLineRunner implements ApplicationRunner {

    private final ArxivImportService importService;

    private final ApplicationContext applicationContext;

    public ArxivImportCommandLineRunner(ArxivImportService importService, ApplicationContext applicationContext) {
        this.importService = importService;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        var commandArgs = args.getNonOptionArgs();
        if (commandArgs.isEmpty() || !"import-arxiv".equals(commandArgs.get(0))) {
            return;
        }

        if (commandArgs.size() != 2) {
            System.err.println("Usage: import-arxiv <path-to-arxiv-jsonl>");
            System.exit(SpringApplication.exit(applicationContext, () -> 2));
        }

        try {
            var datasetPath = Path.of(commandArgs.get(1));
            System.out.println("Starting arXiv import from " + datasetPath);
            System.out.flush();
            var result = importService.importDataset(datasetPath);
            System.out.print(result.formatSummary());
            System.exit(SpringApplication.exit(applicationContext, () -> 0));
        } catch (ArxivImportException exception) {
            System.err.println(exception.getMessage());
            System.exit(SpringApplication.exit(applicationContext, () -> 1));
        }
    }
}
