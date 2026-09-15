package com.example.arxivrag.search;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("search.cache")
public class SearchCacheProperties {
    private boolean enabled = true;
    @NotNull private Duration queryEmbeddingTtl = Duration.ofMinutes(10);
    @Positive private long queryEmbeddingMaximumSize = 1000;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Duration getQueryEmbeddingTtl() { return queryEmbeddingTtl; }
    public void setQueryEmbeddingTtl(Duration value) { this.queryEmbeddingTtl = value; }
    public long getQueryEmbeddingMaximumSize() { return queryEmbeddingMaximumSize; }
    public void setQueryEmbeddingMaximumSize(long value) { this.queryEmbeddingMaximumSize = value; }
}
