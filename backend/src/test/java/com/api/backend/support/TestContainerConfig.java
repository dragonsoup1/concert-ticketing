package com.api.backend.support;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Singleton containers shared across all Spring contexts in a single JVM run.
 * Using static fields here ensures Testcontainers Ryuk does not stop the containers
 * between context reloads.
 */
public final class TestContainerConfig {

    private TestContainerConfig() {}

    @SuppressWarnings("resource")
    public static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine").withReuse(true);

    @SuppressWarnings("resource")
    public static final GenericContainer<?> REDIS =
        new GenericContainer<>("redis:7-alpine").withExposedPorts(6379).withReuse(true);

    static {
        POSTGRES.start();
        REDIS.start();
        try {
            // Flush Redis so stale data from a previous withReuse(true) run doesn't bleed in
            REDIS.execInContainer("redis-cli", "FLUSHALL");
        } catch (Exception ignored) {}
    }
}
