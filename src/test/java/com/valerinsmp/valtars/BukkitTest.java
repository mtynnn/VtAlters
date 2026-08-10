package com.valerinsmp.valtars;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.mockbukkit.mockbukkit.MockBukkit;

public abstract class BukkitTest {
    @BeforeAll
    static void startMockServer() {
        MockBukkit.mock();
    }

    @AfterAll
    static void stopMockServer() {
        MockBukkit.unmock();
    }
}
