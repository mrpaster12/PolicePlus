package com.policeplugin;

import com.policeplugin.commands.*;
import com.policeplugin.listeners.*;
import com.policeplugin.managers.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;

public class PolicePlugin extends JavaPlugin {
    
    private static PolicePlugin instance;
    private ConfigManager configManager;
    private LanguageManager languageManager;
    private WantedManager wantedManager;
    private JailManager jailManager;
    private CompassManager compassManager;
    private DisplayManager displayManager;
    private HandcuffManager handcuffManager;
    
    @Override
    public void onEnable() {
        instance = this;
        
        getLogger().info("=== Police Plugin Starting ===");
        getLogger().info("Version: " + getDescription().getVersion());
        getLogger().info("API Version: " + getDescription().getAPIVersion());
        
        try {
            // Initialize managers in order
            initializeManagers();
            
            // Load configurations
            loadConfigurations();
            
            // Register commands and listeners
            registerCommands();
            registerListeners();
            
            // Start systems
            startSystems();
            
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

        // Register tab completers using a single reusable instance
        PoliceTabCompleter sharedCompleter = new PoliceTabCompleter(this);
        getCommand("wanted").setTabCompleter(sharedCompleter);
        getCommand("police").setTabCompleter(sharedCompleter);
        getCommand("compass").setTabCompleter(sharedCompleter);
        getCommand("jail").setTabCompleter(sharedCompleter);
        
        // Handcuff commands
        try {
            getCommand("cuffe").setExecutor(new HandcuffCommand(this));
            getCommand("uncuffe").setExecutor(new HandcuffCommand(this));
            getCommand("cuffe").setTabCompleter(sharedCompleter);
            getCommand("uncuffe").setTabCompleter(sharedCompleter);
            getLogger().info("Handcuff commands registered successfully");
        } catch (Exception e) {
            getLogger().warning("Failed to register handcuff commands: " + e.getMessage());
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
        
        // Start display manager
        displayManager.start();
        
        // Force refresh all displays to ensure existing players get updated
        displayManager.forceRefreshAllDisplays();
        
        // Start handcuff timeout checker
        startHandcuffTimeoutChecker();
        
        getLogger().info("All systems started successfully");
    }
    
    private void registerPlaceholderAPI() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                Object expansion = new PlaceholderHook(this);
                expansion.getClass().getMethod("register").invoke(expansion);
                getLogger().info("PlaceholderAPI detected: registered placeholders %police_wanted_level% and %police_wanted_stars%.");
            } catch (Throwable t) {
                getLogger().warning("PlaceholderAPI detected but could not register expansion: " + t.getMessage());
            }
        } else {
            getLogger().info("PlaceholderAPI not detected - placeholders will not be available");
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
            
            // Stop systems
            if (jailManager != null) {
                jailManager.stop();
                getLogger().info("Jail system stopped");
            }
            
            if (displayManager != null) {
                displayManager.stop();
                getLogger().info("Display system stopped");
            }
            
            getLogger().info("=== Police Plugin Shut Down Successfully ===");
            
        } catch (Exception e) {
            getLogger().severe("Error during shutdown: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void startHandcuffTimeoutChecker() {
        getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
            if (handcuffManager != null) {
                handcuffManager.checkTimeouts();
            }
        }, 20L, 20L);
        getLogger().info("Handcuff timeout checker started");
    }
    
    // Getter methods
    public static PolicePlugin getInstance() {
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
