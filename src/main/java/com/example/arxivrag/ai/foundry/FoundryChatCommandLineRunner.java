package com.example.arxivrag.ai.foundry;

import com.example.arxivrag.model.api.ChatModelGateway;
import com.example.arxivrag.model.api.ModelDeploymentConfiguration;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;


@Component
@Profile("foundry")
public class FoundryChatCommandLineRunner implements ApplicationRunner {

    private final ChatModelGateway chatModel;
    private final ModelDeploymentConfiguration deployments;

    private final ApplicationContext applicationContext;

    public FoundryChatCommandLineRunner(ChatModelGateway chatModel, ModelDeploymentConfiguration deployments,
                                       ApplicationContext applicationContext) {
        this.chatModel = chatModel;
        this.deployments = deployments;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        var commandArgs = args.getNonOptionArgs();
        if (commandArgs.isEmpty() || !"chat-model".equals(commandArgs.get(0))) {
            return;
        }
        if (commandArgs.size() < 2) {
            System.err.println("Usage: chat-model <question>");
            System.exit(SpringApplication.exit(applicationContext, () -> 2));
        }

        var question = String.join(" ", commandArgs.subList(1, commandArgs.size()));
        try {
            var messages = java.util.List.<java.util.Map<String, Object>>of(
                    java.util.Map.of("role", "system", "content",
                            "You are a concise assistant. This is a connectivity smoke test; do not claim to have searched the paper database."),
                    java.util.Map.of("role", "user", "content", question));
            var result = chatModel.generate(deployments.chatDeployment(), messages, java.util.List.of(),
                    new ChatModelGateway.ChatModelOptions(200, null, "none", null));
            System.out.printf("Microsoft AI Foundry chat deployment: %s%n%n%s%n",
                    deployments.chatDeployment(), result.content());
            System.exit(SpringApplication.exit(applicationContext, () -> 0));
        } catch (RuntimeException exception) {
            System.err.println(exception.getMessage());
            System.exit(SpringApplication.exit(applicationContext, () -> 1));
        }
    }
}
