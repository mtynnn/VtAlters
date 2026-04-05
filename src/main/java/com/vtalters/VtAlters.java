/*
 * VtAlters - Plugin para invocar jefes mediante altares rituales.
 * Copyright (c) 2025 thangks
 *
 * Licenciado bajo la Licencia MIT.
 * Consulta el archivo LICENSE en la raíz del proyecto para más información.
 */

package com.vtalters;

import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Clase principal del plugin VtAlters.
 *
 * Compatibilidad con PlugMan:
 *  - onEnable() inicializa todo desde cero — seguro para "plugman load" y "plugman reload".
 *  - onDisable() limpia todas las tareas y entidades — seguro para "plugman unload".
 *  - reloadPlugin() reutiliza la misma instancia sin reiniciar el servidor, lo que
 *    equivale a lo que PlugMan hace internamente al hacer "plugman reload VtAlters".
 *
 * Orden de inicialización:
 *  1. ErrorHandler  — debe ser primero para que los demás puedan registrar errores.
 *  2. NexoHook      — detección de Nexo antes de que lo necesiten los managers.
 *  3. DataManager   — carga altars.yml.
 *  4. LanguageManager — carga el archivo de idioma seleccionado.
 *  5. WandManager   — crea la varita de configuración.
 *  6. AltarManager  — carga los altares y arranca las tareas de partículas.
 */
public final class VtAlters extends JavaPlugin {

    private AltarManager altarManager;
    private WandManager wandManager;
    private DataManager dataManager;
    private LanguageManager languageManager;
    private ErrorHandler errorHandler;
    private NexoHook nexoHook;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ensureConfigDefaults();

        this.errorHandler    = new ErrorHandler(this);
        this.nexoHook        = new NexoHook();
        this.dataManager     = new DataManager(this);
        this.languageManager = new LanguageManager(this);
        this.wandManager     = new WandManager(this);
        this.altarManager    = new AltarManager(this);

        getServer().getPluginManager().registerEvents(new AltarListener(this), this);

        AltarCommand altarCommand = new AltarCommand(this);
        PluginCommand command = getCommand("vtalters");
        if (command != null) {
            command.setExecutor(altarCommand);
            command.setTabCompleter(altarCommand);
        }

        if (nexoHook.isAvailable()) {
            getLogger().info("Nexo detected - custom item support enabled.");
        } else {
            getLogger().info("Nexo not detected - running with standard Bukkit items.");
        }

        getLogger().info("VtAlters v" + getDescription().getVersion() + " habilitado.");
    }

    @Override
    public void onDisable() {
        if (altarManager != null) {
            altarManager.shutdown();
        }
        getLogger().info("VtAlters deshabilitado.");
    }

    /**
     * Recarga completa del plugin sin reiniciar el servidor.
     * Compatible con PlugMan reload y con el comando /vta reload.
     *
     * Pasos:
     *  1. Apaga el AltarManager (cancela tareas, elimina entidades flotantes).
     *  2. Desregistra todos los listeners de este plugin.
     *  3. Recarga config.yml.
     *  4. Reinicializa todos los managers con la nueva configuración.
     *  5. Vuelve a registrar el listener y el comando.
     */
    private void ensureConfigDefaults() {
        boolean needsSave = false;

        // Añade la sección de mensajes si no existe
        if (!getConfig().contains("messages.prefix")) {
            getConfig().set("messages.prefix", "&e&lVtAlters &8&l»&r ");
            needsSave = true;
        }

        if (needsSave) {
            saveConfig();
        }
    }

    /**
     * Recarga completa del plugin sin reiniciar el servidor.
     * Compatible con PlugMan reload y con el comando /vta reload.
     *
     * Pasos:
     *  1. Apaga el AltarManager (cancela tareas, elimina entidades flotantes).
     *  2. Desregistra todos los listeners de este plugin.
     *  3. Recarga config.yml.
     *  4. Reinicializa todos los managers con la nueva configuración.
     *  5. Vuelve a registrar el listener y el comando.
     */
    public void reloadPlugin() {
        if (altarManager != null) {
            altarManager.shutdown();
        }
        HandlerList.unregisterAll(this);

        reloadConfig();
        ensureConfigDefaults();

        errorHandler.clearErrors();
        dataManager.reloadConfig();

        this.languageManager = new LanguageManager(this);
        this.wandManager     = new WandManager(this);
        this.altarManager    = new AltarManager(this);

        getServer().getPluginManager().registerEvents(new AltarListener(this), this);

        AltarCommand altarCommand = new AltarCommand(this);
        PluginCommand command = getCommand("vtalters");
        if (command != null) {
            command.setExecutor(altarCommand);
            command.setTabCompleter(altarCommand);
        }

        getLogger().info("VtAlters recargado correctamente.");
    }

    // ── Acceso a managers ─────────────────────────────────────────────────
    public AltarManager    getAltarManager()    { return altarManager; }
    public WandManager     getWandManager()     { return wandManager; }
    public DataManager     getDataManager()     { return dataManager; }
    public LanguageManager getLanguageManager() { return languageManager; }
    public ErrorHandler    getErrorHandler()    { return errorHandler; }
    public NexoHook        getNexoHook()        { return nexoHook; }
}
