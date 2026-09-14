package com.example.arxivrag.ai.chat;

import com.example.arxivrag.ai.foundry.FoundryProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@Profile("foundry")
public class ChatBenchmarkCommandLineRunner implements ApplicationRunner {

    private static final Path DEFAULT_CASES = Path.of("benchmarks", "chat-evaluation.jsonl");
    private final GroundedChatService chat;
    private final FoundryProperties foundry;
    private final ChatProperties properties;
    private final ObjectMapper objectMapper;
    private final ApplicationContext applicationContext;

    public ChatBenchmarkCommandLineRunner(
            GroundedChatService chat,
            FoundryProperties foundry,
            ChatProperties properties,
            ObjectMapper objectMapper,
            ApplicationContext applicationContext
    ) {
        this.chat = chat;
        this.foundry = foundry;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        var command = args.getNonOptionArgs();
        if (command.isEmpty() || !"chat-benchmark".equals(command.getFirst())) {
            return;
        }
        var path = command.size() > 1 ? Path.of(command.get(1)) : DEFAULT_CASES;
        var variant = option(args, "variant", "pre-retrieval");
        var deploymentOption = option(args, "deployment", "primary");
        try {
            var maxQuestions = Integer.parseInt(option(args, "max-questions", "30"));
            if (!List.of("agentic", "pre-retrieval", "streaming").contains(variant.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("--variant must be agentic, pre-retrieval, or streaming");
            }
            if (maxQuestions < 1) {
                throw new IllegalArgumentException("--max-questions must be positive");
            }
            var cases = readCases(path).stream().limit(maxQuestions).toList();
            var deployments = deployments(deploymentOption);
            var results = new ArrayList<BenchmarkResult>();
            for (var deployment : deployments) {
                for (var evaluationCase : cases) {
                    results.add(runCase(evaluationCase, variant, deployment));
                }
            }
            var report = report(path, variant, results);
            var output = Path.of("target", "chat-benchmark-results.json");
            Files.createDirectories(output.getParent());
            Files.writeString(output, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report));
            printSummary(report, output);
            System.exit(SpringApplication.exit(applicationContext, () -> 0));
        } catch (Exception exception) {
            System.err.println("Chat benchmark failed: " + exception.getMessage());
            System.exit(SpringApplication.exit(applicationContext, () -> 1));
        }
    }

    private BenchmarkResult runCase(ChatEvaluationCase evaluationCase, String variant, String deployment) {
        ChatExecution execution;
        var request = evaluationCase.request("agentic".equalsIgnoreCase(variant));
        if ("streaming".equalsIgnoreCase(variant)) {
            execution = chat.streamForDeployment(request, deployment, text -> { });
        } else {
            execution = chat.chatDetailedForDeployment(request, deployment);
        }
        var response = execution.response();
        var answerModelMs = execution.timing().milliseconds().getOrDefault(ChatStage.ANSWER_MODEL.metricName(), 0L);
        var cited = response.citations().stream().map(ChatCitation::arxivId).toList();
        var expectedPresent = cited.containsAll(evaluationCase.expectedArxivIds());
        var noEvidenceCorrect = !evaluationCase.expectNoEvidence() || cited.isEmpty();
        var inlineCitationsValid = inlineCitations(response.answer()).stream().allMatch(cited::contains);
        return new BenchmarkResult(
                evaluationCase.id(), variant.toLowerCase(Locale.ROOT), deployment,
                execution.timing().elapsed().toMillis(),
                execution.timing().milliseconds().getOrDefault(ChatStage.FIRST_TOKEN.metricName(), -1L),
                execution.timing().promptTokens(), execution.timing().completionTokens(),
                execution.timing().reasoningTokens(), execution.timing().cachedPromptTokens(),
                execution.timing().foundryCalls(), execution.timing().retryCount(), cited,
                answerModelMs <= 0 ? 0d : execution.timing().completionTokens() * 1000d / answerModelMs,
                estimatedCost(execution.timing()), expectedPresent, noEvidenceCorrect, inlineCitationsValid);
    }

    private Map<String, Object> report(Path path, String variant, List<BenchmarkResult> results) {
        var byDeployment = results.stream().collect(java.util.stream.Collectors.groupingBy(
                BenchmarkResult::deployment, LinkedHashMap::new, java.util.stream.Collectors.toList()));
        var summaries = new ArrayList<Map<String, Object>>();
        byDeployment.forEach((deployment, values) -> summaries.add(summary(deployment, values)));
        var report = new LinkedHashMap<String, Object>();
        report.put("createdAt", Instant.now());
        report.put("evaluationFile", path.toString());
        report.put("variant", variant);
        var configuration = new LinkedHashMap<String, Object>();
        configuration.put("maxCompletionTokens", properties.getMaxCompletionTokens());
        configuration.put("retrievalTopK", properties.getRetrievalTopK());
        configuration.put("contextCharacters", properties.getMaxContextCharactersTotal());
        report.put("chatConfiguration", configuration);
        report.put("summaries", summaries);
        report.put("results", results);
        return report;
    }

    private static Map<String, Object> summary(String deployment, List<BenchmarkResult> values) {
        var latencies = values.stream().map(BenchmarkResult::latencyMilliseconds).sorted().toList();
        var ttft = values.stream().map(BenchmarkResult::ttftMilliseconds).filter(value -> value >= 0).sorted().toList();
        var qualityPasses = values.stream().filter(BenchmarkResult::qualityPassed).count();
        var summary = new LinkedHashMap<String, Object>();
        summary.put("deployment", deployment);
        summary.put("requests", values.size());
        summary.put("latencyP50Ms", percentile(latencies, 0.50));
        summary.put("latencyP90Ms", percentile(latencies, 0.90));
        summary.put("latencyP95Ms", percentile(latencies, 0.95));
        summary.put("ttftP50Ms", percentile(ttft, 0.50));
        summary.put("ttftP95Ms", percentile(ttft, 0.95));
        summary.put("qualityPassRate", values.isEmpty() ? 0d : qualityPasses / (double) values.size());
        summary.put("averageGeneratedTokensPerSecond", values.stream()
                .mapToDouble(BenchmarkResult::generatedTokensPerSecond).average().orElse(0d));
        summary.put("totalPromptTokens", values.stream().mapToInt(BenchmarkResult::promptTokens).sum());
        summary.put("totalCompletionTokens", values.stream().mapToInt(BenchmarkResult::completionTokens).sum());
        var costs = values.stream().map(BenchmarkResult::estimatedCostUsd).filter(java.util.Objects::nonNull).toList();
        summary.put("averageEstimatedCostUsd", costs.isEmpty() ? null
                : costs.stream().mapToDouble(Double::doubleValue).average().orElse(0d));
        return summary;
    }

    private List<ChatEvaluationCase> readCases(Path path) throws Exception {
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("evaluation file does not exist: " + path);
        }
        var result = new ArrayList<ChatEvaluationCase>();
        for (var line : Files.readAllLines(path)) {
            if (!line.isBlank() && !line.stripLeading().startsWith("#")) {
                result.add(objectMapper.readValue(line, ChatEvaluationCase.class));
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("evaluation file contains no cases: " + path);
        }
        return result;
    }

    private List<String> deployments(String option) {
        return switch (option.toLowerCase(Locale.ROOT)) {
            case "primary" -> List.of(foundry.getChatDeployment());
            case "candidate" -> List.of(requireCandidate());
            case "both" -> List.of(foundry.getChatDeployment(), requireCandidate());
            default -> throw new IllegalArgumentException("--deployment must be primary, candidate, or both");
        };
    }

    private String requireCandidate() {
        var value = properties.getCandidateChatDeployment();
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("chat.candidate-chat-deployment is not configured");
        }
        return value;
    }

    private Double estimatedCost(ChatTiming timing) {
        var inputPrice = properties.getBenchmarkInputPricePerMillionTokens();
        var outputPrice = properties.getBenchmarkOutputPricePerMillionTokens();
        if (inputPrice == null || outputPrice == null) {
            return null;
        }
        var cachedPrice = properties.getBenchmarkCachedInputPricePerMillionTokens() == null
                ? inputPrice
                : properties.getBenchmarkCachedInputPricePerMillionTokens();
        var cached = Math.min(timing.promptTokens(), timing.cachedPromptTokens());
        var normalInput = Math.max(0, timing.promptTokens() - cached);
        return (normalInput * inputPrice + cached * cachedPrice + timing.completionTokens() * outputPrice) / 1_000_000d;
    }

    private static String option(ApplicationArguments args, String name, String fallback) {
        var values = args.getOptionValues(name);
        return values == null || values.isEmpty() ? fallback : values.getFirst();
    }

    private static long percentile(List<Long> sorted, double percentile) {
        if (sorted.isEmpty()) {
            return -1;
        }
        var index = (int) Math.ceil(percentile * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    private static List<String> inlineCitations(String answer) {
        var matcher = java.util.regex.Pattern.compile("\\[arXiv:([^]\\s]+)]", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(answer == null ? "" : answer);
        var values = new ArrayList<String>();
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        return values;
    }

    private static void printSummary(Map<String, Object> report, Path output) {
        System.out.println("Chat benchmark complete");
        System.out.println("Results: " + output.toAbsolutePath());
        System.out.println("Summaries: " + report.get("summaries"));
    }

    public record BenchmarkResult(
            String caseId, String variant, String deployment, long latencyMilliseconds, long ttftMilliseconds,
            int promptTokens, int completionTokens, int reasoningTokens, int cachedPromptTokens,
            int foundryCalls, int retries, List<String> citedArxivIds,
            double generatedTokensPerSecond, Double estimatedCostUsd,
            boolean expectedPapersPresent, boolean noEvidenceCorrect, boolean inlineCitationsValid
    ) {
        public boolean qualityPassed() {
            return expectedPapersPresent && noEvidenceCorrect && inlineCitationsValid;
        }
    }
}
