/*
 * vAltars - Ritual altars for ValerinSMP.
 * Copyright (c) 2025 thangks
 * Licensed under the MIT License.
 */
package com.valerinsmp.valtars.domain;

import java.util.Objects;

public record ItemRequirement(ItemSpec item, int amount) {
    public ItemRequirement {
        Objects.requireNonNull(item, "item");
        if (amount <= 0) throw new IllegalArgumentException("Required amount must be positive");
    }
}
