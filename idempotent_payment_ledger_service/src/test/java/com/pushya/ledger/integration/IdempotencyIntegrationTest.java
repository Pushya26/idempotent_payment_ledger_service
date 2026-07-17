package com.pushya.ledger.integration;

import com.pushya.ledger.domain.LedgerEntry;
import com.pushya.ledger.domain.LedgerEntryType;
import com.pushya.ledger.domain.PaymentIntent;
import com.pushya.ledger.domain.PaymentIntentStatus;
import com.pushya.ledger.repository.LedgerEntryRepository;
import com.pushya.ledger.repository.PaymentIntentRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=none",
        "ledger.idempotency.claim-ttl-seconds=30",
        "ledger.idempotency.result-ttl-hours=24"
})
class IdempotencyIntegrationTest {

    static final boolean DOCKER_AVAILABLE = DockerClientFactory.instance().isDockerAvailable();
    static PostgreSQLContainer<?> postgres;
    static GenericContainer<?> redis;

    static {
        if (DOCKER_AVAILABLE) {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("ledger")
                    .withUsername("ledger")
                    .withPassword("ledger");
            redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
            postgres.start();
            redis.start();
        }
    }

    @BeforeAll
    static void checkDocker() {
        Assumptions.assumeTrue(DOCKER_AVAILABLE, "Docker is required for Testcontainers integration tests");
    }

    @AfterAll
    static void stopContainers() {
        if (postgres != null && postgres.isRunning()) {
            postgres.stop();
        }
        if (redis != null && redis.isRunning()) {
            redis.stop();
        }
    }

    @LocalServerPort
    int port;

    @Autowired
    PaymentIntentRepository paymentIntentRepository;

    @Autowired
    LedgerEntryRepository ledgerEntryRepository;

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", () -> redis.getFirstMappedPort());
    }

    @Test
    void fiftyConcurrentConfirms_settleExactlyOnce() throws Exception {
        PaymentIntent paymentIntent = paymentIntentRepository.save(new PaymentIntent(UUID.randomUUID(), 150_000L, "INR",
                PaymentIntentStatus.REQUIRES_CONFIRMATION, Instant.now(), Instant.now()));
        String idempotencyKey = UUID.randomUUID().toString();

        HttpClient client = HttpClient.newHttpClient();
        CountDownLatch ready = new CountDownLatch(50);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<HttpResponse<String>>> futures = new ArrayList<>();

        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 50; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:" + port + "/v1/payment_intents/" + paymentIntent.getId() + "/confirm"))
                            .header("Idempotency-Key", idempotencyKey)
                            .POST(HttpRequest.BodyPublishers.noBody())
                            .build();
                    return client.send(request, HttpResponse.BodyHandlers.ofString());
                }));
            }
            ready.await();
            go.countDown();

            List<HttpResponse<String>> responses = new ArrayList<>();
            for (var future : futures) {
                responses.add(future.get(10, TimeUnit.SECONDS));
            }

            long success = responses.stream().filter(r -> r.statusCode() == 200).count();
            long conflict = responses.stream().filter(r -> r.statusCode() == 409).count();
            assertThat(success + conflict).isEqualTo(50);
            assertThat(success).isGreaterThanOrEqualTo(1);

            Set<String> distinctBodies = responses.stream()
                    .filter(r -> r.statusCode() == 200)
                    .map(HttpResponse::body)
                    .collect(Collectors.toSet());
            assertThat(distinctBodies).hasSize(1);

            List<LedgerEntry> entries = ledgerEntryRepository.findByPaymentIntentId(paymentIntent.getId());
            assertThat(entries).hasSize(2);
            long debits = entries.stream().filter(e -> e.getType() == LedgerEntryType.DEBIT)
                    .mapToLong(LedgerEntry::getAmountMinor).sum();
            long credits = entries.stream().filter(e -> e.getType() == LedgerEntryType.CREDIT)
                    .mapToLong(LedgerEntry::getAmountMinor).sum();
            assertThat(debits).isEqualTo(credits).isEqualTo(150_000L);
        }
    }
}
