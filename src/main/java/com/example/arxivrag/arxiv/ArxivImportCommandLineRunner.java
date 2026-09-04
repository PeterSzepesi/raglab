package com.example.arxivrag.arxiv;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

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
        var sourceArgs = args.getSourceArgs();
        if (sourceArgs.length == 0 || !"import-arxiv".equals(sourceArgs[0])) {
            return;
        }

        if (sourceArgs.length != 2) {
            System.err.println("Usage: import-arxiv <path-to-arxiv-jsonl>");
            System.exit(SpringApplication.exit(applicationContext, () -> 2));
        }

        try {
            var result = importService.importDataset(Path.of(sourceArgs[1]));
            System.out.print(result.formatSummary());
            System.exit(SpringApplication.exit(applicationContext, () -> 0));
        } catch (ArxivImportException exception) {
            System.err.println(exception.getMessage());
            System.exit(SpringApplication.exit(applicationContext, () -> 1));
        }
    }
}
