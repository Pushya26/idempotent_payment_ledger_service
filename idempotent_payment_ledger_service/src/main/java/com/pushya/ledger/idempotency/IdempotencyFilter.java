package com.pushya.ledger.idempotency;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;

@Component
@Order(1)
public class IdempotencyFilter extends OncePerRequestFilter {
    private final StringRedisTemplate redis;
    private final Duration claimTtl;
    private final Duration resultTtl;

    public IdempotencyFilter(
            StringRedisTemplate redis,
            @Value("${ledger.idempotency.claim-ttl-seconds:30}") long claimTtlSeconds,
            @Value("${ledger.idempotency.result-ttl-hours:24}") long resultTtlHours) {
        this.redis = redis;
        this.claimTtl = Duration.ofSeconds(claimTtlSeconds);
        this.resultTtl = Duration.ofHours(resultTtlHours);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !request.getRequestURI().startsWith("/v1/payment_intents");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getHeader("Idempotency-Key");
        if (key == null || key.isBlank()) {
            writeJson(response, 400, "missing_idempotency_key", "Idempotency-Key header is required on this endpoint.");
            return;
        }

        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request, 1024 * 1024);
        wrappedRequest.getInputStream().readAllBytes();
        String requestHash = sha256(wrappedRequest.getContentAsByteArray());
        String redisKey = "idem:" + key;

        Boolean claimed = redis.opsForValue().setIfAbsent(redisKey, encode("IN_PROGRESS", requestHash, 0, ""), claimTtl);
        if (Boolean.TRUE.equals(claimed)) {
            ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
            try {
                chain.doFilter(wrappedRequest, wrappedResponse);
            } finally {
                String body = new String(wrappedResponse.getContentAsByteArray(), StandardCharsets.UTF_8);
                redis.opsForValue().set(redisKey, encode("COMPLETED", requestHash, wrappedResponse.getStatus(), body), resultTtl);
                wrappedResponse.copyBodyToResponse();
            }
            return;
        }

        String existingRaw = redis.opsForValue().get(redisKey);
        if (existingRaw == null) {
            writeJson(response, 409, "idempotency_conflict", "Retry the request.");
            return;
        }

        IdemRecord existing = decode(existingRaw);
        if ("IN_PROGRESS".equals(existing.status())) {
            writeJson(response, 409, "idempotency_conflict", "A request with this Idempotency-Key is already being processed. Retry shortly.");
            return;
        }
        if (!existing.requestHash().equals(requestHash)) {
            writeJson(response, 422, "idempotency_key_reused", "This Idempotency-Key was already used with a different request body.");
            return;
        }

        response.setStatus(existing.responseStatus());
        response.setContentType("application/json");
        response.getWriter().write(existing.responseBody());
    }

    private static void writeJson(HttpServletResponse response, int status, String error, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"%s\",\"message\":\"%s\"}".formatted(error, message));
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String encode(String status, String hash, int responseStatus, String body) {
        return status + ":" + hash + ":" + responseStatus + ":"
                + Base64.getEncoder().encodeToString(body.getBytes(StandardCharsets.UTF_8));
    }

    private static IdemRecord decode(String raw) {
        String[] parts = raw.split(":", 4);
        String body = parts.length > 3 ? new String(Base64.getDecoder().decode(parts[3]), StandardCharsets.UTF_8) : "";
        return new IdemRecord(parts[0], parts[1], Integer.parseInt(parts[2]), body);
    }

    private record IdemRecord(String status, String requestHash, int responseStatus, String responseBody) {
    }
}
