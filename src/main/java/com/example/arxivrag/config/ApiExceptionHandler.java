package com.example.arxivrag.config;

import com.example.arxivrag.arxiv.ArxivImportJobManager;
import com.example.arxivrag.ai.chat.ChatDeadlineExceededException;
import com.example.arxivrag.model.api.ChatModelGateway;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({IllegalArgumentException.class, ConstraintViolationException.class})
    ResponseEntity<ProblemDetail> badRequest(RuntimeException exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-request", exception.getMessage(), request, false);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        var detail = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("Request validation failed");
        return problem(HttpStatus.BAD_REQUEST, "invalid-request", detail, request, false);
    }

    @ExceptionHandler(CapacityExceededException.class)
    ResponseEntity<ProblemDetail> capacity(CapacityExceededException exception, HttpServletRequest request) {
        var response = problem(HttpStatus.TOO_MANY_REQUESTS, "capacity-exceeded",
                exception.getMessage(), request, true);
        response.getHeaders().set("Retry-After", "1");
        return response;
    }

    @ExceptionHandler(ChatModelGateway.ModelProviderException.class)
    ResponseEntity<ProblemDetail> provider(
            ChatModelGateway.ModelProviderException exception,
            HttpServletRequest request
    ) {
        var status = switch (exception.statusCode()) {
            case 429 -> HttpStatus.TOO_MANY_REQUESTS;
            case 408, 504 -> HttpStatus.GATEWAY_TIMEOUT;
            default -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        var response = problem(status, "model-provider-unavailable",
                "Microsoft AI Foundry could not complete the request. Retry later and use the request ID for diagnostics.",
                request, exception.retryable());
        if (exception.providerRequestId() != null) {
            response.getBody().setProperty("providerRequestId", exception.providerRequestId());
        }
        if (exception.retryAfter() != null) {
            response.getHeaders().set("Retry-After", Long.toString(Math.max(1, exception.retryAfter().toSeconds())));
        }
        return response;
    }

    @ExceptionHandler(ChatDeadlineExceededException.class)
    ResponseEntity<ProblemDetail> deadline(ChatDeadlineExceededException exception,
                                           HttpServletRequest request) {
        return problem(HttpStatus.GATEWAY_TIMEOUT, "request-deadline-exceeded",
                "The chat request exceeded its end-to-end deadline.", request, true);
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ProblemDetail> responseStatus(ResponseStatusException exception, HttpServletRequest request) {
        var status = HttpStatus.valueOf(exception.getStatusCode().value());
        return problem(status, "request-failed", exception.getReason(), request, false);
    }

    @ExceptionHandler(ArxivImportJobManager.ArxivImportJobNotFoundException.class)
    ResponseEntity<ProblemDetail> importJobNotFound(RuntimeException exception, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "import-job-not-found", exception.getMessage(), request, false);
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ProblemDetail> conflict(IllegalStateException exception, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "operation-conflict", exception.getMessage(), request, false);
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String code,
            String detail,
            HttpServletRequest request,
            boolean retryable
    ) {
        var safeDetail = detail == null || detail.isBlank() ? status.getReasonPhrase() : detail;
        var body = ProblemDetail.forStatusAndDetail(status, safeDetail);
        body.setType(URI.create("https://arxiv-rag.local/problems/" + code));
        body.setTitle(status.getReasonPhrase());
        body.setProperty("code", code);
        body.setProperty("error", safeDetail);
        body.setProperty("retryable", retryable);
        body.setProperty("requestId", requestId(request));
        return ResponseEntity.status(status).body(body);
    }

    private static String requestId(HttpServletRequest request) {
        var value = request.getAttribute(RequestCorrelationFilter.ATTRIBUTE);
        return value == null ? "unavailable" : value.toString();
    }
}
