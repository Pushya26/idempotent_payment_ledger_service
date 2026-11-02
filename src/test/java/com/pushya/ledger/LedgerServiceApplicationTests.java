package com.pushya.ledger;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class LedgerServiceApplicationTests {

    @Test
    void mainClassExists() {
        // Verifies that the application entry point is present on the classpath
        assertThat(LedgerServiceApplication.class).isNotNull();
    }
}
