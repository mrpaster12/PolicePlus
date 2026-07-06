package com.policeplus;

import com.policeplus.commands.*;
import com.policeplus.listeners.*;
import com.policeplus.managers.*;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class PolicePlus extends JavaPlugin {

    private static PolicePlus instance;
    private ConfigManager configManager;
    private LanguageManager languageManager;
    private WantedManager wantedManager;
    private JailManager jailManager;
    private CompassManager compassManager;
    private DisplayManager displayManager;
    private HandcuffManager handcuffManager;
    private RankManager rankManager;
    private LogManager logManager;
    private BountyManager bountyManager;
    private SalaryManager salaryManager;
    private StatsManager statsManager;
    private static Economy econ = null;
    private int handcuffTimeoutTaskId = -1;
    private int elasticDragTaskId = -1;

    @Override
    public void onEnable() {
        instance = this;

        // Setup Vault Economy
        if (!setupEconomy()) {
            getLogger().warning("[Police-Plus] Vault not detected. Economy rewards will be disabled.");
        }

        getLogger().info("=== Police Plugin Starting ===");
        getLogger().info("Version: " + getDescription().getVersion());
        getLogger().info("API Version: " + getDescription().getAPIVersion());

        try {
            // Initialize managers in order
            initializeManagers();

            // Load configurations (loads config.yml and language files)
            loadConfigurations();

            // Load HandcuffManager config AFTER ConfigManager has loaded config.yml
            handcuffManager.loadConfig();

            // Register commands and listeners
            registerCommands();
            registerListeners();

            // Start systems
            startSystems();

            // Clean up any residual freeze effects from all online players (e.g. after reload)
            cleanupResidualEffects();

            // Register PlaceholderAPI if present
            registerPlaceholderAPI();

            getLogger().info("=== Police Plugin Started Successfully ===");

        } catch (Exception e) {
            getLogger().severe("Failed to start Police Plugin: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void initializeManagers() {
        getLogger().info("Initializing managers...");

        configManager = new ConfigManager(this);
        languageManager = new LanguageManager(this);
        wantedManager = new WantedManager(this);
        jailManager = new JailManager(this);
        compassManager = new CompassManager(this);
        displayManager = new DisplayManager(this);
        handcuffManager = new HandcuffManager(this);
        rankManager = new RankManager(this);
        logManager = new LogManager(this);
        bountyManager = new BountyManager(this);
        salaryManager = new SalaryManager(this);
        statsManager = new StatsManager(this);

        getLogger().info("All managers initialized successfully");
    }

    private void loadConfigurations() {
        getLogger().info("Loading configurations...");

        // Load main config first
        configManager.loadConfig();
        configManager.validateConfig();

        // Load language configuration
        languageManager.loadLanguage();

        // Validate language loading
        if (!languageManager.isLanguageLoaded()) {
            getLogger().severe("Failed to load language configuration!");
            throw new RuntimeException("Language configuration failed to load");
        }

        getLogger().info("Language loaded: " + languageManager.getCurrentLanguage());
        getLogger().info("Messages loaded: " + languageManager.getLoadedMessageCount());

        getLogger().info("All configurations loaded successfully");
    }

    private void registerCommands() {
        getLogger().info("Registering commands...");

        getCommand("wanted").setExecutor(new WantedCommand(this));
        getCommand("police").setExecutor(new PoliceCommand(this));
        getCommand("compass").setExecutor(new CompassCommand(this));
        getCommand("jail").setExecutor(new JailCommand(this));
        getCommand("jails").setExecutor(new JailCommand(this));
        getCommand("unjail").setExecutor(new UnjailCommand(this));

        // Register tab completers using a single reusable instance
        PoliceTabCompleter sharedCompleter = new PoliceTabCompleter(this);
        getCommand("wanted").setTabCompleter(sharedCompleter);
        getCommand("police").setTabCompleter(sharedCompleter);
        getCommand("compass").setTabCompleter(sharedCompleter);
        getCommand("jail").setTabCompleter(sharedCompleter);
        getCommand("unjail").setTabCompleter(sharedCompleter);

        // Handcuff commands (shared instance)
        try {
            HandcuffCommand handcuffCommand = new HandcuffCommand(this);
            getCommand("cuffe").setExecutor(handcuffCommand);
            getCommand("uncuffe").setExecutor(handcuffCommand);
            getCommand("cuffe").setTabCompleter(sharedCompleter);
            getCommand("uncuffe").setTabCompleter(sharedCompleter);
            getLogger().info("Handcuff commands registered successfully");
        } catch (Exception e) {
            getLogger().warning("Failed to register handcuff commands: " + e.getMessage());
        }

        // Phase 1 commands (Rank, Bounty, Log)
        try {
            getCommand("rank").setExecutor(new RankCommand(this));
            getCommand("rank").setTabCompleter(sharedCompleter);
            getLogger().info("Rank command registered successfully");
        } catch (Exception e) {
            getLogger().warning("Failed to register rank command: " + e.getMessage());
        }

        try {
            getCommand("bounty").setExecutor(new BountyCommand(this));
            getCommand("bounty").setTabCompleter(sharedCompleter);
            getLogger().info("Bounty command registered successfully");
        } catch (Exception e) {
            getLogger().warning("Failed to register bounty command: " + e.getMessage());
        }

        try {
            getCommand("log").setExecutor(new LogCommand(this));
            getCommand("log").setTabCompleter(sharedCompleter);
            getLogger().info("Log command registered successfully");
        } catch (Exception e) {
            getLogger().warning("Failed to register log command: " + e.getMessage());
        }

        try {
            getCommand("salary").setExecutor(new SalaryCommand(this));
            getCommand("salary").setTabCompleter(sharedCompleter);
            getLogger().info("Salary command registered successfully");
        } catch (Exception e) {
            getLogger().warning("Failed to register salary command: " + e.getMessage());
        }

        try {
            getCommand("stats").setExecutor(new StatsCommand(this));
            getCommand("stats").setTabCompleter(sharedCompleter);
            getLogger().info("Stats command registered successfully");
        } catch (Exception e) {
            getLogger().warning("Failed to register stats command: " + e.getMessage());
        }

        getLogger().info("All commands registered successfully");
    }

    private void registerListeners() {
        getLogger().info("Registering listeners...");

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new CompassListener(this), this);
        getServer().getPluginManager().registerEvents(new JailListener(this), this);
        getServer().getPluginManager().registerEvents(new HandcuffListener(this), this);

        getLogger().info("All listeners registered successfully");
    }

    private void startSystems() {
        getLogger().info("Starting systems...");

        // Start display manager (also calls setupBelowNameObjective + updateAllPlayersDisplay internally)
        displayManager.start();

        // Start handcuff timeout checker
        startHandcuffTimeoutChecker();
        handcuffManager.start();

        // Start wanted auto-decay scheduler
        wantedManager.startAutoDecay();

        getLogger().info("All systems started successfully");
    }

    private void registerPlaceholderAPI() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                Object expansion = new PlaceholderHook(this);
                expansion.getClass().getMethod("register").invoke(expansion);
                getLogger().info(
                        "PlaceholderAPI detected: registered placeholders %police_wanted_level% and %police_wanted_stars%.");
            } catch (Throwable t) {
                getLogger().warning("PlaceholderAPI detected but could not register expansion: " + t.getMessage());
            }
        } else {
            getLogger().info("PlaceholderAPI not detected - placeholders will not be available");
        }
    }

    /**
     * Cleans up residual handcuff/jail freeze effects from all online players.
     * This handles the case where a plugin reload clears the in-memory cuffedPlayers map
     * but the actual potion effects and walk speeds remain on the players.
     */
    private void cleanupResidualEffects() {
        int cleaned = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            // If the player is NOT currently tracked as cuffed or jailed, clear any freeze effects
            if (!handcuffManager.isCuffed(player) && !jailManager.isJailed(player)) {
                boolean hadEffects = false;
                try {
                    if (player.getWalkSpeed() == 0.0f) {
                        player.setWalkSpeed(0.2f);
                        hadEffects = true;
                    }
                    if (player.getFlySpeed() == 0.0f) {
                        player.setFlySpeed(0.1f);
                        hadEffects = true;
                    }
                    // Clear immobilizing potion effects
                    player.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOW);
                    player.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOW_DIGGING);
                    player.removePotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS);
                    hadEffects = true;
                } catch (Throwable ignored) {
                }
                if (hadEffects) cleaned++;
            }
        }
        if (cleaned > 0) {
            getLogger().info("Cleaned up residual freeze effects for " + cleaned + " player(s).");
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("=== Police Plugin Shutting Down ===");

        try {
            // Save all data
            if (wantedManager != null) {
                wantedManager.saveData();
                getLogger().info("Wanted data saved");
            }

            if (jailManager != null) {
                jailManager.saveData();
                getLogger().info("Jail data saved");
            }

            // Stop wanted auto-decay
            if (wantedManager != null) {
                wantedManager.stopAutoDecay();
                getLogger().info("Wanted auto-decay stopped");
            }

            // Stop systems
            if (jailManager != null) {
                jailManager.stop();
                getLogger().info("Jail system stopped");
            }

            if (displayManager != null) {
                displayManager.stop();
                getLogger().info("Display system stopped");
            }

            if (handcuffManager != null) {
                handcuffManager.stop();
                handcuffManager.cleanupCombatLoggers();
                getLogger().info("Handcuff drag system stopped");
            }

            // Clean up freeze effects from all online players on shutdown
            for (Player player : Bukkit.getOnlinePlayers()) {
                try {
                    player.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOW);
                    player.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOW_DIGGING);
                    player.removePotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS);
                    if (player.getWalkSpeed() == 0.0f) player.setWalkSpeed(0.2f);
                    if (player.getFlySpeed() == 0.0f) player.setFlySpeed(0.1f);
                } catch (Throwable ignored) {
                }
            }

            getLogger().info("=== Police Plugin Shut Down Successfully ===");

        } catch (Exception e) {
            getLogger().severe("Error during shutdown: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Starts the handcuff timeout checker repeating task.
     * Stores the task ID for cancellation on reload/disable.
     */
    public void startHandcuffTimeoutChecker() {
        // Cancel existing task if running (prevents duplicates on reload)
        if (handcuffTimeoutTaskId != -1) {
            getServer().getScheduler().cancelTask(handcuffTimeoutTaskId);
            handcuffTimeoutTaskId = -1;
        }
        // Use sync task for timeout checking
        // Also periodically cleans up expired combat-logger entries (every 5 min)
        final int[] tickCounter = {0};
        handcuffTimeoutTaskId = getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
            if (handcuffManager != null) {
                handcuffManager.checkTimeouts();
                // Clean up expired combat-logger entries every 5 minutes (6000 ticks)
                if (++tickCounter[0] >= 6000) {
                    tickCounter[0] = 0;
                    handcuffManager.cleanupExpiredCombatLoggers();
                }
            }
        }, 20L, 20L);
        getLogger().info("Handcuff timeout checker started");

        // Start elastic drag task — every 2 ticks, smooth velocity pull + emergency teleport
        // FIX: cancel any previous instance first (same pattern as the timeout checker above)
        // so calling this method again on /police reload doesn't stack duplicate tasks.
        if (elasticDragTaskId != -1) {
            getServer().getScheduler().cancelTask(elasticDragTaskId);
            elasticDragTaskId = -1;
        }
        elasticDragTaskId = getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
            if (handcuffManager != null) {
                handcuffManager.handleElasticDrag();
            }
        }, 2L, 2L);
        getLogger().info("Elastic drag system started");
    }

    // Getter methods
    public static PolicePlus getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    public WantedManager getWantedManager() {
        return wantedManager;
    }

    public JailManager getJailManager() {
        return jailManager;
    }

    public CompassManager getCompassManager() {
        return compassManager;
    }

    public DisplayManager getDisplayManager() {
        return displayManager;
    }

    public HandcuffManager getHandcuffManager() {
        return handcuffManager;
    }

    public RankManager getRankManager() {
        return rankManager;
    }

    public LogManager getLogManager() {
        return logManager;
    }

    public BountyManager getBountyManager() {
        return bountyManager;
    }

    public SalaryManager getSalaryManager() {
        return salaryManager;
    }

    public StatsManager getStatsManager() {
        return statsManager;
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    public static Economy getEconomy() {
        return econ;
    }

    /**
     * Master cop permission check. A player is considered a cop if they have
     * the master permission {@code policeplus.police},
     * the admin permission {@code policeplus.admin}, or operator status.
     *
     * @param player the player to check
     * @return true if the player should be treated as a police officer
     */
    public static boolean isCop(Player player) {
        return player.hasPermission("policeplus.police")
            || player.hasPermission("policeplus.admin")
            || player.isOp();
    }

    // Debug methods
    public void debugPlugin() {
        getLogger().info("=== Police Plugin Debug Info ===");
        getLogger().info("Plugin Version: " + getDescription().getVersion());
        getLogger().info("API Version: " + getDescription().getAPIVersion());
        getLogger().info("Server Version: " + getServer().getVersion());
        getLogger().info("Bukkit Version: " + getServer().getBukkitVersion());

        if (configManager != null) {
            configManager.validateConfig();
        }

        if (languageManager != null) {
            languageManager.debugLanguage();
        }

        getLogger().info("=== Debug Complete ===");
    }
}
