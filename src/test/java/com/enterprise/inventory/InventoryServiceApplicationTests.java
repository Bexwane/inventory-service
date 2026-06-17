package com.enterprise.inventory;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Context loading integration tests for the Inventory Service.
 */
class InventoryServiceApplicationTests {

    @Test
    @Disabled("Requires a running PostgreSQL instance, which is not available in the CI/test environment without Testcontainers.")
    void contextLoads() {
    }

}
