package com.example.arxivrag.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "rag.execution")
public class ExecutionProperties {

    @Min(1)
    @Max(1_000)
    private int maxConcurrentChats = 16;

    @Min(1)
    @Max(1_000)
    private int maxConcurrentRetrievals = 32;

    @NotNull
    private Duration admissionWait = Duration.ofMillis(100);

    @NotNull
    private Duration semanticSearchTimeout = Duration.ofSeconds(2);

    public int getMaxConcurrentChats() {
        return maxConcurrentChats;
    }

    public void setMaxConcurrentChats(int maxConcurrentChats) {
        this.maxConcurrentChats = maxConcurrentChats;
    }

    public int getMaxConcurrentRetrievals() {
        return maxConcurrentRetrievals;
    }

    public void setMaxConcurrentRetrievals(int maxConcurrentRetrievals) {
        this.maxConcurrentRetrievals = maxConcurrentRetrievals;
    }

    public Duration getAdmissionWait() {
        return admissionWait;
    }

    public void setAdmissionWait(Duration admissionWait) {
        this.admissionWait = admissionWait;
    }

    public Duration getSemanticSearchTimeout() {
        return semanticSearchTimeout;
    }

    public void setSemanticSearchTimeout(Duration semanticSearchTimeout) {
        this.semanticSearchTimeout = semanticSearchTimeout;
    }
}
