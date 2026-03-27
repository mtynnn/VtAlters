/*
 * VtAlters - Plugin for summoning bosses via ritual altars.
 * Copyright (c) 2025 thangks
 *
 * Licensed under the MIT License.
 * See the LICENSE file in the root of this project for more information.
 */

package com.vtalters;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;

import java.util.*;

public class AltarCommand implements CommandExecutor, TabCompleter {

    private final VtAlters plugin;
    private final WandManager wandManager;
    private final LanguageManager lang;
    private final NexoHook nexo;

    public AltarCommand(VtAlters plugin) {
        this.plugin      = plugin;
        this.wandManager = plugin.getWandManager();
        this.lang        = plugin.getLanguageManager();
        this.nexo        = plugin.getNexoHook();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            lang.sendRawMessage(sender, "general.not-a-player");
            return true;
        }
        Player player = (Player) sender;

        // Warn admins if the plugin has detected configuration errors
        if (player.hasPermission("vtalters.admin") && plugin.getErrorHandler().hasErrors()) {
            lang.sendMessage(player, "general.plugin-has-errors",
                    "%reasons%", plugin.getErrorHandler().getErrorReasons());
        }

        if (args.length == 0) {
            sendHelpMessage(player, label);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create": handleCreate(player, args, label); break;
            case "delete": handleDelete(player, args, label); break;
            case "list":   handleList(player);                break;
            case "reload":
                if (!player.hasPermission("vtalters.command.reload")) {
                    lang.sendMessage(player, "general.no-permission"); return true;
                }
                plugin.reloadPlugin();
                lang.sendMessage(player, "general.reloaded");
                break;
            case "wand":
                if (!player.hasPermission("vtalters.command.wand")) {
                    lang.sendMessage(player, "general.no-permission"); return true;
                }
                wandManager.giveWand(player);
                break;
            case "edit":   handleEdit(player, args, label);   break;
            default:       sendHelpMessage(player, label);    break;
        }
        return true;
    }

    // =========================================================================
    // CREATE
    // =========================================================================

    private void handleCreate(Player player, String[] args, String label) {
        if (!player.hasPermission("vtalters.command.create")) {
            lang.sendMessage(player, "general.no-permission"); return;
        }
        if (args.length < 2) {
            lang.sendMessage(player, "general.usage", "%usage%", "/" + label + " create <nombre>"); return;
        }
        String altarName = args[1];
        FileConfiguration data = plugin.getDataManager().getConfig();
        if (data.isConfigurationSection("altars." + altarName)) {
            lang.sendMessage(player, "altar-commands.error-altar-exists", "%name%", altarName); return;
        }
        data.set("altars." + altarName + ".boss-name",           "DefaultBoss");
        data.set("altars." + altarName + ".center",              "not_set");
        data.set("altars." + altarName + ".central-item",        null);
        data.set("altars." + altarName + ".central-item-nexo-id", null);
        data.set("altars." + altarName + ".required-items",      new ArrayList<Map<String, Object>>());
        data.set("altars." + altarName + ".required-items-nexo", null);
        data.set("altars." + altarName + ".pedestal-locations",  new ArrayList<String>());
        saveAndReload();
        lang.sendMessage(player, "altar-commands.created", "%name%", altarName);
    }

    // =========================================================================
    // DELETE
    // =========================================================================

    private void handleDelete(Player player, String[] args, String label) {
        if (!player.hasPermission("vtalters.command.delete")) {
            lang.sendMessage(player, "general.no-permission"); return;
        }
        if (args.length < 2) {
            lang.sendMessage(player, "general.usage", "%usage%", "/" + label + " delete <nombre>"); return;
        }
        String altarName = args[1];
        FileConfiguration data = plugin.getDataManager().getConfig();
        if (!data.isConfigurationSection("altars." + altarName)) {
            lang.sendMessage(player, "altar-commands.error-no-altar", "%name%", altarName); return;
        }
        data.set("altars." + altarName, null);
        saveAndReload();
        lang.sendMessage(player, "altar-commands.deleted", "%name%", altarName);
    }

    // =========================================================================
    // LIST
    // =========================================================================

    private void handleList(Player player) {
        if (!player.hasPermission("vtalters.command.list")) {
            lang.sendMessage(player, "general.no-permission"); return;
        }
        FileConfiguration data = plugin.getDataManager().getConfig();
        lang.sendRawMessage(player, "altar-commands.list-header");
        if (!data.isConfigurationSection("altars")
                || data.getConfigurationSection("altars").getKeys(false).isEmpty()) {
            lang.sendRawMessage(player, "altar-commands.list-empty");
        } else {
            data.getConfigurationSection("altars").getKeys(false)
                    .forEach(name -> lang.sendRawMessage(player, "altar-commands.list-entry", "%name%", name));
        }
    }

    // =========================================================================
    // EDIT (dispatcher)
    // =========================================================================

    private void handleEdit(Player player, String[] args, String label) {
        if (!player.hasPermission("vtalters.command.edit")) {
            lang.sendMessage(player, "general.no-permission"); return;
        }
        if (args.length < 3) { sendHelpMessage(player, label); return; }

        String altarName = args[1];
        FileConfiguration data = plugin.getDataManager().getConfig();
        if (!data.isConfigurationSection("altars." + altarName)) {
            lang.sendMessage(player, "altar-commands.error-no-altar", "%name%", altarName); return;
        }

        String pathPrefix = "altars." + altarName + ".";
        switch (args[2].toLowerCase()) {
            case "set":    handleEditSet(player, args, data, pathPrefix, label);            break;
            case "add":    handleEditAdd(player, args, data, pathPrefix, altarName, label); break;
            case "remove": handleEditRemove(player, args, data, pathPrefix, altarName, label); break;
            default:       sendHelpMessage(player, label); break;
        }
    }

    // =========================================================================
    // EDIT SET
    // =========================================================================

    private void handleEditSet(Player player, String[] args, FileConfiguration data,
                                String pathPrefix, String label) {
        if (args.length < 4) { sendHelpMessage(player, label); return; }

        switch (args[3].toLowerCase()) {
            case "center": {
                Location sel = wandManager.getSelection(player);
                if (sel == null) { lang.sendMessage(player, "wand.error-no-selection"); return; }
                data.set(pathPrefix + "center", Altar.locationToString(sel.getBlock().getLocation()));
                lang.sendMessage(player, "altar-commands.center-set");
                saveAndReload();
                break;
            }
            case "mob": {
                if (args.length < 5) {
                    lang.sendMessage(player, "general.usage", "%usage%",
                            "/" + label + " edit " + args[1] + " set mob <nombre_mob>"); return;
                }
                data.set(pathPrefix + "boss-name", args[4]);
                lang.sendMessage(player, "altar-commands.boss-set", "%name%", args[1], "%mob%", args[4]);
                saveAndReload();
                break;
            }
            default: sendHelpMessage(player, label); break;
        }
    }

    // =========================================================================
    // EDIT ADD
    // =========================================================================

    private void handleEditAdd(Player player, String[] args, FileConfiguration data,
                                String pathPrefix, String altarName, String label) {
        if (args.length < 4) { sendHelpMessage(player, label); return; }

        switch (args[3].toLowerCase()) {

            // ── Activation (central) item ─────────────────────────────────
            case "itemcenter": {
                ItemStack inHand = player.getInventory().getItemInMainHand();
                if (inHand.getType().isAir()) {
                    lang.sendMessage(player, "altar-commands.error-no-item-in-hand"); return;
                }

                if (nexo.isAvailable() && nexo.isNexoItem(inHand)) {
                    // Store as Nexo ID — no serialized ItemStack needed
                    String nexoId = nexo.getNexoId(inHand);
                    data.set(pathPrefix + "central-item",         null);
                    data.set(pathPrefix + "central-item-nexo-id", nexoId);
                    lang.sendMessage(player, "altar-commands.center-item-set",
                            "%item_name%", "[Nexo] " + nexoId);
                } else {
                    // Store as plain Bukkit ItemStack
                    ItemStack template = inHand.clone();
                    template.setAmount(1);
                    data.set(pathPrefix + "central-item",         template.serialize());
                    data.set(pathPrefix + "central-item-nexo-id", null);
                    lang.sendMessage(player, "altar-commands.center-item-set",
                            "%item_name%", nexo.getDisplayName(inHand));
                }
                saveAndReload();
                break;
            }

            // ── Pedestal block ────────────────────────────────────────────
            case "pedestal": {
                Location sel = wandManager.getSelection(player);
                if (sel == null) { lang.sendMessage(player, "wand.error-no-selection"); return; }

                Location centerLoc = Altar.locationFromString(data.getString(pathPrefix + "center"));
                if (centerLoc == null) {
                    lang.sendMessage(player, "altar-commands.error-must-set-center"); return;
                }
                double maxRadius = plugin.getConfig().getDouble("altar.max-pedestal-radius", 10.0);
                if (!sel.getWorld().equals(centerLoc.getWorld()) || sel.distance(centerLoc) > maxRadius) {
                    lang.sendMessage(player, "altar-commands.error-pedestal-too-far",
                            "%radius%", String.valueOf(maxRadius)); return;
                }
                String locStr = Altar.locationToString(sel.getBlock().getLocation());
                List<String> locs = data.getStringList(pathPrefix + "pedestal-locations");
                if (locs.contains(locStr)) {
                    lang.sendMessage(player, "altar-commands.error-pedestal-already-exists"); return;
                }
                locs.add(locStr);
                data.set(pathPrefix + "pedestal-locations", locs);
                lang.sendMessage(player, "altar-commands.pedestal-added");
                saveAndReload();
                break;
            }

            // ── Required item ─────────────────────────────────────────────
            case "item": {
                if (args.length < 5) {
                    lang.sendMessage(player, "general.usage", "%usage%",
                            "/" + label + " edit " + altarName + " add item <cantidad>"); return;
                }
                ItemStack inHand = player.getInventory().getItemInMainHand();
                if (inHand.getType().isAir()) {
                    lang.sendMessage(player, "altar-commands.error-no-item-in-hand"); return;
                }
                int amount;
                try {
                    amount = Integer.parseInt(args[4]);
                    if (amount <= 0) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    lang.sendMessage(player, "altar-commands.error-invalid-number"); return;
                }

                int pedestalCount = data.getStringList(pathPrefix + "pedestal-locations").size();
                if (pedestalCount == 0) {
                    lang.sendMessage(player, "altar-commands.error-must-add-pedestals-first"); return;
                }

                // Calculate total already required (Bukkit + Nexo)
                int currentTotal = 0;
                for (Map<?, ?> m : data.getMapList(pathPrefix + "required-items")) {
                    if (m.get("amount") instanceof Integer) currentTotal += (Integer) m.get("amount");
                }
                org.bukkit.configuration.ConfigurationSection nexoSec =
                        data.getConfigurationSection(pathPrefix + "required-items-nexo");
                if (nexoSec != null) {
                    for (String k : nexoSec.getKeys(false)) currentTotal += nexoSec.getInt(k, 0);
                }

                if (currentTotal + amount > pedestalCount) {
                    lang.sendMessage(player, "altar-commands.error-items-exceed-pedestals",
                            "%total_required%", String.valueOf(currentTotal + amount),
                            "%pedestal_count%", String.valueOf(pedestalCount)); return;
                }

                if (nexo.isAvailable() && nexo.isNexoItem(inHand)) {
                    // Store under the required-items-nexo section
                    String nexoId = nexo.getNexoId(inHand);
                    int existing = data.getInt(pathPrefix + "required-items-nexo." + nexoId, 0);
                    data.set(pathPrefix + "required-items-nexo." + nexoId, existing + amount);
                    lang.sendMessage(player, "altar-commands.required-item-set",
                            "%amount%", String.valueOf(amount),
                            "%item_name%", "[Nexo] " + nexoId);
                } else {
                    // Store as serialized Bukkit ItemStack
                    ItemStack template = inHand.clone();
                    template.setAmount(1);
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("item",   template.serialize());
                    entry.put("amount", amount);
                    List<Map<?, ?>> list = data.getMapList(pathPrefix + "required-items");
                    list.add(entry);
                    data.set(pathPrefix + "required-items", list);
                    lang.sendMessage(player, "altar-commands.required-item-set",
                            "%amount%", String.valueOf(amount),
                            "%item_name%", nexo.getDisplayName(inHand));
                }
                saveAndReload();
                break;
            }

            default: sendHelpMessage(player, label); break;
        }
    }

    // =========================================================================
    // EDIT REMOVE
    // =========================================================================

    @SuppressWarnings("unchecked")
    private void handleEditRemove(Player player, String[] args, FileConfiguration data,
                                   String pathPrefix, String altarName, String label) {
        if (args.length < 4) { sendHelpMessage(player, label); return; }

        switch (args[3].toLowerCase()) {

            case "pedestal": {
                String pedestalPath = pathPrefix + "pedestal-locations";
                List<String> locs = data.getStringList(pedestalPath);

                if (args.length > 4 && args[4].equalsIgnoreCase("all")) {
                    if (locs.isEmpty()) {
                        lang.sendMessage(player, "altar-commands.error-no-pedestals-to-clear", "%name%", altarName); return;
                    }
                    data.set(pedestalPath, new ArrayList<String>());
                    lang.sendMessage(player, "altar-commands.pedestals-cleared", "%name%", altarName);
                    saveAndReload();
                    return;
                }

                Location sel = wandManager.getSelection(player);
                if (sel == null) { lang.sendMessage(player, "wand.error-no-selection"); return; }
                String locStr = Altar.locationToString(sel.getBlock().getLocation());
                if (!locs.contains(locStr)) {
                    lang.sendMessage(player, "altar-commands.error-pedestal-not-found"); return;
                }
                locs.remove(locStr);
                data.set(pedestalPath, locs);
                lang.sendMessage(player, "altar-commands.pedestal-removed");
                saveAndReload();
                break;
            }

            case "item": {
                if (args.length > 4 && args[4].equalsIgnoreCase("all")) {
                    boolean hadItems = !data.getMapList(pathPrefix + "required-items").isEmpty();
                    boolean hadNexo  = data.getConfigurationSection(pathPrefix + "required-items-nexo") != null;
                    if (!hadItems && !hadNexo) {
                        lang.sendMessage(player, "altar-commands.error-no-required-items-to-remove"); return;
                    }
                    data.set(pathPrefix + "required-items",      new ArrayList<>());
                    data.set(pathPrefix + "required-items-nexo", null);
                    lang.sendMessage(player, "altar-commands.required-items-cleared");
                    saveAndReload();
                    return;
                }

                ItemStack inHand = player.getInventory().getItemInMainHand();
                if (inHand.getType().isAir()) {
                    lang.sendMessage(player, "altar-commands.error-no-item-in-hand"); return;
                }

                if (nexo.isAvailable() && nexo.isNexoItem(inHand)) {
                    String nexoId = nexo.getNexoId(inHand);
                    if (!data.contains(pathPrefix + "required-items-nexo." + nexoId)) {
                        lang.sendMessage(player, "altar-commands.error-no-matching-item-to-remove"); return;
                    }
                    data.set(pathPrefix + "required-items-nexo." + nexoId, null);
                    lang.sendMessage(player, "altar-commands.required-item-removed");
                    saveAndReload();
                    return;
                }

                // Plain Bukkit item removal
                List<Map<?, ?>> list = data.getMapList(pathPrefix + "required-items");
                if (list.isEmpty()) {
                    lang.sendMessage(player, "altar-commands.error-no-required-items-to-remove"); return;
                }
                int before = list.size();
                list.removeIf(m -> {
                    Object obj = m.get("item");
                    if (!(obj instanceof Map)) return false;
                    try {
                        ItemStack t = ItemStack.deserialize((Map<String, Object>) obj);
                        return t.isSimilar(inHand);
                    } catch (Exception ex) { return false; }
                });
                if (list.size() < before) {
                    data.set(pathPrefix + "required-items", list);
                    lang.sendMessage(player, "altar-commands.required-item-removed");
                    saveAndReload();
                } else {
                    lang.sendMessage(player, "altar-commands.error-no-matching-item-to-remove");
                }
                break;
            }

            default: sendHelpMessage(player, label); break;
        }
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private void saveAndReload() {
        plugin.getDataManager().saveConfig();
        plugin.getDataManager().reloadConfig();
        plugin.getAltarManager().loadAltars();
    }

    private void sendHelpMessage(Player player, String label) {
        lang.sendRawMessage(player, "help.header");
        lang.sendRawMessage(player, "help.create",             "%label%", label);
        lang.sendRawMessage(player, "help.delete",             "%label%", label);
        lang.sendRawMessage(player, "help.list",               "%label%", label);
        lang.sendRawMessage(player, "help.wand",               "%label%", label);
        lang.sendRawMessage(player, "help.reload",             "%label%", label);
        lang.sendRawMessage(player, "help.edit-header");
        lang.sendRawMessage(player, "help.edit-set-center",    "%label%", label);
        lang.sendRawMessage(player, "help.edit-set-mob",       "%label%", label);
        lang.sendRawMessage(player, "help.edit-add-itemcenter","%label%", label);
        lang.sendRawMessage(player, "help.edit-add-pedestal",  "%label%", label);
        lang.sendRawMessage(player, "help.edit-add-item",      "%label%", label);
        lang.sendRawMessage(player, "help.edit-remove-pedestal","%label%", label);
        lang.sendRawMessage(player, "help.edit-remove-item",   "%label%", label);
    }

    // =========================================================================
    // TAB COMPLETION
    // =========================================================================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("vtalters")) return Collections.emptyList();

        if (args.length == 1)
            return StringUtil.copyPartialMatches(args[0],
                    Arrays.asList("create","delete","list","wand","reload","edit"), new ArrayList<>());

        if (args.length == 2 && (args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("edit"))) {
            FileConfiguration data = plugin.getDataManager().getConfig();
            if (data.isConfigurationSection("altars"))
                return StringUtil.copyPartialMatches(args[1],
                        data.getConfigurationSection("altars").getKeys(false), new ArrayList<>());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("edit"))
            return StringUtil.copyPartialMatches(args[2],
                    Arrays.asList("set","add","remove"), new ArrayList<>());

        if (args.length == 4 && args[0].equalsIgnoreCase("edit")) {
            if (args[2].equalsIgnoreCase("set"))
                return StringUtil.copyPartialMatches(args[3], Arrays.asList("center","mob"), new ArrayList<>());
            if (args[2].equalsIgnoreCase("add"))
                return StringUtil.copyPartialMatches(args[3], Arrays.asList("pedestal","item","itemcenter"), new ArrayList<>());
            if (args[2].equalsIgnoreCase("remove"))
                return StringUtil.copyPartialMatches(args[3], Arrays.asList("pedestal","item"), new ArrayList<>());
        }

        if (args.length == 5 && args[0].equalsIgnoreCase("edit") && args[2].equalsIgnoreCase("remove")
                && (args[3].equalsIgnoreCase("pedestal") || args[3].equalsIgnoreCase("item")))
            return StringUtil.copyPartialMatches(args[4], Collections.singletonList("all"), new ArrayList<>());

        return Collections.emptyList();
    }
}
