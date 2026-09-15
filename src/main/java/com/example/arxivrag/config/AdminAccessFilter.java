package com.example.arxivrag.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AdminAccessFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Admin-Token";
    private final AdminAccessProperties properties;
    private final ObjectMapper mapper;

    public AdminAccessFilter(AdminAccessProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        var path = request.getRequestURI();
        var mutation = !"GET".equalsIgnoreCase(request.getMethod()) && !"HEAD".equalsIgnoreCase(request.getMethod());
        return !(path.matches("/api(?:/v1)?/imports(?:/.*)?")
                || mutation && path.matches("/api(?:/v1)?/embeddings/jobs(?:/.*)?")
                || mutation && path.matches("/api(?:/v1)?/benchmarks(?:/.*)?")
                || path.matches("/api(?:/v1)?/search/diagnostics(?:/.*)?"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (allowed(request)) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        var requestId = request.getAttribute(RequestCorrelationFilter.ATTRIBUTE);
        mapper.writeValue(response.getOutputStream(), Map.of(
                "type", "https://arxiv-rag.local/problems/admin-access-required",
                "title", "Forbidden", "status", 403,
                "detail", "This operation requires local access or a configured administrator token.",
                "code", "admin-access-required", "retryable", false,
                "requestId", requestId == null ? "unavailable" : requestId.toString(),
                "timestamp", Instant.now().toString()));
    }

    private boolean allowed(HttpServletRequest request) {
        if (properties.tokenConfigured()) {
            var supplied = request.getHeader(HEADER);
            return supplied != null && MessageDigest.isEqual(properties.getToken().getBytes(StandardCharsets.UTF_8),
                    supplied.getBytes(StandardCharsets.UTF_8));
        }
        return properties.isLocalOnly() && isLoopback(request.getRemoteAddr());
    }

    private static boolean isLoopback(String address) {
        try { return java.net.InetAddress.getByName(address).isLoopbackAddress(); }
        catch (Exception ignored) { return false; }
    }
}
