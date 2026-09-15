package com.example.arxivrag.ai.foundry;

import java.net.http.HttpHeaders;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Converts provider HTTP failures to one safe, stable internal failure. */
@Component
@Profile("foundry")
final class FoundryErrorMapper {

    FoundryClient.FoundryProviderException fromHttp(int statusCode, HttpHeaders headers, String body) {
        var retryAfter = headers == null ? null : headers.firstValue("retry-after")
                .flatMap(FoundryErrorMapper::parseRetryAfter).orElse(null);
        return new FoundryClient.FoundryProviderException(
                "Foundry request failed with HTTP " + statusCode + ": " + truncate(body),
                statusCode, retryAfter, requestId(headers));
    }

    static String requestId(HttpHeaders headers) {
        if (headers == null) return null;
        return headers.firstValue("apim-request-id")
                .or(() -> headers.firstValue("x-request-id"))
                .orElse(null);
    }

    private static java.util.Optional<Duration> parseRetryAfter(String value) {
        try {
            return java.util.Optional.of(Duration.ofSeconds(Long.parseLong(value.trim())));
        } catch (NumberFormatException ignored) {
            try {
                var retryAt = ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME);
                var duration = Duration.between(Instant.now(), retryAt.toInstant());
                return java.util.Optional.of(duration.isNegative() ? Duration.ZERO : duration);
            } catch (RuntimeException ignoredDate) {
                return java.util.Optional.empty();
            }
        }
    }

    private static String truncate(String value) {
        if (value == null) return "";
        return value.length() <= 500 ? value : value.substring(0, 500) + "...";
    }
}
