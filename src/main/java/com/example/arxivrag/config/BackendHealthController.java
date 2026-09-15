package com.example.arxivrag.config;

import com.example.arxivrag.ai.chat.ChatAnswerCache;
import com.example.arxivrag.ai.chat.ChatRetrievalCache;
import com.example.arxivrag.ai.foundry.FoundryOperationalState;
import com.example.arxivrag.ai.foundry.FoundryProperties;
import com.example.arxivrag.ai.foundry.ProviderCircuitBreaker;
import com.example.arxivrag.embedding.EmbeddingCheckpointRepository;
import com.example.arxivrag.embedding.EmbeddingJob;
import com.example.arxivrag.embedding.EmbeddingJobRepository;
import com.example.arxivrag.embedding.EmbeddingModelGateway;
import com.example.arxivrag.paper.PaperRepository;
import com.example.arxivrag.search.QueryEmbeddingCache;
import com.example.arxivrag.vector.VectorStoreGateway;
import java.time.Instant;
import java.util.Arrays;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/health", "/api/v1/health"})
public class BackendHealthController {

    private final Environment environment;
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final FoundryProperties foundry;
    private final PaperRepository papers;
    private final EmbeddingCheckpointRepository checkpoints;
    private final EmbeddingModelGateway embeddingModel;
    private final VectorStoreGateway vectors;
    private final EmbeddingJobRepository jobs;
    private final InteractiveAdmissionController admission;
    private final ExecutionProperties execution;
    private final ObjectProvider<ProviderCircuitBreaker> circuitBreaker;
    private final ChatRetrievalCache retrievalCache;
    private final ChatAnswerCache answerCache;
    private final QueryEmbeddingCache queryEmbeddingCache;
    private final FoundryOperationalState providerOperations;
    private final DatabaseObservability databaseObservability;

    public BackendHealthController(
            Environment environment,
            DataSource dataSource,
            JdbcTemplate jdbcTemplate,
            FoundryProperties foundry,
            PaperRepository papers,
            EmbeddingCheckpointRepository checkpoints,
            EmbeddingModelGateway embeddingModel,
            VectorStoreGateway vectors,
            EmbeddingJobRepository jobs,
            InteractiveAdmissionController admission,
            ExecutionProperties execution,
            ObjectProvider<ProviderCircuitBreaker> circuitBreaker,
            ChatRetrievalCache retrievalCache,
            ChatAnswerCache answerCache,
            QueryEmbeddingCache queryEmbeddingCache,
            FoundryOperationalState providerOperations,
            DatabaseObservability databaseObservability
    ) {
        this.environment = environment;
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
        this.foundry = foundry;
        this.papers = papers;
        this.checkpoints = checkpoints;
        this.embeddingModel = embeddingModel;
        this.vectors = vectors;
        this.jobs = jobs;
        this.admission = admission;
        this.execution = execution;
        this.circuitBreaker = circuitBreaker;
        this.retrievalCache = retrievalCache;
        this.answerCache = answerCache;
        this.queryEmbeddingCache = queryEmbeddingCache;
        this.providerOperations = providerOperations;
        this.databaseObservability = databaseObservability;
    }

    @GetMapping
    public BackendHealthResponse health() {
        var database = databaseStatus();
        var vector = vectorStatus(database.product());
        var paperCount = safeCount(papers::count);
        var vectorCount = safeCount(() -> vectors.countByEmbeddingModel(embeddingModel.modelName()));
        var checkpointCount = safeCount(() -> checkpoints.countDistinctPaperIdsByEmbeddingModel(
                embeddingModel.modelName()));
        var latest = safeLatestJob();
        var status = !database.reachable() ? "DOWN"
                : "DOWN".equals(vector.status()) ? "DEGRADED" : "UP";
        return new BackendHealthResponse(
                status,
                environment.getProperty("spring.application.name", "arxiv-rag"),
                Instant.now(),
                "UP".equals(status) ? "Backend services are reachable." : "One or more dependencies are degraded.",
                Arrays.asList(environment.getActiveProfiles()),
                database,
                vector,
                providerStatus(),
                new CorpusStatus(paperCount, vectorCount, checkpointCount,
                        paperCount == 0 ? 0.0d : vectorCount * 100.0d / paperCount),
                new AdmissionStatus(execution.getMaxConcurrentChats() - admission.availableChatPermits(),
                        admission.availableChatPermits(), execution.getMaxConcurrentChats(),
                        execution.getMaxConcurrentRetrievals() - admission.availableRetrievalPermits(),
                        admission.availableRetrievalPermits(), execution.getMaxConcurrentRetrievals()),
                new CacheStatus(cache(retrievalCache.stats()), cache(answerCache.stats()),
                        cache(queryEmbeddingCache.stats())),
                latest
        );
    }

    private DatabaseStatus databaseStatus() {
        try (var connection = dataSource.getConnection()) {
            var reachable = connection.isValid(2);
            var metadata = connection.getMetaData();
            var product = metadata.getDatabaseProductName();
            return new DatabaseStatus(reachable, product, metadata.getDatabaseProductVersion(),
                    databaseObservability.snapshot(product));
        } catch (Exception exception) {
            return new DatabaseStatus(false, "unknown", null, DatabaseObservability.Snapshot.notApplicable());
        }
    }

    private VectorStatus vectorStatus(String product) {
        if (product == null || !product.toLowerCase(java.util.Locale.ROOT).contains("postgresql")) {
            return new VectorStatus("NOT_APPLICABLE", environment.getProperty("vector.store.type", "memory"));
        }
        try {
            var installed = Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                    "select exists(select 1 from pg_extension where extname = 'vector')", Boolean.class));
            return new VectorStatus(installed ? "UP" : "DOWN",
                    environment.getProperty("vector.store.type", "pgvector"));
        } catch (RuntimeException exception) {
            return new VectorStatus("DOWN", environment.getProperty("vector.store.type", "pgvector"));
        }
    }

    private ProviderStatus providerStatus() {
        var foundryProfile = Arrays.stream(environment.getActiveProfiles()).anyMatch("foundry"::equalsIgnoreCase);
        var keyMode = "api-key".equalsIgnoreCase(foundry.getAuthenticationMode());
        var credentialConfigured = !keyMode || foundry.getApiKey() != null && !foundry.getApiKey().isBlank();
        var configured = foundryProfile && foundry.getEndpoint() != null && !foundry.getEndpoint().isBlank()
                && credentialConfigured;
        var breaker = circuitBreaker.getIfAvailable();
        return new ProviderStatus(foundryProfile ? "MICROSOFT_AI_FOUNDRY" : "OFFLINE",
                configured ? "CONFIGURED_NOT_PROBED" : foundryProfile ? "MISCONFIGURED" : "LOCAL",
                foundry.getChatDeployment(), foundry.getEmbeddingDeployment(),
                breaker == null ? "NOT_APPLICABLE" : breaker.state().name(),
                breaker == null ? java.util.Map.of() : breaker.states(), providerOperations.snapshot());
    }

    private EmbeddingJob safeLatestJob() {
        try {
            return jobs.findLatest().orElse(null);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static long safeCount(java.util.function.LongSupplier supplier) {
        try {
            return supplier.getAsLong();
        } catch (RuntimeException ignored) {
            return -1L;
        }
    }

    private static CacheSnapshot cache(com.github.benmanes.caffeine.cache.stats.CacheStats stats) {
        return new CacheSnapshot(stats.hitCount(), stats.missCount(), stats.hitRate(), stats.evictionCount(),
                stats.loadFailureCount());
    }

    public record BackendHealthResponse(
            String status,
            String applicationName,
            Instant checkedAt,
            String message,
            java.util.List<String> activeProfiles,
            DatabaseStatus database,
            VectorStatus vectorStore,
            ProviderStatus provider,
            CorpusStatus corpus,
            AdmissionStatus admission,
            CacheStatus caches,
            EmbeddingJob latestEmbeddingJob
    ) { }

    public record DatabaseStatus(boolean reachable, String product, String version,
                                 DatabaseObservability.Snapshot statistics) { }
    public record VectorStatus(String status, String type) { }
    public record ProviderStatus(String provider, String status, String chatDeployment, String embeddingDeployment,
                                 String circuitState, java.util.Map<String, ProviderCircuitBreaker.State> circuits,
                                 FoundryOperationalState.Snapshot operations) { }
    public record CorpusStatus(long papers, long vectorRows, long checkpointedPapers, double vectorCoveragePercent) { }
    public record AdmissionStatus(int activeChats, int availableChatPermits, int maximumChats,
                                  int activeRetrievals, int availableRetrievalPermits,
                                  int maximumRetrievals) { }
    public record CacheStatus(CacheSnapshot retrieval, CacheSnapshot answers, CacheSnapshot queryEmbeddings) { }
    public record CacheSnapshot(long hits, long misses, double hitRate, long evictions, long loadFailures) { }
}
