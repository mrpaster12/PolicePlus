package com.policeplugin.managers;

import com.policeplugin.PolicePlugin;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {
    
    private final PolicePlugin plugin;
    private FileConfiguration config;
    
    public ConfigManager(PolicePlugin plugin) {
        this.plugin = plugin;
    }
    
    public void loadConfig() {
        plugin.saveDefaultConfig();
        config = plugin.getConfig();
        plugin.getLogger().info("Configuration loaded successfully");
    }
    
    public void reloadConfig() {
        plugin.reloadConfig();
        config = plugin.getConfig();
        plugin.getLogger().info("Configuration reloaded successfully");
    }
    
    public String getLanguage() {
        String language = config.getString("language", "en");
        plugin.getLogger().info("Language setting: " + language);
        return language;
    }
    
    // Wanted system settings
    public int getMaxWantedLevel() {
        return config.getInt("wanted.max_wanted_level", 5);
    }
    
    public boolean isWantedOnKill() {
        return config.getBoolean("wanted.wanted_on_kill", true);
    }
    
    public int getWantedLevelPerKill() {
        return config.getInt("wanted.wanted_level_per_kill", 1);
    }
    
    public boolean isResetWantedOnDeathEnabled() {
        return config.getBoolean("wanted.reset_on_death.enabled", false);
    }
    
    public int getMaxWantedLevelToResetOnDeath() {
        return config.getInt("wanted.reset_on_death.max_level_to_reset", 3);
    }
    
    // Jail system settings
    public int getJailTimePerWanted() {
        return config.getInt("jail.jail_time_per_wanted", 5);
    }
    
    public int getMaxJailTime() {
        return config.getInt("jail.max_jail_time", 60);
    }
    
    public int getArrestDistance() {
        return config.getInt("jail.arrest_distance", 10);
    }
    
    public boolean isClearWantedOnRelease() {
        return config.getBoolean("jail.clear_wanted_on_release", true);
    }
    
    public int getDefaultJailRadius() {
        return config.getInt("jail.default_jail_radius", 10);
    }
    
    // Compass settings
    public int getCompassUpdateInterval() {
        return config.getInt("compass.update_interval", 20);
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
        return config.getInt("handcuff.max_time", 300);
    }
    
    public boolean isHandcuffAcceptPlainItem() {
        return config.getBoolean("handcuff.accept_plain_item", true);
    }
    
    // Display settings
    public boolean isDisplayEnabled() {
        return config.getBoolean("display.enabled", true);
    }
    
    public String getDisplayMode() {
        return config.getString("display.mode", "stars");
    }
    
    public boolean isShowTabList() {
        return config.getBoolean("display.tablist.enabled", true);
    }
    
    public String getTablistFormatStars() {
        return config.getString("display.tablist.format_stars", " &7[&e{stars}&7]");
    }
    
    public String getTablistFormatNumber() {
        return config.getString("display.tablist.format_number", " &7[&e{level}&7]");
    }
    
    public boolean isShowBelowName() {
        return config.getBoolean("display.below_name.enabled", true);
    }
    
    public String getBelowNameObjective() {
        return config.getString("display.below_name.objective", "wantedLevel");
    }
    
    public String getBelowNameTitle() {
        return config.getString("display.below_name.title", "&eWanted");
    }
    
    public String getStarSymbol() {
        return config.getString("display.stars.symbol", "★");
    }
    
    public String getStarFilledColor() {
        return config.getString("display.stars.filled_color", "&6");
    }
    
    public String getStarEmptyColor() {
        return config.getString("display.stars.empty_color", "&7");
    }
    
    // Salary settings
    public String getSalaryIntervalUnit() {
        return config.getString("salary.interval.unit", "minutes");
    }
    
    public int getSalaryIntervalValue() {
        return config.getInt("salary.interval.value", 10);
    }
    
    public double getSalaryAmountForRank(String rankKey) {
        return config.getDouble("salary.amounts." + rankKey, 0.0);
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
