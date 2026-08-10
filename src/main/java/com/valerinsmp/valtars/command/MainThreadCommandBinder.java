/*
 * vAltars - Ritual altars for ValerinSMP.
 * Copyright (c) 2025 thangks
 * Licensed under the MIT License.
 */
package com.valerinsmp.valtars.command;

import com.valerinsmp.valtars.VAltarsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class MainThreadCommandBinder {
    private final VAltarsPlugin plugin;

    public MainThreadCommandBinder(VAltarsPlugin plugin) {
        this.plugin = plugin;
    }

    public void bind(String commandName, CommandExecutor executor, TabCompleter completer) {
        PluginCommand command = plugin.getCommand(commandName);
        if (command == null) throw new IllegalStateException("Command missing from plugin.yml: " + commandName);
        command.setExecutor((sender, invoked, label, args) -> {
            if (Bukkit.isPrimaryThread()) return executor.onCommand(sender, invoked, label, args);
            String[] copied = args.clone();
            Bukkit.getScheduler().runTask(plugin, () -> executor.onCommand(sender, invoked, label, copied));
            return true;
        });
        command.setTabCompleter((sender, invoked, alias, args) -> {
            if (Bukkit.isPrimaryThread()) return safeComplete(completer, sender, invoked, alias, args);
            CompletableFuture<List<String>> result = new CompletableFuture<>();
            String[] copied = args.clone();
            Bukkit.getScheduler().runTask(plugin,
                    () -> result.complete(safeComplete(completer, sender, invoked, alias, copied)));
            try {
                return result.get(2, TimeUnit.SECONDS);
            } catch (Exception exception) {
                plugin.getLogger().warning("Command completion timed out for /" + alias);
                return Collections.emptyList();
            }
        });
    }

    private List<String> safeComplete(TabCompleter completer, org.bukkit.command.CommandSender sender,
                                      org.bukkit.command.Command command, String alias, String[] args) {
        try {
            List<String> result = completer.onTabComplete(sender, command, alias, args);
            return result == null ? Collections.emptyList() : List.copyOf(result);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Command completion failed for /" + alias + ": " + exception.getMessage());
            return Collections.emptyList();
        }
    }
}
