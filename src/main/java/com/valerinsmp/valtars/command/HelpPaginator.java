/*
 * vAltars - Ritual altars for ValerinSMP.
 * Copyright (c) 2025 thangks
 * Licensed under the MIT License.
 */
package com.valerinsmp.valtars.command;

import java.util.List;

public final class HelpPaginator {
    private HelpPaginator() { }

    public static <T> Page<T> page(List<T> entries, int requestedPage, int pageSize) {
        int safeSize = Math.max(1, pageSize);
        int totalPages = Math.max(1, (entries.size() + safeSize - 1) / safeSize);
        if (requestedPage < 1 || requestedPage > totalPages) {
            return new Page<>(requestedPage, totalPages, List.of(), false);
        }
        int from = (requestedPage - 1) * safeSize;
        return new Page<>(requestedPage, totalPages,
                entries.subList(from, Math.min(entries.size(), from + safeSize)), true);
    }

    public record Page<T>(int number, int totalPages, List<T> entries, boolean valid) { }
}
