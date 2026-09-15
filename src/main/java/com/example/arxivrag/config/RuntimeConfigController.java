package com.example.arxivrag.config;

import com.example.arxivrag.ai.chat.ChatProperties;
import com.example.arxivrag.ai.foundry.FoundryProperties;
import com.example.arxivrag.embedding.EmbeddingIngestionProperties;
import java.util.Arrays;
import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/runtime-config", "/api/v1/capabilities"})
public class RuntimeConfigController {

    private final Environment environment;

    private final RagRuntimeProperties properties;
    private final FoundryProperties foundry;
    private final EmbeddingIngestionProperties embedding;
    private final ChatProperties chat;
    private final AdminAccessProperties admin;

    public RuntimeConfigController(Environment environment, RagRuntimeProperties properties,
                                   FoundryProperties foundry, EmbeddingIngestionProperties embedding,
                                   ChatProperties chat, AdminAccessProperties admin) {
        this.environment = environment;
        this.properties = properties;
        this.foundry = foundry;
        this.embedding = embedding;
        this.chat = chat;
        this.admin = admin;
    }

    @GetMapping
    public RuntimeConfigResponse runtimeConfig() {
        var activeProfiles = Arrays.stream(environment.getActiveProfiles()).toList();
        var displayedProfiles = activeProfiles.isEmpty()
                ? Arrays.stream(environment.getDefaultProfiles()).toList()
                : activeProfiles;
        var features = properties.getFeatures();

        return new RuntimeConfigResponse(
                environment.getProperty("spring.application.name", "arxiv-rag"),
                properties.getRuntime().getProfile(),
                displayedProfiles,
                List.of(
                        new FeatureFlag("Import", features.isImportEnabled()),
                        new FeatureFlag("Papers", features.isPapersEnabled()),
                        new FeatureFlag("Search", features.isSearchEnabled()),
                        new FeatureFlag("Chat", features.isChatEnabled()),
                        new FeatureFlag("Vector Stores", features.isVectorStoresEnabled()),
                        new FeatureFlag("Benchmarks", features.isBenchmarksEnabled())
                ),
                facts()
        );
    }

    private RuntimeFacts facts() {
        return new RuntimeFacts(
                "foundry".equalsIgnoreCase(properties.getRuntime().getProfile()) ? "MICROSOFT_AI_FOUNDRY" : "OFFLINE",
                endpointHost(foundry.getEndpoint()), foundry.getChatDeployment(), foundry.getEmbeddingDeployment(),
                embedding.getDimensions(), foundry.getAuthenticationMode(),
                !foundry.getApiKey().isBlank() || !"api-key".equalsIgnoreCase(foundry.getAuthenticationMode()),
                admin.tokenConfigured() ? "TOKEN" : admin.isLocalOnly() ? "LOCAL_ONLY" : "DISABLED",
                chat.getRetrievalPolicyVersion(), chat.getPromptVersion(), chat.isAnswerCacheEnabled());
    }

    private static String endpointHost(String endpoint) {
        try { return endpoint == null || endpoint.isBlank() ? "not-configured" : java.net.URI.create(endpoint).getHost(); }
        catch (RuntimeException ignored) { return "invalid"; }
    }

    public record RuntimeConfigResponse(
            String applicationName,
            String configuredProfile,
            List<String> activeProfiles,
            List<FeatureFlag> features,
            RuntimeFacts facts
    ) {
    }

    public record FeatureFlag(String name, boolean enabled) {
    }

    public record RuntimeFacts(String provider, String endpointHost, String chatDeployment,
                               String embeddingDeployment, int embeddingDimensions, String authenticationMode,
                               boolean credentialConfigured, String adminProtection,
                               String retrievalPolicyVersion, String promptVersion, boolean answerCacheEnabled) { }
}
