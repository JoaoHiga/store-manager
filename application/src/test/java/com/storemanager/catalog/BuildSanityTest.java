package com.storemanager.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Proves the {@code application} module's test harness (JUnit 5 + AssertJ,
 * zero Spring dependency) actually runs. Replaced by real domain tests
 * starting in the Category/Product work units (tasks.md Phase 2/3).
 */
class BuildSanityTest {

    @Test
    void applicationModuleTestHarnessRuns() {
        assertThat(1 + 1).isEqualTo(2);
    }
}
