/*
 * vAltars - Ritual altars for ValerinSMP.
 * Copyright (c) 2025 thangks
 * Licensed under the MIT License.
 */
package com.valerinsmp.valtars.command;

import com.valerinsmp.valtars.VAltarsPlugin;
import com.valerinsmp.valtars.domain.BlockKey;
import com.valerinsmp.valtars.service.AltarManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class VAltarsCommand implements CommandExecutor, TabCompleter {
    private static final TextColor PRIMARY = TextColor.fromHexString("#FFD166");
    private static final TextColor ERROR = TextColor.fromHexString("#FF3300");
    private final VAltarsPlugin plugin;

    public VAltarsCommand(VAltarsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean admin = command.getName().equalsIgnoreCase("valtarsadmin");
        if (args.length == 0 || is(args[0], "help", "ayuda", "?")) {
            help(sender, admin, args.length > 1 ? args[1] : null);
            return true;
        }
        if (is(args[0], "about", "info", "acerca")) {
            about(sender);
            return true;
        }
        if (!admin) {
            plugin.messages().send(sender, "general.unknown-command");
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload", "recargar" -> reload(sender);
            case "create" -> create(sender, args);
            case "delete" -> delete(sender, args);
            case "list" -> list(sender);
            case "gui", "menu", "altares" -> gui(sender);
            case "wand" -> wand(sender);
            case "edit" -> edit(sender, args);
            default -> {
                plugin.messages().send(sender, "general.unknown-command");
                yield true;
            }
        };
    }

    private boolean reload(CommandSender sender) {
        if (!require(sender, "valtars.command.reload")) return true;
        plugin.messages().send(sender, plugin.reloadRuntime() ? "general.reloaded" : "general.reload-failed");
        return true;
    }

    private boolean create(CommandSender sender, String[] args) {
        if (!require(sender, "valtars.command.create")) return true;
        if (args.length < 2) return usage(sender, "/valtarsadmin create <nombre>");
        result(sender, plugin.altars().create(args[1]), args[1], "altar-commands.created");
        return true;
    }

    private boolean delete(CommandSender sender, String[] args) {
        if (!require(sender, "valtars.command.delete")) return true;
        if (args.length < 2) return usage(sender, "/valtarsadmin delete <nombre>");
        result(sender, plugin.altars().delete(args[1]), args[1], "altar-commands.deleted");
        return true;
    }

    private boolean list(CommandSender sender) {
        if (!require(sender, "valtars.command.list")) return true;
        plugin.messages().send(sender, "altar-commands.list-header");
        if (plugin.altars().names().isEmpty()) plugin.messages().send(sender, "altar-commands.list-empty");
        else plugin.altars().names().forEach(name -> plugin.messages().send(sender, "altar-commands.list-entry",
                Placeholder.unparsed("name", name)));
        return true;
    }

    private boolean gui(CommandSender sender) {
        if (!require(sender, "valtars.command.teleport")) return true;
        Player player = player(sender);
        if (player != null) plugin.browser().open(player, 1);
        return true;
    }

    private boolean wand(CommandSender sender) {
        if (!require(sender, "valtars.command.wand")) return true;
        Player player = player(sender);
        if (player != null) plugin.wands().give(player);
        return true;
    }

    private boolean edit(CommandSender sender, String[] args) {
        if (!require(sender, "valtars.command.edit")) return true;
        if (args.length < 4) return usage(sender, "/valtarsadmin edit <altar> <set|add|remove> ...");
        String altar = args[1];
        String operation = args[2].toLowerCase(Locale.ROOT);
        String target = args[3].toLowerCase(Locale.ROOT);
        Player player;

        if (operation.equals("set") && target.equals("mob")) {
            if (args.length < 5) return usage(sender, "/valtarsadmin edit <altar> set mob <nombre>");
            result(sender, plugin.altars().setBoss(altar, args[4]), altar, "altar-commands.boss-set");
            return true;
        }
        player = player(sender);
        if (player == null) return true;

        if (operation.equals("set") && target.equals("center")) {
            BlockKey selection = selection(player);
            if (selection != null) result(sender, plugin.altars().setCenter(altar, selection), altar,
                    "altar-commands.center-set");
            return true;
        }
        if (operation.equals("add") && target.equals("itemcenter")) {
            int amount = 1;
            if (args.length > 4) {
                try {
                    amount = Integer.parseInt(args[4]);
                } catch (NumberFormatException exception) {
                    plugin.messages().send(sender, "altar-commands.error-invalid-number");
                    return true;
                }
            }
            ItemStack hand = hand(player);
            if (hand != null) result(sender, plugin.altars().setActivationItem(altar, hand, amount), altar,
                    "altar-commands.center-item-set");
            return true;
        }
        if (operation.equals("add") && target.equals("pedestal")) {
            BlockKey selection = selection(player);
            if (selection != null) result(sender, plugin.altars().addPedestal(altar, selection), altar,
                    "altar-commands.pedestal-added");
            return true;
        }
        if (operation.equals("add") && target.equals("item")) {
            if (args.length < 5) return usage(sender, "/valtarsadmin edit <altar> add item <cantidad>");
            int amount;
            try {
                amount = Integer.parseInt(args[4]);
            } catch (NumberFormatException exception) {
                plugin.messages().send(sender, "altar-commands.error-invalid-number");
                return true;
            }
            ItemStack hand = hand(player);
            if (hand != null) result(sender, plugin.altars().addRequirement(altar, hand, amount), altar,
                    "altar-commands.required-item-set");
            return true;
        }
        if (operation.equals("remove") && target.equals("pedestal")) {
            AltarManager.MutationResult result;
            if (args.length > 4 && args[4].equalsIgnoreCase("all")) result = plugin.altars().clearPedestals(altar);
            else {
                BlockKey selection = selection(player);
                if (selection == null) return true;
                result = plugin.altars().removePedestal(altar, selection);
            }
            result(sender, result, altar, "altar-commands.pedestal-removed");
            return true;
        }
        if (operation.equals("remove") && target.equals("item")) {
            AltarManager.MutationResult result;
            if (args.length > 4 && args[4].equalsIgnoreCase("all")) result = plugin.altars().clearRequirements(altar);
            else {
                ItemStack hand = hand(player);
                if (hand == null) return true;
                result = plugin.altars().removeRequirement(altar, hand);
            }
            result(sender, result, altar, "altar-commands.required-item-removed");
            return true;
        }
        return usage(sender, "/valtarsadmin edit <altar> <set|add|remove> ...");
    }

    private void help(CommandSender sender, boolean admin, String rawPage) {
        int pageNumber;
        try {
            pageNumber = rawPage == null ? 1 : Integer.parseInt(rawPage);
        } catch (NumberFormatException exception) {
            pageNumber = -1;
        }
        List<HelpEntry> visible = entries(admin).stream()
                .filter(entry -> entry.permission().isBlank() || PermissionPolicy.has(sender, entry.permission())).toList();
        HelpPaginator.Page<HelpEntry> page = HelpPaginator.page(visible, pageNumber, 8);
        if (!page.valid()) {
            plugin.messages().send(sender, "help.invalid-page",
                    Placeholder.unparsed("pages", String.valueOf(page.totalPages())));
            return;
        }

        plugin.messages().send(sender, "help.header",
                Placeholder.unparsed("page", String.valueOf(page.number())),
                Placeholder.unparsed("pages", String.valueOf(page.totalPages())));
        for (HelpEntry entry : page.entries()) send(sender, render(entry));
        send(sender, navigation(admin, page.number(), page.totalPages()));
        plugin.messages().send(sender, "help.footer");
    }

    private void about(CommandSender sender) {
        plugin.messages().sendLines(sender, "about.lines",
                Placeholder.unparsed("version", plugin.getPluginMeta().getVersion()));
        String url = plugin.messages().text("about.repository-url", "https://github.com/ValerinSMP/VtAlters");
        Component link = plugin.messages().component("about.repository-label")
                .hoverEvent(HoverEvent.showText(plugin.messages().component("about.repository-hover")))
                .clickEvent(ClickEvent.openUrl(url));
        send(sender, link);
    }

    private List<HelpEntry> entries(boolean admin) {
        List<HelpEntry> entries = new ArrayList<>();
        entries.add(new HelpEntry("/valtars help [página]", "help.descriptions.help", "", "/valtars help "));
        entries.add(new HelpEntry("/valtars about", "help.descriptions.about", "", "/valtars about"));
        if (!admin) return entries;
        entries.add(new HelpEntry("/valtarsadmin reload", "help.descriptions.reload", "valtars.command.reload", "/valtarsadmin reload"));
        entries.add(new HelpEntry("/valtarsadmin create <nombre>", "help.descriptions.create", "valtars.command.create", "/valtarsadmin create "));
        entries.add(new HelpEntry("/valtarsadmin delete <nombre>", "help.descriptions.delete", "valtars.command.delete", "/valtarsadmin delete "));
        entries.add(new HelpEntry("/valtarsadmin list", "help.descriptions.list", "valtars.command.list", "/valtarsadmin list"));
        entries.add(new HelpEntry("/valtarsadmin gui", "help.descriptions.gui", "valtars.command.teleport", "/valtarsadmin gui"));
        entries.add(new HelpEntry("/valtarsadmin wand", "help.descriptions.wand", "valtars.command.wand", "/valtarsadmin wand"));
        entries.add(new HelpEntry("/valtarsadmin edit <altar> set center", "help.descriptions.set-center", "valtars.command.edit", "/valtarsadmin edit "));
        entries.add(new HelpEntry("/valtarsadmin edit <altar> set mob <mob>", "help.descriptions.set-mob", "valtars.command.edit", "/valtarsadmin edit "));
        entries.add(new HelpEntry("/valtarsadmin edit <altar> add itemcenter [cantidad]", "help.descriptions.itemcenter", "valtars.command.edit", "/valtarsadmin edit "));
        entries.add(new HelpEntry("/valtarsadmin edit <altar> add pedestal", "help.descriptions.add-pedestal", "valtars.command.edit", "/valtarsadmin edit "));
        entries.add(new HelpEntry("/valtarsadmin edit <altar> add item <cantidad>", "help.descriptions.add-item", "valtars.command.edit", "/valtarsadmin edit "));
        entries.add(new HelpEntry("/valtarsadmin edit <altar> remove pedestal [all]", "help.descriptions.remove-pedestal", "valtars.command.edit", "/valtarsadmin edit "));
        entries.add(new HelpEntry("/valtarsadmin edit <altar> remove item [all]", "help.descriptions.remove-item", "valtars.command.edit", "/valtarsadmin edit "));
        return entries;
    }

    private Component render(HelpEntry entry) {
        Component description = plugin.messages().component(entry.descriptionKey());
        return Component.text()
                .append(Component.text(entry.command(), PRIMARY)
                        .hoverEvent(HoverEvent.showText(description.append(Component.newline())
                                .append(Component.text(entry.permission().isBlank() ? "Público" : entry.permission(), NamedTextColor.DARK_GRAY))))
                        .clickEvent(ClickEvent.suggestCommand(entry.suggestion())))
                .append(Component.text(" — ", NamedTextColor.DARK_GRAY))
                .append(description)
                .build();
    }

    private Component navigation(boolean admin, int page, int totalPages) {
        String root = admin ? "/valtarsadmin help " : "/valtars help ";
        Component previous = page > 1
                ? Component.text("«", PRIMARY).clickEvent(ClickEvent.runCommand(root + (page - 1)))
                : Component.text("«", NamedTextColor.DARK_GRAY);
        Component next = page < totalPages
                ? Component.text("»", PRIMARY).clickEvent(ClickEvent.runCommand(root + (page + 1)))
                : Component.text("»", NamedTextColor.DARK_GRAY);
        return Component.text().append(previous)
                .append(Component.text("  " + page + "/" + totalPages + "  ", NamedTextColor.GRAY))
                .append(next).build();
    }

    private boolean require(CommandSender sender, String permission) {
        if (PermissionPolicy.has(sender, permission) || PermissionPolicy.has(sender, "valtars.admin")) return true;
        plugin.messages().send(sender, "general.no-permission");
        return false;
    }

    private Player player(CommandSender sender) {
        if (sender instanceof Player player) return player;
        plugin.messages().send(sender, "general.not-a-player");
        return null;
    }

    private ItemStack hand(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item != null && !item.getType().isAir()) return item;
        plugin.messages().send(player, "altar-commands.error-no-item-in-hand");
        return null;
    }

    private BlockKey selection(Player player) {
        BlockKey selection = plugin.wands().selection(player);
        if (selection == null) plugin.messages().send(player, "wand.error-no-selection");
        return selection;
    }

    private void result(CommandSender sender, AltarManager.MutationResult result, String altar, String successKey) {
        String key = switch (result) {
            case SAVED -> successKey;
            case NOT_FOUND -> "altar-commands.error-no-altar";
            case ALREADY_EXISTS -> "altar-commands.error-altar-exists";
            case BUSY -> "altar-commands.error-altar-busy";
            case CONFLICT -> "altar-commands.error-conflict";
            case INVALID -> "altar-commands.error-invalid";
        };
        plugin.messages().send(sender, key, Placeholder.unparsed("name", altar));
    }

    private boolean usage(CommandSender sender, String usage) {
        plugin.messages().send(sender, "general.usage", Placeholder.unparsed("usage", usage));
        return true;
    }

    private void send(CommandSender sender, Component component) {
        if (sender instanceof ConsoleCommandSender) {
            sender.sendMessage(PlainTextComponentSerializer.plainText().serialize(component));
        } else {
            sender.sendMessage(component);
        }
    }

    private boolean is(String value, String... options) {
        for (String option : options) if (value.equalsIgnoreCase(option)) return true;
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean admin = command.getName().equalsIgnoreCase("valtarsadmin");
        if (args.length == 1) {
            List<String> roots = admin
                    ? List.of("help", "about", "reload", "create", "delete", "list", "gui", "wand", "edit")
                    : List.of("help", "about");
            return partial(args[0], roots.stream().filter(root -> visibleRoot(sender, root)).toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("help")) return partial(args[1], List.of("1", "2"));
        if (!admin) return List.of();
        if (args.length == 2 && (args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("edit"))) {
            return partial(args[1], new ArrayList<>(plugin.altars().names()));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("edit")) return partial(args[2], List.of("set", "add", "remove"));
        if (args.length == 4 && args[0].equalsIgnoreCase("edit")) {
            return switch (args[2].toLowerCase(Locale.ROOT)) {
                case "set" -> partial(args[3], List.of("center", "mob"));
                case "add" -> partial(args[3], List.of("pedestal", "item", "itemcenter"));
                case "remove" -> partial(args[3], List.of("pedestal", "item"));
                default -> List.of();
            };
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("edit") && args[2].equalsIgnoreCase("remove")) {
            return partial(args[4], List.of("all"));
        }
        return List.of();
    }

    private boolean visibleRoot(CommandSender sender, String root) {
        return switch (root) {
            case "reload" -> requireSilently(sender, "valtars.command.reload");
            case "create" -> requireSilently(sender, "valtars.command.create");
            case "delete" -> requireSilently(sender, "valtars.command.delete");
            case "list" -> requireSilently(sender, "valtars.command.list");
            case "gui" -> requireSilently(sender, "valtars.command.teleport");
            case "wand" -> requireSilently(sender, "valtars.command.wand");
            case "edit" -> requireSilently(sender, "valtars.command.edit");
            default -> true;
        };
    }

    private boolean requireSilently(CommandSender sender, String permission) {
        return PermissionPolicy.has(sender, permission) || PermissionPolicy.has(sender, "valtars.admin");
    }

    private List<String> partial(String token, List<String> values) {
        return StringUtil.copyPartialMatches(token, values, new ArrayList<>());
    }

    private record HelpEntry(String command, String descriptionKey, String permission, String suggestion) { }
}
