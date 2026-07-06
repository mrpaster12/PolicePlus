package com.policeplus.managers;

import com.policeplus.PolicePlus;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class ConfigManager {

    private final PolicePlus plugin;
    private FileConfiguration config;

    // Cache frequently accessed values
    private int cachedMaxWantedLevel;
    private int cachedJailTimePerWanted;
    private int cachedArrestDistance;
    private int cachedCompassUpdateInterval;
    private String cachedLanguage;
    private boolean cacheInitialized = false;

    // Cached display settings (read on every display update)
    private boolean cachedDisplayEnabled;
    private String cachedDisplayMode;
    private boolean cachedShowTabList;
    private boolean cachedShowBelowName;
    private String cachedTablistFormatStars;
    private String cachedTablistFormatNumber;
    private String cachedBelowNameObjective;
    private String cachedBelowNameTitle;
    private String cachedStarSymbol;
    private String cachedStarFilledColor;
    private String cachedStarEmptyColor;

    // Cached jail settings
    private int cachedDefaultJailRadius;
    private int cachedJailRequiredDistance;
    private boolean cachedClearWantedOnRelease;
    private int cachedBlocksPerWanted;
    private int cachedMaxJailTime;
    private String cachedJailType;

    // Cached handcuff settings
    private int cachedHandcuffMaxTime;
    private boolean cachedHandcuffApplyBlindness;

    // Cached wanted settings
    private boolean cachedWantedOnKill;
    private int cachedWantedLevelPerKill;
    private boolean cachedResetWantedOnDeath;
    private int cachedMaxWantedLevelToResetOnDeath;

    public ConfigManager(PolicePlus plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        config = plugin.getConfig();
        fillMissingKeys();
        initializeCache();
        plugin.getLogger().info("Configuration loaded successfully");
    }

    public void reloadConfig() {
        // Force a fresh read from disk — bypasses any Bukkit-level caching
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (configFile.exists()) {
            config = YamlConfiguration.loadConfiguration(configFile);
            plugin.getLogger().info("Config re-read from disk: " + configFile.getAbsolutePath());
        } else {
            // Fallback: use Bukkit's built-in mechanism
            plugin.reloadConfig();
            config = plugin.getConfig();
        }

        // Auto-insert any missing keys from defaults (prevents NPE after plugin updates)
        fillMissingKeys();

        // Re-cache all settings
        initializeCache();
        plugin.getLogger().info("Configuration reloaded successfully (" + config.getKeys(true).size() + " keys)");
    }

    /**
     * Automatically inserts any missing config keys with their default values.
     * This prevents NullPointerExceptions when admins update the plugin but use an old config.yml.
     * Also writes the updated file to disk so admin sees the new keys next time they open it.
     */
    private void fillMissingKeys() {
        boolean changed = false;
        // Language
        changed |= ensureDefault("language", "en");

        // Wanted
        changed |= ensureDefault("wanted.max_wanted_level", 5);
        changed |= ensureDefault("wanted.wanted_on_kill", true);
        changed |= ensureDefault("wanted.wanted_level_per_kill", 1);
        changed |= ensureDefault("wanted.auto_remove", true);
        changed |= ensureDefault("wanted.remove_interval_minutes", 30);
        changed |= ensureDefault("wanted.reset_on_death.enabled", false);
        changed |= ensureDefault("wanted.reset_on_death.max_level_to_reset", 3);

        // Jail
        changed |= ensureDefault("jail.type", "TIME");
        changed |= ensureDefault("jail.jail_time_per_wanted", 5);
        changed |= ensureDefault("jail.max_jail_time", 60);
        changed |= ensureDefault("jail.blocks_per_wanted", 100);
        changed |= ensureDefault("jail.arrest_distance", 10);
        changed |= ensureDefault("jail.jail_required_distance", 7);
        changed |= ensureDefault("jail.clear_wanted_on_release", true);
        changed |= ensureDefault("jail.default_jail_radius", 10);

        // Compass
        changed |= ensureDefault("compass.update_interval", 20);
        changed |= ensureDefault("compass.max_distance", 10000);

        // Handcuff
        changed |= ensureDefault("handcuff.name", "cuff");
        changed |= ensureDefault("handcuff.lore", "cuff");
        changed |= ensureDefault("handcuff.name_color", "&a");
        changed |= ensureDefault("handcuff.name_bold", true);
        changed |= ensureDefault("handcuff.max_time", 300);
        changed |= ensureDefault("handcuff.accept_plain_item", false);
        changed |= ensureDefault("handcuff.apply_blindness", false);

        // Display
        changed |= ensureDefault("display.enabled", true);
        changed |= ensureDefault("display.mode", "stars");
        changed |= ensureDefault("display.stars.symbol", "★");
        changed |= ensureDefault("display.stars.filled_color", "&6");
        changed |= ensureDefault("display.stars.empty_color", "&7");
        changed |= ensureDefault("display.tablist.enabled", true);
        changed |= ensureDefault("display.tablist.format_stars", " &7[&e{stars}&7]");
        changed |= ensureDefault("display.tablist.format_number", " &7[&e{level}&7]");
        changed |= ensureDefault("display.below_name.enabled", true);
        changed |= ensureDefault("display.below_name.objective", "wantedLevel");
        changed |= ensureDefault("display.below_name.title", "&eWanted");

        // Bounty
        changed |= ensureDefault("bounty.enabled", true);
        changed |= ensureDefault("bounty.max_bounty", 10000);
        changed |= ensureDefault("bounty.min_bounty", 10);
        changed |= ensureDefault("bounty.remove_on_jail", true);
        changed |= ensureDefault("bounty.currency_symbol", "$");

        // Economy
        changed |= ensureDefault("economy.enabled", true);
        changed |= ensureDefault("economy.reward_per_wanted_level", 150.0);

        // Logs
        changed |= ensureDefault("logs.enabled", true);
        changed |= ensureDefault("logs.save_on_shutdown", true);
        changed |= ensureDefault("logs.rotation_days", 30);
        changed |= ensureDefault("logs.max_log_entries_per_day", 10000);

        if (changed) {
            try {
                File configFile = new File(plugin.getDataFolder(), "config.yml");
                config.save(configFile);
                plugin.getLogger().info("Missing config keys filled with defaults and saved to disk.");
            } catch (Exception e) {
                plugin.getLogger().warning("Could not save updated config.yml: " + e.getMessage());
            }
        }
    }

    /**
     * Ensures a config key exists. If missing, sets it to the default value.
     * Returns true if the config was modified (key was missing).
     */
    private boolean ensureDefault(String path, Object defaultValue) {
        if (!config.isSet(path)) {
            config.set(path, defaultValue);
            return true;
        }
        return false;
    }

    private void initializeCache() {
        cachedMaxWantedLevel = config.getInt("wanted.max_wanted_level", 5);
        cachedJailTimePerWanted = config.getInt("jail.jail_time_per_wanted", 5);
        cachedArrestDistance = config.getInt("jail.arrest_distance", 10);
        cachedCompassUpdateInterval = config.getInt("compass.update_interval", 20);
        cachedLanguage = config.getString("language", "en");

        // Display settings
        cachedDisplayEnabled = config.getBoolean("display.enabled", true);
        cachedDisplayMode = config.getString("display.mode", "stars");
        cachedShowTabList = config.getBoolean("display.tablist.enabled", true);
        cachedShowBelowName = config.getBoolean("display.below_name.enabled", true);
        cachedTablistFormatStars = config.getString("display.tablist.format_stars", " &7[&e{stars}&7]");
        cachedTablistFormatNumber = config.getString("display.tablist.format_number", " &7[&e{level}&7]");
        cachedBelowNameObjective = config.getString("display.below_name.objective", "wantedLevel");
        cachedBelowNameTitle = config.getString("display.below_name.title", "&eWanted");
        cachedStarSymbol = config.getString("display.stars.symbol", "★");
        cachedStarFilledColor = config.getString("display.stars.filled_color", "&6");
        cachedStarEmptyColor = config.getString("display.stars.empty_color", "&7");

        // Jail settings
        cachedDefaultJailRadius = config.getInt("jail.default_jail_radius", 10);
        cachedJailRequiredDistance = config.getInt("jail.jail_required_distance", 7);
        cachedClearWantedOnRelease = config.getBoolean("jail.clear_wanted_on_release", true);
        cachedBlocksPerWanted = config.getInt("jail.blocks_per_wanted", 100);
        cachedMaxJailTime = config.getInt("jail.max_jail_time", 60);
        cachedJailType = config.getString("jail.type", "TIME");

        // Handcuff settings
        cachedHandcuffMaxTime = config.getInt("handcuff.max_time", 300);
        cachedHandcuffApplyBlindness = config.getBoolean("handcuff.apply_blindness", false);

        // Wanted settings
        cachedWantedOnKill = config.getBoolean("wanted.wanted_on_kill", true);
        cachedWantedLevelPerKill = config.getInt("wanted.wanted_level_per_kill", 1);
        cachedResetWantedOnDeath = config.getBoolean("wanted.reset_on_death.enabled", false);
        cachedMaxWantedLevelToResetOnDeath = config.getInt("wanted.reset_on_death.max_level_to_reset", 3);

        cacheInitialized = true;
    }

    public String getLanguage() {
        return cacheInitialized ? cachedLanguage : config.getString("language", "en");
    }

    // Wanted system settings - use cached values for frequently accessed
    public int getMaxWantedLevel() {
        return cacheInitialized ? cachedMaxWantedLevel : config.getInt("wanted.max_wanted_level", 5);
    }

    public boolean isWantedOnKill() {
        return cacheInitialized ? cachedWantedOnKill : config.getBoolean("wanted.wanted_on_kill", true);
    }

    public int getWantedLevelPerKill() {
        return cacheInitialized ? cachedWantedLevelPerKill : config.getInt("wanted.wanted_level_per_kill", 1);
    }

    public boolean isWantedAutoRemove() {
        return config.getBoolean("wanted.auto_remove", true);
    }

    public int getWantedRemoveIntervalMinutes() {
        return config.getInt("wanted.remove_interval_minutes", 30);
    }

    public boolean isResetWantedOnDeathEnabled() {
        return cacheInitialized ? cachedResetWantedOnDeath : config.getBoolean("wanted.reset_on_death.enabled", false);
    }

    public int getMaxWantedLevelToResetOnDeath() {
        return cacheInitialized ? cachedMaxWantedLevelToResetOnDeath : config.getInt("wanted.reset_on_death.max_level_to_reset", 3);
    }

    // Jail system settings - use cached value for frequently accessed
    public int getJailTimePerWanted() {
        return cacheInitialized ? cachedJailTimePerWanted : config.getInt("jail.jail_time_per_wanted", 5);
    }

    public String getJailType() {
        return cacheInitialized ? cachedJailType : config.getString("jail.type", "TIME");
    }

    public boolean isJailTypeBlocks() {
        return "BLOCKS".equalsIgnoreCase(getJailType());
    }

    public int getBlocksPerWanted() {
        return cacheInitialized ? cachedBlocksPerWanted : config.getInt("jail.blocks_per_wanted", 100);
    }

    public java.util.List<String> getAllowedBlocks() {
        java.util.List<String> blocks = config.getStringList("jail.allowed_blocks");
        if (blocks.isEmpty()) {
            blocks = new java.util.ArrayList<>();
            blocks.add("COBBLESTONE");
            blocks.add("STONE");
        }
        return blocks;
    }

    public int getMaxJailTime() {
        return cacheInitialized ? cachedMaxJailTime : config.getInt("jail.max_jail_time", 60);
    }

    public int getArrestDistance() {
        return cacheInitialized ? cachedArrestDistance : config.getInt("jail.arrest_distance", 10);
    }

    public int getJailRequiredDistance() {
        return cacheInitialized ? cachedJailRequiredDistance : config.getInt("jail.jail_required_distance", 7);
    }

    public boolean isClearWantedOnRelease() {
        return cacheInitialized ? cachedClearWantedOnRelease : config.getBoolean("jail.clear_wanted_on_release", true);
    }

    public int getDefaultJailRadius() {
        return cacheInitialized ? cachedDefaultJailRadius : config.getInt("jail.default_jail_radius", 10);
    }

    // Compass settings - use cached value for frequently accessed
    public int getCompassUpdateInterval() {
        return cacheInitialized ? cachedCompassUpdateInterval : config.getInt("compass.update_interval", 20);
    }

    public int getCompassMaxDistance() {
        return config.getInt("compass.max_distance", 10000);
    }

    // Handcuff settings
    public String getHandcuffItem() {
        return config.getString("handcuff.item", "BLAZE_ROD");
    }

    public String getHandcuffName() {
        return config.getString("handcuff.name", "cuff");
    }

    public String getHandcuffLore() {
        return config.getString("handcuff.lore", "cuff");
    }

    public String getHandcuffNameColor() {
        return config.getString("handcuff.name_color", "&a");
    }

    public boolean isHandcuffNameBold() {
        return config.getBoolean("handcuff.name_bold", true);
    }

    public int getHandcuffMaxTime() {
        return cacheInitialized ? cachedHandcuffMaxTime : config.getInt("handcuff.max_time", 300);
    }

    public boolean isHandcuffApplyBlindness() {
        return cacheInitialized ? cachedHandcuffApplyBlindness : config.getBoolean("handcuff.apply_blindness", false);
    }

    public boolean isHandcuffAcceptPlainItem() {
        return config.getBoolean("handcuff.accept_plain_item", false);
    }

    // Display settings
    public boolean isDisplayEnabled() {
        return cacheInitialized ? cachedDisplayEnabled : config.getBoolean("display.enabled", true);
    }

    public String getDisplayMode() {
        return cacheInitialized ? cachedDisplayMode : config.getString("display.mode", "stars");
    }

    public boolean isShowTabList() {
        return cacheInitialized ? cachedShowTabList : config.getBoolean("display.tablist.enabled", true);
    }

    public String getTablistFormatStars() {
        return cacheInitialized ? cachedTablistFormatStars : config.getString("display.tablist.format_stars", " &7[&e{stars}&7]");
    }

    public String getTablistFormatNumber() {
        return cacheInitialized ? cachedTablistFormatNumber : config.getString("display.tablist.format_number", " &7[&e{level}&7]");
    }

    public boolean isShowBelowName() {
        return cacheInitialized ? cachedShowBelowName : config.getBoolean("display.below_name.enabled", true);
    }

    public String getBelowNameObjective() {
        return cacheInitialized ? cachedBelowNameObjective : config.getString("display.below_name.objective", "wantedLevel");
    }

    public String getBelowNameTitle() {
        return cacheInitialized ? cachedBelowNameTitle : config.getString("display.below_name.title", "&eWanted");
    }

    public String getStarSymbol() {
        return cacheInitialized ? cachedStarSymbol : config.getString("display.stars.symbol", "★");
    }

    public String getStarFilledColor() {
        return cacheInitialized ? cachedStarFilledColor : config.getString("display.stars.filled_color", "&6");
    }

    public String getStarEmptyColor() {
        return cacheInitialized ? cachedStarEmptyColor : config.getString("display.stars.empty_color", "&7");
    }

    // Economy settings
    public boolean isEconomyEnabled() {
        return config.getBoolean("economy.enabled", true);
    }

    public double getEconomyRewardPerWantedLevel() {
        return config.getDouble("economy.reward_per_wanted_level", 150.0);
    }

    // Debug and validation
    public void validateConfig() {
        plugin.getLogger().info("=== Configuration Validation ===");
        plugin.getLogger().info("Language: " + getLanguage());
        plugin.getLogger().info("Max Wanted Level: " + getMaxWantedLevel());
        plugin.getLogger().info("Jail Time Per Wanted: " + getJailTimePerWanted());
        plugin.getLogger().info("Arrest Distance: " + getArrestDistance());
        plugin.getLogger().info("Compass Update Interval: " + getCompassUpdateInterval());
        plugin.getLogger().info("Handcuff Item: " + getHandcuffItem());
        plugin.getLogger().info("Display Enabled: " + isDisplayEnabled());
        plugin.getLogger().info("Display Mode: " + getDisplayMode());
    }
}
