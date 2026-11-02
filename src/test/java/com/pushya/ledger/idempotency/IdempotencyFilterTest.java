package com.pushya.ledger.idempotency;

import com.pushya.ledger.observability.PaymentMetrics;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyFilterTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private PaymentMetrics metrics;

    @Mock
    private FilterChain filterChain;

    private IdempotencyFilter filter;

    @BeforeEach
    void setUp() {
        filter = new IdempotencyFilter(redis, metrics, 30, 24);
    }

    // --- shouldNotFilter tests ---

    @Test
    void getRequestsAreNotFiltered() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/payment_intents/123");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void postToCreateEndpointIsNotFiltered() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/payment_intents");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void postToConfirmEndpointIsFiltered() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST",
                "/v1/payment_intents/550e8400-e29b-41d4-a716-446655440000/confirm");
        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    @Test
    void postToCancelEndpointIsFiltered() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST",
                "/v1/payment_intents/550e8400-e29b-41d4-a716-446655440000/cancel");
        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    // --- doFilterInternal tests ---

    @Test
    void missingIdempotencyKeyReturns400() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST",
                "/v1/payment_intents/123/confirm");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("missing_idempotency_key");
        verifyNoInteractions(filterChain);
    }

    @Test
    void blankIdempotencyKeyReturns400() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST",
                "/v1/payment_intents/123/confirm");
        request.addHeader("Idempotency-Key", "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("missing_idempotency_key");
    }

    @Test
    void newKeyClaimsAndProcessesRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST",
                "/v1/payment_intents/123/confirm");
        request.addHeader("Idempotency-Key", "key-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq("idem:key-123"), anyString(), any(Duration.class)))
                .thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(any(), any());
        verify(valueOps).set(eq("idem:key-123"), anyString(), any(Duration.class));
    }

    @Test
    void existingInProgressKeyReturns409() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST",
                "/v1/payment_intents/123/confirm");
        request.addHeader("Idempotency-Key", "key-456");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq("idem:key-456"), anyString(), any(Duration.class)))
                .thenReturn(false);
        when(valueOps.get("idem:key-456")).thenReturn("IN_PROGRESS:abc123:0:");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).contains("idempotency_conflict");
        verifyNoInteractions(filterChain);
        verify(metrics).incrementIdempotencyConflict();
    }

    @Test
    void nullExistingRecordReturns409() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST",
                "/v1/payment_intents/123/confirm");
        request.addHeader("Idempotency-Key", "key-expired");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq("idem:key-expired"), anyString(), any(Duration.class)))
                .thenReturn(false);
        when(valueOps.get("idem:key-expired")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(409);
        verifyNoInteractions(filterChain);
        verify(metrics).incrementIdempotencyConflict();
    }

    @Test
    void completedKeyWithSameBodyReplaysResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST",
                "/v1/payment_intents/123/confirm");
        request.addHeader("Idempotency-Key", "key-789");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq("idem:key-789"), anyString(), any(Duration.class)))
                .thenReturn(false);
        // SHA-256 of empty body
        String emptyHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        String cachedBody = Base64.getEncoder().encodeToString("{\"id\":\"abc\"}".getBytes());
        when(valueOps.get("idem:key-789")).thenReturn("COMPLETED:" + emptyHash + ":200:" + cachedBody);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEqualTo("{\"id\":\"abc\"}");
        verifyNoInteractions(filterChain);
        verify(metrics).incrementIdempotencyHit();
    }

    @Test
    void completedKeyWithDifferentBodyReturns422() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST",
                "/v1/payment_intents/123/confirm");
        request.addHeader("Idempotency-Key", "key-reused");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq("idem:key-reused"), anyString(), any(Duration.class)))
                .thenReturn(false);
        String differentHash = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        String cachedBody = Base64.getEncoder().encodeToString("{}".getBytes());
        when(valueOps.get("idem:key-reused")).thenReturn("COMPLETED:" + differentHash + ":200:" + cachedBody);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(response.getContentAsString()).contains("idempotency_key_reused");
        verifyNoInteractions(filterChain);
        verify(metrics).incrementIdempotencyConflict();
    }
}
