package com.example.arxivrag.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

class AdminAccessFilterTests {

    @Test
    void permitsLocalAdministrativeRequestsWhenNoTokenIsConfigured() throws Exception {
        var properties = new AdminAccessProperties();
        var request = request("POST", "/api/v1/imports/jobs", "127.0.0.1");
        var response = new MockHttpServletResponse();

        new AdminAccessFilter(properties, new ObjectMapper())
                .doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void rejectsRemoteAdministrativeRequestsWithProblemDetails() throws Exception {
        var properties = new AdminAccessProperties();
        var request = request("POST", "/api/v1/embeddings/jobs", "192.0.2.12");
        request.setAttribute(RequestCorrelationFilter.ATTRIBUTE, "request-123");
        var response = new MockHttpServletResponse();

        new AdminAccessFilter(properties, new ObjectMapper())
                .doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString()).contains("admin-access-required", "request-123");
    }

    @Test
    void configuredTokenIsRequiredEvenFromLoopback() throws Exception {
        var properties = new AdminAccessProperties();
        properties.setToken("expected-secret");
        var denied = request("POST", "/api/v1/imports/jobs", "127.0.0.1");
        var deniedResponse = new MockHttpServletResponse();
        var filter = new AdminAccessFilter(properties, new ObjectMapper());
        filter.doFilter(denied, deniedResponse, new MockFilterChain());

        var allowed = request("POST", "/api/v1/imports/jobs", "127.0.0.1");
        allowed.addHeader(AdminAccessFilter.HEADER, "expected-secret");
        var allowedResponse = new MockHttpServletResponse();
        filter.doFilter(allowed, allowedResponse, new MockFilterChain());

        assertThat(deniedResponse.getStatus()).isEqualTo(403);
        assertThat(allowedResponse.getStatus()).isEqualTo(200);
    }

    @Test
    void protectsCostlySearchDiagnosticsFromRemoteCallers() throws Exception {
        var properties = new AdminAccessProperties();
        var request = request("POST", "/api/v1/search/diagnostics/vector-recall", "192.0.2.12");
        var response = new MockHttpServletResponse();

        new AdminAccessFilter(properties, new ObjectMapper())
                .doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
    }

    private static MockHttpServletRequest request(String method, String path, String remoteAddress) {
        var request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(remoteAddress);
        return request;
    }
}
