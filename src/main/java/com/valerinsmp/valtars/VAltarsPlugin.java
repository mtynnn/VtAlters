/*
 * vAltars - Ritual altars for ValerinSMP.
 * Copyright (c) 2025 thangks
 * Licensed under the MIT License.
 */
package com.valerinsmp.valtars;

import com.valerinsmp.valtars.command.MainThreadCommandBinder;
import com.valerinsmp.valtars.command.VAltarsCommand;
import com.valerinsmp.valtars.config.AltarRepository;
import com.valerinsmp.valtars.config.ConfigService;
import com.valerinsmp.valtars.config.LegacyDataMigrator;
import com.valerinsmp.valtars.gui.AltarBrowser;
import com.valerinsmp.valtars.integration.MythicMobsIntegration;
import com.valerinsmp.valtars.integration.NexoIntegration;
import com.valerinsmp.valtars.listener.AltarListener;
import com.valerinsmp.valtars.message.MessageService;
import com.valerinsmp.valtars.ritual.RefundMailbox;
import com.valerinsmp.valtars.ritual.RefundService;
import com.valerinsmp.valtars.ritual.RitualPresentation;
import com.valerinsmp.valtars.service.AltarManager;
import com.valerinsmp.valtars.service.WandService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class VAltarsPlugin extends JavaPlugin {
    private ConfigService configService;
    private MessageService messageService;
    private RefundService refundService;
    private WandService wandService;
    private AltarManager altarManager;
    private AltarBrowser altarBrowser;
    private boolean started;

    @Override
    public void onEnable() {
        long startedAt = System.nanoTime();
        getLogger().info("Starting vAltars v" + getPluginMeta().getVersion() + "...");
        getLogger().info("Platform: Paper 1.21.11+ | Java 21 bytecode");
        try {
            migrateLegacyData();
            saveDefaultConfig();
            ensureResource("altars.yml");
            ensureResource("language/messages_es.yml");
            ensureResource("language/messages_en.yml");
            ensureResource("language/messages_vi.yml");

            configService = new ConfigService(new File(getDataFolder(), "config.yml").toPath());
            messageService = new MessageService(this, configService.snapshot().language());
            RefundMailbox mailbox = new RefundMailbox(new File(getDataFolder(), "pending-refunds.yml").toPath());
            refundService = new RefundService(this, mailbox);
            NexoIntegration nexo = new NexoIntegration(this);
            MythicMobsIntegration mythic = new MythicMobsIntegration();
            RitualPresentation presentation = new RitualPresentation(this);
            AltarRepository repository = new AltarRepository(new File(getDataFolder(), "altars.yml").toPath());
            wandService = new WandService(this, messageService);
            altarManager = new AltarManager(this, repository, configService, messageService, nexo, mythic,
                    refundService, presentation);
            altarBrowser = new AltarBrowser(this);

            getServer().getPluginManager().registerEvents(new AltarListener(this), this);
            getServer().getPluginManager().registerEvents(altarBrowser, this);
            VAltarsCommand commands = new VAltarsCommand(this);
            MainThreadCommandBinder binder = new MainThreadCommandBinder(this);
            binder.bind("valtars", commands, commands);
            binder.bind("valtarsadmin", commands, commands);

            getLogger().info(nexo.available()
                    ? "Integration enabled: Nexo"
                    : "Integration unavailable: Nexo (custom item altars disabled)");
            started = true;
            getLogger().info("Enabled successfully in " + elapsedMillis(startedAt) + " ms.");
        } catch (Throwable throwable) {
            getLogger().severe("Startup failed during initialization: " + throwable.getMessage());
            throwable.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        long startedAt = System.nanoTime();
        getLogger().info("Stopping vAltars...");
        if (altarManager != null && Bukkit.isPrimaryThread()) altarManager.shutdown();
        getLogger().info("Disabled successfully in " + elapsedMillis(startedAt) + " ms.");
        started = false;
    }

    public boolean reloadRuntime() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Reload must run on the primary thread");
        try {
            ConfigService.Snapshot configCandidate = configService.validateReload();
            MessageService.Snapshot messageCandidate = messageService.validateReload(configCandidate.language());
            configService.apply(configCandidate);
            messageService.apply(messageCandidate);
            getLogger().info("Configuration and messages reloaded.");
            return true;
        } catch (RuntimeException exception) {
            getLogger().warning("Reload rejected; previous configuration remains active: " + exception.getMessage());
            return false;
        }
    }

    public ConfigService configService() { return configService; }
    public MessageService messages() { return messageService; }
    public RefundService refunds() { return refundService; }
    public WandService wands() { return wandService; }
    public AltarManager altars() { return altarManager; }
    public AltarBrowser browser() { return altarBrowser; }
    public boolean started() { return started; }

    private void migrateLegacyData() {
        File pluginsFolder = getDataFolder().getParentFile();
        LegacyDataMigrator.Result result = new LegacyDataMigrator(
                new File(pluginsFolder, "VtAlters").toPath(), getDataFolder().toPath()).migrate();
        if (result.copiedFiles() > 0 || result.preservedModernFiles() > 0) {
            getLogger().info("Legacy data migration: copied=" + result.copiedFiles()
                    + ", preserved-modern=" + result.preservedModernFiles() + ".");
        }
    }

    private void ensureResource(String path) {
        if (!new File(getDataFolder(), path).exists()) saveResource(path, false);
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
