package com.hmdp.integration;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled("Template for local Docker-based integration tests. Enable when Docker is available.")
@Testcontainers
class MySqlContainerTemplateTest {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("hmdp")
            .withUsername("root")
            .withPassword("123456");

    @Test
    void mysqlContainerCanStart() {
        assertTrue(mysql.isRunning());
    }
}
