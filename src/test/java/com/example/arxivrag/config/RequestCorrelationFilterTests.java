package com.example.arxivrag.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestCorrelationFilterTests {

    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

    @Test
    void preservesASafeCallerRequestIdAndPropagatesItEverywhere() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/health");
        request.addHeader(RequestCorrelationFilter.HEADER, "browser-request_42");
        var response = new MockHttpServletResponse();
        var seenInChain = new java.util.concurrent.atomic.AtomicReference<String>();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                seenInChain.set(MDC.get("requestId")));

        assertThat(request.getAttribute(RequestCorrelationFilter.ATTRIBUTE)).isEqualTo("browser-request_42");
        assertThat(response.getHeader(RequestCorrelationFilter.HEADER)).isEqualTo("browser-request_42");
        assertThat(seenInChain).hasValue("browser-request_42");
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void replacesUnsafeCallerInputWithAServerGeneratedIdentifier() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/health");
        request.addHeader(RequestCorrelationFilter.HEADER, "unsafe value\r\nInjected: true");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> { });

        var requestId = response.getHeader(RequestCorrelationFilter.HEADER);
        assertThat(requestId).matches("[0-9a-f-]{36}");
        assertThat(request.getAttribute(RequestCorrelationFilter.ATTRIBUTE)).isEqualTo(requestId);
        assertThat(MDC.get("requestId")).isNull();
    }
}
