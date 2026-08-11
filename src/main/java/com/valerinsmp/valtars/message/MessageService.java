/*
 * vAltars - Ritual altars for ValerinSMP.
 * Copyright (c) 2025 thangks
 * Licensed under the MIT License.
 */
package com.valerinsmp.valtars.message;

import com.valerinsmp.valtars.VAltarsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MessageService {
    private static final String DEFAULT_PREFIX =
            "<dark_gray>[</dark_gray><primary>vAltars</primary><dark_gray>]</dark_gray> <reset>";
    private static final String LEGACY_DEFAULT_PREFIX =
            "<primary><bold>vAltars</bold></primary> <dark_gray>»</dark_gray> ";
    private static final Pattern LEGACY_HEX = Pattern.compile("(?i)&#([0-9a-f]{6})");
    private static final Pattern LEGACY_PLACEHOLDER = Pattern.compile("%([a-zA-Z0-9_-]+)%");
    private static final Pattern EMOJI = Pattern.compile("<emoji:([a-zA-Z0-9_-]+)>");
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final VAltarsPlugin plugin;
    private Snapshot snapshot;

    public MessageService(VAltarsPlugin plugin, String language) {
        this.plugin = plugin;
        this.snapshot = load(language);
    }

    public Snapshot validateReload(String language) {
        return load(language);
    }

    public void apply(Snapshot candidate) {
        snapshot = candidate;
    }

    public Component component(String key, TagResolver... resolvers) {
        return render(snapshot.configuration().getString(key, key), false, resolvers);
    }

    public List<Component> lines(String key, TagResolver... resolvers) {
        List<String> raw = snapshot.configuration().isList(key)
                ? snapshot.configuration().getStringList(key)
                : List.of(snapshot.configuration().getString(key, key));
        List<Component> result = new ArrayList<>(raw.size());
        for (String line : raw) result.add(line == null || line.isEmpty() ? Component.empty() : render(line, false, resolvers));
        return result;
    }

    public void send(CommandSender audience, String key, TagResolver... resolvers) {
        sendComponent(audience, componentFor(audience, key, resolvers));
    }

    public void sendLines(CommandSender audience, String key, TagResolver... resolvers) {
        if (audience instanceof ConsoleCommandSender) {
            List<String> raw = snapshot.configuration().isList(key)
                    ? snapshot.configuration().getStringList(key)
                    : List.of(snapshot.configuration().getString(key, key));
            for (String line : raw) sendComponent(audience, render(line, true, resolvers));
            return;
        }
        for (Component line : lines(key, resolvers)) sendComponent(audience, line);
    }

    public String text(String key, String fallback) {
        return snapshot.configuration().getString(key, fallback);
    }

    private Component componentFor(CommandSender audience, String key, TagResolver... resolvers) {
        return render(snapshot.configuration().getString(key, key), audience instanceof ConsoleCommandSender, resolvers);
    }

    private Component render(String raw, boolean console, TagResolver... resolvers) {
        String normalized = normalize(raw == null ? "" : raw, console);
        TagResolver prefix = Placeholder.component("prefix", console ? Component.text("vAltars: ") : snapshot.prefix());
        try {
            return MINI_MESSAGE.deserialize(normalized, TagResolver.resolver(prefix, TagResolver.resolver(resolvers)));
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Invalid MiniMessage; sending plain text: " + exception.getMessage());
            return Component.text(MINI_MESSAGE.stripTags(normalized));
        }
    }

    private Snapshot load(String language) {
        String fileName = "language/messages_" + language + ".yml";
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) plugin.saveResource(fileName, false);
        YamlConfiguration current = YamlConfiguration.loadConfiguration(file);
        mergeMissingDefaults(current, file, fileName);

        Map<String, String> emojis = new LinkedHashMap<>();
        var emojiSection = current.getConfigurationSection("emojis");
        if (emojiSection != null) {
            for (String key : emojiSection.getKeys(false)) emojis.put(key, emojiSection.getString(key, ""));
        }
        String prefixRaw = current.getString("prefix", DEFAULT_PREFIX);
        Component prefix = MINI_MESSAGE.deserialize(normalize(prefixRaw, false));
        Snapshot candidate = new Snapshot(current, Map.copyOf(emojis), prefix);
        Snapshot previous = snapshot;
        snapshot = candidate;
        try {
            for (String key : current.getKeys(true)) {
                if (current.isString(key)) render(current.getString(key), false);
                if (current.isList(key)) for (String line : current.getStringList(key)) render(line, false);
            }
        } finally {
            snapshot = previous == null ? candidate : previous;
        }
        return candidate;
    }

    private void mergeMissingDefaults(YamlConfiguration current, File file, String resourcePath) {
        try (InputStream stream = plugin.getResource(resourcePath)) {
            if (stream == null) return;
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            boolean changed = migrateLegacyPrefix(current, defaults);
            for (String key : defaults.getKeys(true)) {
                if (!defaults.isConfigurationSection(key) && !current.isSet(key)) {
                    current.set(key, defaults.get(key));
                    changed = true;
                }
            }
            if (changed) current.save(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not merge message defaults", exception);
        }
    }

    static boolean migrateLegacyPrefix(YamlConfiguration current, YamlConfiguration defaults) {
        if (!LEGACY_DEFAULT_PREFIX.equals(current.getString("prefix"))) return false;
        current.set("prefix", defaults.getString("prefix", DEFAULT_PREFIX));
        return true;
    }

    private String normalize(String raw, boolean console) {
        String value = legacyToMini(raw)
                .replace("<primary>", "<#FFD166>").replace("</primary>", "</#FFD166>")
                .replace("<secondary>", "<white>").replace("</secondary>", "</white>")
                .replace("<muted>", "<gray>").replace("</muted>", "</gray>")
                .replace("<success>", "<#00FB9A>").replace("</success>", "</#00FB9A>")
                .replace("<warning>", "<#FFC43B>").replace("</warning>", "</#FFC43B>")
                .replace("<error>", "<#FF3300>").replace("</error>", "</#FF3300>");
        Matcher placeholders = LEGACY_PLACEHOLDER.matcher(value);
        value = placeholders.replaceAll(match -> "<" + match.group(1).toLowerCase(Locale.ROOT) + ">");

        Matcher emoji = EMOJI.matcher(value);
        StringBuffer result = new StringBuffer();
        while (emoji.find()) {
            String replacement = console ? "" : snapshot == null ? "" : snapshot.emojis().getOrDefault(emoji.group(1), "");
            emoji.appendReplacement(result, Matcher.quoteReplacement(MINI_MESSAGE.escapeTags(replacement)));
        }
        emoji.appendTail(result);
        return result.toString();
    }

    private String legacyToMini(String raw) {
        Matcher matcher = LEGACY_HEX.matcher(raw);
        StringBuffer hex = new StringBuffer();
        while (matcher.find()) matcher.appendReplacement(hex, "<#" + matcher.group(1).toLowerCase(Locale.ROOT) + ">");
        matcher.appendTail(hex);
        return hex.toString()
                .replace("&0", "<black>").replace("&1", "<dark_blue>").replace("&2", "<dark_green>")
                .replace("&3", "<dark_aqua>").replace("&4", "<dark_red>").replace("&5", "<dark_purple>")
                .replace("&6", "<gold>").replace("&7", "<gray>").replace("&8", "<dark_gray>")
                .replace("&9", "<blue>").replace("&a", "<green>").replace("&b", "<aqua>")
                .replace("&c", "<red>").replace("&d", "<light_purple>").replace("&e", "<yellow>")
                .replace("&f", "<white>").replace("&k", "<obfuscated>").replace("&l", "<bold>")
                .replace("&m", "<strikethrough>").replace("&n", "<underlined>").replace("&o", "<italic>")
                .replace("&r", "<reset>");
    }

    private void sendComponent(CommandSender audience, Component component) {
        if (audience instanceof ConsoleCommandSender) {
            audience.sendMessage(PlainTextComponentSerializer.plainText().serialize(component));
        } else {
            audience.sendMessage(component);
        }
    }

    public record Snapshot(YamlConfiguration configuration, Map<String, String> emojis, Component prefix) { }
}
