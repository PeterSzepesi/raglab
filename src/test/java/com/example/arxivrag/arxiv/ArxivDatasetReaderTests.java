package com.example.arxivrag.arxiv;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Path;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class ArxivDatasetReaderTests {

    @Autowired
    private ArxivDatasetReader datasetReader;

    @Test
    void readsValidRecordsAndSkipsMalformedLines() {
        var records = new ArrayList<ArxivDatasetRecord>();
        var result = datasetReader.read(
                Path.of("src/test/resources/fixtures/arxiv-tiny.jsonl"),
                ArxivImportProperties.MalformedRecordPolicy.WARN_AND_SKIP,
                record -> {
                    records.add(record);
                    return true;
                }
        );

        assertThat(result.recordsRead()).isEqualTo(4);
        assertThat(result.invalidRecords()).isEqualTo(1);
        assertThat(records).hasSize(4);
        assertThat(records.getFirst().lineNumber()).isEqualTo(1);
        assertThat(records.getFirst().record().id()).isEqualTo("2401.00001");
    }
}
