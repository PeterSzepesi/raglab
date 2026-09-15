package com.example.arxivrag.arxiv;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class ArxivImportJobManager {
    private final String ownerId = UUID.randomUUID().toString();
    private final ArxivImportService imports;
    private final ArxivImportJobRepository jobs;
    private final ExecutorService executor;

    public ArxivImportJobManager(ArxivImportService imports, ArxivImportJobRepository jobs,
                                 @Qualifier("importJobExecutor") ExecutorService executor) {
        this.imports = imports;
        this.jobs = jobs;
        this.executor = executor;
    }

    @PostConstruct
    void recover() { jobs.recoverExpired(); }

    public ArxivImportJob start(Path path, ArxivImportOptions options) {
        validatePath(path);
        var id = jobs.create(path.toAbsolutePath().normalize().toString(), options);
        submit(id);
        return require(id);
    }

    public ArxivImportJob resume(UUID id) {
        var job = require(id);
        if (!List.of("FAILED", "CANCELLED", "INTERRUPTED").contains(job.status())) {
            throw new IllegalStateException("Only failed, cancelled, or interrupted imports can be resumed");
        }
        submit(id);
        return require(id);
    }

    public ArxivImportJob cancel(UUID id) {
        if (!jobs.requestCancellation(id)) throw new IllegalStateException("Import job is not cancellable");
        jobs.cancelQueued(id);
        return require(id);
    }

    public ArxivImportJob find(UUID id) { return require(id); }
    public List<ArxivImportJob> recent(int limit) { return jobs.recent(limit); }

    private void submit(UUID id) { executor.submit(() -> run(id)); }

    private void run(UUID id) {
        if (!jobs.claim(id, ownerId)) return;
        var job = require(id);
        var path = Path.of(job.sourcePath());
        try {
            validatePath(path);
            var options = new ArxivImportOptions(job.categories(), job.fromDate(), job.maxRecords());
            var execution = imports.importDataset(path, options, () -> jobs.cancellationRequested(id),
                    progress -> jobs.progress(id, ownerId, progress));
            jobs.complete(id, ownerId, execution.result(), checksum(path), execution.cancelled(),
                    execution.malformedLineSamples());
        } catch (Throwable failure) {
            jobs.fail(id, ownerId, failure);
        }
    }

    private ArxivImportJob require(UUID id) {
        return jobs.find(id).orElseThrow(() -> new ArxivImportJobNotFoundException(id));
    }

    private static void validatePath(Path path) {
        if (path == null || !Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IllegalArgumentException("Dataset path must identify a readable server-side file");
        }
    }

    private static String checksum(Path path) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            var buffer = new byte[64 * 1024];
            for (int read; (read = input.read(buffer)) >= 0; ) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public static final class ArxivImportJobNotFoundException extends RuntimeException {
        public ArxivImportJobNotFoundException(UUID id) { super("Import job not found: " + id); }
    }
}
