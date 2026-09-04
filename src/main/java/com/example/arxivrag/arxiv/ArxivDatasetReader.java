package com.example.arxivrag.arxiv;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;

@Component
public class ArxivDatasetReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArxivDatasetReader.class);

    private final ObjectMapper objectMapper;

    public ArxivDatasetReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ReadResult read(Path datasetPath, ArxivImportProperties.MalformedRecordPolicy malformedRecordPolicy, Predicate<ArxivDatasetRecord> consumer) {
        var recordsRead = 0L;
        var invalidRecords = 0L;
        var lineNumber = 0L;

        try (BufferedReader reader = Files.newBufferedReader(datasetPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }

                try {
                    var record = objectMapper.readValue(line, ArxivRecord.class);
                    recordsRead++;
                    if (!consumer.test(new ArxivDatasetRecord(lineNumber, record))) {
                        break;
                    }
                } catch (Exception exception) {
                    invalidRecords++;
                    if (malformedRecordPolicy == ArxivImportProperties.MalformedRecordPolicy.FAIL_FAST) {
                        throw new ArxivImportException("Failed to parse arXiv JSONL record at line " + lineNumber, exception);
                    }
                    LOGGER.warn("Skipping malformed arXiv JSONL record at line {}", lineNumber, exception);
                }
            }
        } catch (IOException exception) {
            throw new ArxivImportException("Failed to read arXiv dataset file: " + datasetPath, exception);
        }

        return new ReadResult(recordsRead, invalidRecords);
    }

    public record ReadResult(long recordsRead, long invalidRecords) {
    }
}
