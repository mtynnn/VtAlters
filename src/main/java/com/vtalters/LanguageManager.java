/*
 * VtAlters - A plugin for summoning bosses via altars.
 * Copyright (c) 2025 thangks
 *
 * Licensed under the MIT License.
 * See the LICENSE file in the root of this project for more information.
 */

package com.vtalters;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

public class LanguageManager {

    private final VtAlters plugin;
    private FileConfiguration langConfig;
    private String prefix;

    public LanguageManager(VtAlters plugin) {
        this.plugin = plugin;
        loadMessages();
    }

    public void loadMessages() {
        String lang = plugin.getConfig().getString("language", "en");
        String prefixConfig = plugin.getConfig().getString("messages.prefix", "&e&lVtAlters &8&l»&r ");
        prefix = ChatColor.translateAlternateColorCodes('&', prefixConfig);

        File langFolder = new File(plugin.getDataFolder(), "language");
        if (!langFolder.exists()) {
            langFolder.mkdirs();
        }

        File langFile = new File(langFolder, "messages_" + lang + ".yml");
        String resourcePath = "language/messages_" + lang + ".yml";

        if (!langFile.exists()) {
            try {
                plugin.saveResource(resourcePath, false);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().log(Level.SEVERE, "ERROR: Cannot find language file '" + resourcePath + "' in the plugin JAR.");
            }
        }
        
        langConfig = YamlConfiguration.loadConfiguration(langFile);

        try (InputStream defLangStream = plugin.getResource(resourcePath)) {
            if (defLangStream != null) {
                langConfig.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defLangStream, StandardCharsets.UTF_8)));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getMessage(String path) {
        return ChatColor.translateAlternateColorCodes('&', langConfig.getString(path, "&cMissing message: " + path));
    }

    public void sendMessage(CommandSender sender, String path) {
        sender.sendMessage(prefix + getMessage(path));
    }

    public void sendMessage(CommandSender sender, String path, String... replacements) {
        String message = getMessage(path);
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                message = message.replace(replacements[i], replacements[i + 1]);
            }
        }
        sender.sendMessage(prefix + message);
    }
    
    public void sendRawMessage(CommandSender sender, String path, String... replacements) {
        String message = getMessage(path);
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                message = message.replace(replacements[i], replacements[i + 1]);
            }
        }
        sender.sendMessage(message);
    }
}
