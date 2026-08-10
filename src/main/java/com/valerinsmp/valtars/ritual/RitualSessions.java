/*
 * vAltars - Ritual altars for ValerinSMP.
 * Copyright (c) 2025 thangks
 * Licensed under the MIT License.
 */
package com.valerinsmp.valtars.ritual;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class RitualSessions {
    private final Map<String, RitualSession> active = new LinkedHashMap<>();

    public boolean begin(RitualSession session) {
        return active.putIfAbsent(key(session.altarName()), session) == null;
    }

    public RitualSession get(String altarName) {
        return active.get(key(altarName));
    }

    public RitualSession remove(String altarName) {
        return active.remove(key(altarName));
    }

    public boolean contains(String altarName) {
        return active.containsKey(key(altarName));
    }

    public Collection<RitualSession> snapshot() {
        return new ArrayList<>(active.values());
    }

    private String key(String altarName) {
        return altarName.toLowerCase(Locale.ROOT);
    }
}
