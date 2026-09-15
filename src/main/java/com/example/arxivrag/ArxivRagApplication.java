package com.example.arxivrag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class ArxivRagApplication {

    public static void main(String[] args) {
        var application = new SpringApplication(ArxivRagApplication.class);
        if (isCommandLineWorker(args)) {
            application.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
        }
        application.run(args);
    }

    private static boolean isCommandLineWorker(String[] args) {
        return java.util.Arrays.stream(args).anyMatch(argument ->
                "embed-papers".equals(argument)
                        || "import-arxiv".equals(argument)
                        || "chat-model".equals(argument)
                        || "chat-benchmark".equals(argument)
                        || "search-vector".equals(argument));
    }
}
