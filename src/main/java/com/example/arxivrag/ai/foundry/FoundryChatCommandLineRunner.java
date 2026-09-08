package com.example.arxivrag.ai.foundry;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
@Profile("foundry")
public class FoundryChatCommandLineRunner implements ApplicationRunner {

    private final FoundryChatGateway chatGateway;

    private final ApplicationContext applicationContext;

    public FoundryChatCommandLineRunner(FoundryChatGateway chatGateway, ApplicationContext applicationContext) {
        this.chatGateway = chatGateway;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        var sourceArgs = args.getSourceArgs();
        if (sourceArgs.length == 0 || !"chat-model".equals(sourceArgs[0])) {
            return;
        }
        if (sourceArgs.length < 2) {
            System.err.println("Usage: chat-model <question>");
            System.exit(SpringApplication.exit(applicationContext, () -> 2));
        }

        var question = String.join(" ", Arrays.copyOfRange(sourceArgs, 1, sourceArgs.length));
        try {
            var answer = chatGateway.generate(
                    "You are a concise assistant. This is a connectivity smoke test; do not claim to have searched the paper database.",
                    question
            );
            System.out.printf("Microsoft AI Foundry chat deployment: %s%n%n%s%n", chatGateway.modelName(), answer);
            System.exit(SpringApplication.exit(applicationContext, () -> 0));
        } catch (RuntimeException exception) {
            System.err.println(exception.getMessage());
            System.exit(SpringApplication.exit(applicationContext, () -> 1));
        }
    }
}
