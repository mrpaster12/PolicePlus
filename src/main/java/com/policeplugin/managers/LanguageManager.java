package com.policeplugin.managers;

import com.policeplugin.PolicePlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class LanguageManager {
    
    private final PolicePlugin plugin;
    private FileConfiguration languageConfig;
    private String currentLanguage;
    private String cachedPrefix;
    // Final, effective messages after merging defaults + language file
    private final Map<String, String> messages = new HashMap<>();
    // Built-in defaults so messages always exist even if file is broken/missing
    private final Map<String, String> defaultMessages = new HashMap<>();
    
    public LanguageManager(PolicePlugin plugin) {
        this.plugin = plugin;
    }
    
    public void loadLanguage() {
        currentLanguage = plugin.getConfigManager().getLanguage();
        plugin.getLogger().info("Loading language: " + currentLanguage);
        
        // Prepare defaults and clear state
        buildDefaultMessages();
        messages.clear();
        cachedPrefix = null;
        
        loadLanguageFile(currentLanguage);
        
        // Merge: start with defaults, then overlay file values
        messages.putAll(defaultMessages);
        if (languageConfig != null) {
            Set<String> keys = languageConfig.getKeys(true);
            for (String key : keys) {
                if (languageConfig.isConfigurationSection(key)) continue;
                String val = languageConfig.getString(key);
                if (val != null) {
                    messages.put(key, val);
                }
            }
            // Backfill missing defaults into file so server owners see them
            try {
                boolean changed = false;
                for (Map.Entry<String, String> e : defaultMessages.entrySet()) {
                    if (!languageConfig.isSet(e.getKey())) {
                        languageConfig.set(e.getKey(), e.getValue());
                        changed = true;
                    }
                }
                if (changed) {
                    File out = new File(plugin.getDataFolder(), "languages/" + currentLanguage + ".yml");
                    languageConfig.save(out);
                }
            } catch (Exception saveErr) {
                plugin.getLogger().warning("Could not write missing default messages to language file: " + saveErr.getMessage());
            }
        }

        // Cache prefix (already colorized in getter)
        cachedPrefix = colorize(messages.getOrDefault("prefix", "&8[&cPolice&8] &r"));
        plugin.getLogger().info("Language loaded successfully: " + currentLanguage);
    }
    
    private void loadLanguageFile(String language) {
        File languageFile = new File(plugin.getDataFolder(), "languages/" + language + ".yml");
        
        // Create language directory if it doesn't exist
        if (!languageFile.getParentFile().exists()) {
            languageFile.getParentFile().mkdirs();
        }
        
        // Save default language file if it doesn't exist
        if (!languageFile.exists()) {
            plugin.getLogger().info("Language file not found, saving default: " + languageFile.getPath());
            plugin.saveResource("languages/" + language + ".yml", false);
        }
        
        // Load with UTF-8 support
        try (InputStreamReader reader = new InputStreamReader(
                new java.io.FileInputStream(languageFile), StandardCharsets.UTF_8)) {
            languageConfig = YamlConfiguration.loadConfiguration(reader);
            plugin.getLogger().info("Successfully loaded language file: " + language + ".yml");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load language file with UTF-8: " + e.getMessage());
            
            // Try fallback loading
            try {
                languageConfig = YamlConfiguration.loadConfiguration(languageFile);
                plugin.getLogger().warning("Loaded language file with fallback method");
            } catch (Exception e2) {
                plugin.getLogger().severe("Failed to load language file with fallback: " + e2.getMessage());
                
                // Try loading from resources as last resort
                try {
                    InputStream resourceStream = plugin.getResource("languages/" + language + ".yml");
                    if (resourceStream != null) {
                        languageConfig = YamlConfiguration.loadConfiguration(
                            new InputStreamReader(resourceStream, StandardCharsets.UTF_8));
                        plugin.getLogger().info("Loaded language file from resources as fallback");
                    } else {
                        plugin.getLogger().severe("Could not find language file in resources: " + language + ".yml");
                        // Load default English as emergency fallback
                        loadEmergencyFallback();
                    }
                } catch (Exception e3) {
                    plugin.getLogger().severe("Complete failure loading language file: " + e3.getMessage());
                    loadEmergencyFallback();
                }
            }
        }
        
        // Validate loaded configuration
        if (languageConfig != null) {
            validateLanguageFile();
        }
    }
    
    private void loadEmergencyFallback() {
        plugin.getLogger().warning("Loading emergency fallback language configuration");
        try {
            InputStream resourceStream = plugin.getResource("languages/en.yml");
            if (resourceStream != null) {
                languageConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(resourceStream, StandardCharsets.UTF_8));
                currentLanguage = "en";
                plugin.getLogger().info("Emergency fallback loaded: English");
            } else {
                plugin.getLogger().severe("No emergency fallback available!");
                createBasicLanguageConfig();
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load emergency fallback: " + e.getMessage());
            createBasicLanguageConfig();
        }
    }
    
    private void createBasicLanguageConfig() {
        plugin.getLogger().warning("Creating basic language configuration");
        languageConfig = new YamlConfiguration();
        
        // Add essential messages
        languageConfig.set("prefix", "&8[&cPolice&8] &r");
        languageConfig.set("no_permission", "&cYou don't have permission to use this command!");
        languageConfig.set("player_not_found", "&cPlayer not found!");
        languageConfig.set("wanted_set", "&c{player} wanted level set to: {level}");
        languageConfig.set("wanted_added", "&c{player} is now wanted! Level: {level}");
        languageConfig.set("wanted_removed", "&a{player} is no longer wanted!");
        
        currentLanguage = "fallback";
        plugin.getLogger().info("Basic language configuration created");
    }
    
    private void validateLanguageFile() {
        if (languageConfig == null) return;
        
        // Check for essential messages
        String[] essentialMessages = {
            "prefix", "no_permission", "player_not_found", 
            "wanted_set", "wanted_added", "wanted_removed"
        };
        
        for (String message : essentialMessages) {
            if (languageConfig.getString(message) == null) {
                plugin.getLogger().warning("Missing essential message: " + message);
            }
        }
        
        plugin.getLogger().info("Language validation complete. Available keys: " + languageConfig.getKeys(true).size());
    }
    
    public String getMessage(String path) {
        if (path == null || path.isEmpty()) {
            return "Invalid message path";
        }
        String raw = messages.get(path);
        if (raw == null) {
            plugin.getLogger().warning("Missing message key '" + path + "' in language '" + currentLanguage + "'");
            // fall back to default if exists, else a readable placeholder
            raw = defaultMessages.getOrDefault(path, "[missing:" + path + "]");
        }
        return colorize(raw);
    }
    
    public String getMessage(String path, String... replacements) {
        if (path == null || path.isEmpty()) {
            return "Invalid message path";
        }
        
        String message = getMessage(path);
        
        // Apply replacements
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length && replacements[i] != null) {
                message = message.replace("{" + replacements[i] + "}", replacements[i + 1]);
            }
        }
        
        return message;
    }
    
    public String getPrefix() {
        if (cachedPrefix != null) {
            return cachedPrefix;
        }
        
        String prefix = getMessage("prefix");
        if (prefix != null) {
            cachedPrefix = prefix;
        }
        
        return cachedPrefix != null ? cachedPrefix : colorize("&8[&cPolice&8] &r");
    }
    
    public void reloadLanguage() {
        plugin.getLogger().info("Reloading language configuration...");
        loadLanguage();
    }
    
    public String getCurrentLanguage() {
        return currentLanguage;
    }
    
    public void debugLanguage() {
        plugin.getLogger().info("=== Language Debug Info ===");
        plugin.getLogger().info("Current Language: " + currentLanguage);
        plugin.getLogger().info("Language Config: " + (languageConfig != null ? "Loaded" : "NULL"));
        plugin.getLogger().info("Cached Prefix: " + cachedPrefix);
        plugin.getLogger().info("Messages map size: " + messages.size());
        
        if (languageConfig != null) {
            plugin.getLogger().info("Available keys: " + languageConfig.getKeys(true).size());
            // Log first 10 keys for debugging
            int count = 0;
            for (String key : languageConfig.getKeys(true)) {
                if (!languageConfig.isConfigurationSection(key) && count < 10) {
                    plugin.getLogger().info("  " + key + ": " + languageConfig.getString(key));
                    count++;
                }
            }
            if (count >= 10) {
                plugin.getLogger().info("  ... and " + (languageConfig.getKeys(true).size() - 10) + " more keys");
            }
        }
    }
    
    public boolean isLanguageLoaded() {
        return languageConfig != null;
    }
    
    public int getLoadedMessageCount() {
        return languageConfig != null ? languageConfig.getKeys(true).size() : 0;
    }

    // ---------- helpers ----------
    private void buildDefaultMessages() {
        defaultMessages.clear();
        // Load ALL defaults from bundled English file so every key exists
        try (InputStreamReader reader = new InputStreamReader(
                plugin.getResource("languages/en.yml"), StandardCharsets.UTF_8)) {
            if (reader != null) {
                YamlConfiguration def = YamlConfiguration.loadConfiguration(reader);
                for (String key : def.getKeys(true)) {
                    if (def.isConfigurationSection(key)) continue;
                    String val = def.getString(key);
                    if (val != null) defaultMessages.put(key, val);
                }
                return; // success
            }
        } catch (Exception ignored) {
            // fall through to minimal hardcoded defaults
        }

        // Minimal built-ins as last resort
        defaultMessages.put("prefix", "&8[&cPolice&8] &r");
        defaultMessages.put("no_permission", "&cYou don't have permission to use this command!");
        defaultMessages.put("player_not_found", "&cPlayer not found!");
        defaultMessages.put("wanted_set", "&c{player} wanted level set to: {level}");
        defaultMessages.put("wanted_added", "&c{player} is now wanted! Level: {level}");
        defaultMessages.put("wanted_removed", "&a{player} is no longer wanted!");
        defaultMessages.put("wanted_list_header", "&6=== Wanted Players ===");
        defaultMessages.put("wanted_list_empty", "&eNo players are currently wanted.");
        defaultMessages.put("wanted_list_entry", "&c{player} - Level: {level}");
        defaultMessages.put("jail_time_set", "&c{player} has been jailed for {time} minutes!");
        defaultMessages.put("jail_no_jails", "&cNo jails have been created!");
        defaultMessages.put("player_arrested", "&c{player} has been arrested by {police}!");
        defaultMessages.put("player_arrested_success", "&aPlayer successfully arrested! You can now jail them.");
    }

    private String colorize(String input) {
        return input == null ? null : input.replace("&", "§");
    }
}
