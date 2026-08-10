/*
 * vAltars - Ritual altars for ValerinSMP.
 * Copyright (c) 2025 thangks
 * Licensed under the MIT License.
 */
package com.valerinsmp.valtars.command;

import org.bukkit.command.CommandSender;

import java.util.function.Predicate;

public final class PermissionPolicy {
    private PermissionPolicy() { }

    public static boolean has(CommandSender sender, String permission) {
        return has(sender::hasPermission, permission);
    }

    static boolean has(Predicate<String> checker, String permission) {
        return checker.test(permission) || checker.test(permission.replaceFirst("^valtars", "vtalters"));
    }
}
