package com.example.arxivrag.ai.foundry;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class FoundryRetryPolicyTests {

    @Test
    void addsBoundedJitterAndHonorsRetryAfter() {
        var properties = new FoundryProperties();
        properties.setRetryInitialBackoff(Duration.ofMillis(100));
        properties.setRetryMaxBackoff(Duration.ofMillis(200));
        var policy = new FoundryRetryPolicy(properties);
        var transientFailure = FoundryClient.FoundryProviderException.network("network", new Exception());

        assertThat(policy.delay(transientFailure, 0)).isBetween(Duration.ofMillis(75), Duration.ofMillis(125));
        assertThat(policy.delay(transientFailure, 10)).isBetween(Duration.ofMillis(150), Duration.ofMillis(200));

        var retryAfter = new FoundryClient.FoundryProviderException("busy", 429, Duration.ofSeconds(3), "id");
        assertThat(policy.delay(retryAfter, 0)).isEqualTo(Duration.ofSeconds(3));
    }

    @Test
    void rejectsRetryThatWouldOutliveOriginalRequestDeadline() {
        var properties = new FoundryProperties();
        properties.setRequestTimeout(Duration.ofMillis(10));
        var policy = new FoundryRetryPolicy(properties);

        assertThat(policy.withinRequestDeadline(System.nanoTime(), Duration.ofSeconds(1))).isFalse();
    }
}
