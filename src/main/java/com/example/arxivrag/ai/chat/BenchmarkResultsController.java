package com.example.arxivrag.ai.chat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping({"/api/benchmarks", "/api/v1/benchmarks"})
@ConditionalOnProperty(name = "rag.features.benchmarks-enabled", havingValue = "true")
public class BenchmarkResultsController {
    private static final Path ROOT = Path.of("benchmarks", "results").toAbsolutePath().normalize();
    private final ObjectMapper mapper;

    public BenchmarkResultsController(ObjectMapper mapper) { this.mapper = mapper; }

    @GetMapping
    public List<BenchmarkRun> runs() throws Exception {
        if (!Files.isDirectory(ROOT)) return List.of();
        try (var files = Files.list(ROOT)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(this::metadata).sorted(Comparator.comparing(BenchmarkRun::modifiedAt).reversed()).toList();
        }
    }

    @GetMapping("/{name:.+}")
    public JsonNode run(@PathVariable String name) throws Exception {
        var path = resolve(name);
        if (!Files.isRegularFile(path)) throw new IllegalArgumentException("Benchmark result does not exist");
        return mapper.readTree(Files.readString(path));
    }

    private BenchmarkRun metadata(Path path) {
        try {
            var json = mapper.readTree(Files.readString(path));
            return new BenchmarkRun(path.getFileName().toString(), Files.getLastModifiedTime(path).toInstant(),
                    json.path("createdAt").asText(""), json.path("variant").asText(""),
                    json.path("applicationCommit").asText(""), json.path("summaries"));
        } catch (Exception failure) {
            return new BenchmarkRun(path.getFileName().toString(), Instant.EPOCH, "", "", "", null);
        }
    }

    private static Path resolve(String name) {
        if (!name.matches("[A-Za-z0-9._-]+\\.json")) throw new IllegalArgumentException("Invalid result name");
        var path = ROOT.resolve(name).normalize();
        if (!path.startsWith(ROOT)) throw new IllegalArgumentException("Invalid result path");
        return path;
    }

    public record BenchmarkRun(String name, Instant modifiedAt, String createdAt, String variant,
                               String applicationCommit, JsonNode summaries) { }
}
