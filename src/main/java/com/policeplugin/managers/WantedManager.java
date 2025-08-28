package com.policeplugin.managers;

import com.policeplugin.PolicePlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WantedManager {
    private int pendingSaveTaskId = -1;
    private boolean dirty = false;
    
    private final PolicePlugin plugin;
    private final Map<UUID, Integer> wantedPlayers;
    private final Map<UUID, UUID> arrestedPlayers; // arrested player -> arresting police
    private final File dataFile;
    private final FileConfiguration dataConfig;
    
    public WantedManager(PolicePlugin plugin) {
        this.plugin = plugin;
        this.wantedPlayers = new HashMap<>();
        this.arrestedPlayers = new HashMap<>();
        this.dataFile = new File(plugin.getDataFolder(), "wanted.yml");
        this.dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        loadData();
    }
    
    public void addWanted(Player player, int level) {
        int currentLevel = getWantedLevel(player);
        int newLevel = Math.min(currentLevel + level, plugin.getConfigManager().getMaxWantedLevel());
        wantedPlayers.put(player.getUniqueId(), newLevel);
        markDirtyAndScheduleSave();
        
        String message = plugin.getLanguageManager().getMessage("wanted_added", 
            "player", player.getName(), "level", String.valueOf(newLevel));
        plugin.getServer().broadcastMessage(plugin.getLanguageManager().getPrefix() + message);
        
        // Notify all online police about the new wanted player
        notifyPoliceAboutWantedPlayer(player);

        // Update visual displays
        plugin.getDisplayManager().updatePlayerDisplay(player);
        plugin.getDisplayManager().updateAllPlayersDisplay();
        plugin.getDisplayManager().updateTargetForAllViewers(player);
    }
    
    public void setWantedLevel(Player player, int level) {
        int maxLevel = plugin.getConfigManager().getMaxWantedLevel();
        int finalLevel = Math.min(level, maxLevel);
        wantedPlayers.put(player.getUniqueId(), finalLevel);
        markDirtyAndScheduleSave();
        
        String message = plugin.getLanguageManager().getMessage("wanted_set", 
            "player", player.getName(), "level", String.valueOf(finalLevel));
        plugin.getServer().broadcastMessage(plugin.getLanguageManager().getPrefix() + message);

        // Update visual displays
        plugin.getDisplayManager().updatePlayerDisplay(player);
        plugin.getDisplayManager().updateAllPlayersDisplay();
        plugin.getDisplayManager().updateTargetForAllViewers(player);
    }
    
    public void removeWanted(Player player) {
        if (wantedPlayers.remove(player.getUniqueId()) != null) {
            markDirtyAndScheduleSave();
            
            // Stop compass tracking for this player
            plugin.getCompassManager().stopTracking(player);
            
            String message = plugin.getLanguageManager().getMessage("wanted_removed", 
                "player", player.getName());
            plugin.getServer().broadcastMessage(plugin.getLanguageManager().getPrefix() + message);

            // Update visual displays
            plugin.getDisplayManager().updatePlayerDisplay(player);
            plugin.getDisplayManager().updateAllPlayersDisplay();
            plugin.getDisplayManager().updateTargetForAllViewers(player);
        }
    }
    
    public boolean isWanted(Player player) {
        return wantedPlayers.containsKey(player.getUniqueId());
    }
    
    public int getWantedLevel(Player player) {
        return wantedPlayers.getOrDefault(player.getUniqueId(), 0);
    }
    
    public Map<UUID, Integer> getWantedPlayers() {
        return new HashMap<>(wantedPlayers);
    }
    
    public void arrestPlayer(Player arrested, Player police) {
        arrestedPlayers.put(arrested.getUniqueId(), police.getUniqueId());
        markDirtyAndScheduleSave();
        
        // Stop compass tracking for both police and arrested player
        plugin.getCompassManager().stopTracking(police);
        plugin.getCompassManager().stopTracking(arrested);
        
        String message = plugin.getLanguageManager().getMessage("player_arrested", 
            "player", arrested.getName(), "police", police.getName());
        plugin.getServer().broadcastMessage(plugin.getLanguageManager().getPrefix() + message);
    }
    
    public void releaseArrested(Player player) {
        if (arrestedPlayers.remove(player.getUniqueId()) != null) {
            saveData();
            
            // Stop compass tracking for this player
            plugin.getCompassManager().stopTracking(player);
            
            String message = plugin.getLanguageManager().getMessage("player_released_arrest", 
                "player", player.getName());
            plugin.getServer().broadcastMessage(plugin.getLanguageManager().getPrefix() + message);
        }
    }
    
    public boolean isArrested(Player player) {
        return arrestedPlayers.containsKey(player.getUniqueId());
    }
    
    public Player getArrestingPolice(Player arrested) {
        UUID policeUUID = arrestedPlayers.get(arrested.getUniqueId());
        if (policeUUID != null) {
            return plugin.getServer().getPlayer(policeUUID);
        }
        return null;
    }
    
    public boolean isArrestedBy(Player arrested, Player police) {
        UUID policeUUID = arrestedPlayers.get(arrested.getUniqueId());
        return policeUUID != null && policeUUID.equals(police.getUniqueId());
    }
    
    public Map<UUID, UUID> getArrestedPlayers() {
        return new HashMap<>(arrestedPlayers);
    }
    
    public void onPlayerKill(Player killer, Player victim) {
        if (plugin.getConfigManager().isWantedOnKill()) {
            addWanted(killer, plugin.getConfigManager().getWantedLevelPerKill());
        }
    }
    
    private void notifyPoliceAboutWantedPlayer(Player wantedPlayer) {
        // Notify all online players with police permission
        for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
            if (onlinePlayer.hasPermission("policeplugin.police") && !onlinePlayer.equals(wantedPlayer)) {
                String message = plugin.getLanguageManager().getMessage("wanted_police_notification", 
                    "player", wantedPlayer.getName(), "level", String.valueOf(getWantedLevel(wantedPlayer)));
                onlinePlayer.sendMessage(plugin.getLanguageManager().getPrefix() + message);
            }
        }
    }
    
    private void loadData() {
        if (dataFile.exists()) {
            // Load wanted players
            if (dataConfig.contains("wanted")) {
                for (String uuidString : dataConfig.getConfigurationSection("wanted").getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(uuidString);
                        int level = dataConfig.getInt("wanted." + uuidString);
                        wantedPlayers.put(uuid, level);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid UUID in wanted.yml: " + uuidString);
                    }
                }
            }
            
            // Load arrested players
            if (dataConfig.contains("arrested")) {
                for (String uuidString : dataConfig.getConfigurationSection("arrested").getKeys(false)) {
                    try {
                        UUID arrestedUUID = UUID.fromString(uuidString);
                        String policeUUIDString = dataConfig.getString("arrested." + uuidString);
                        UUID policeUUID = UUID.fromString(policeUUIDString);
                        arrestedPlayers.put(arrestedUUID, policeUUID);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid UUID in arrested section: " + uuidString);
                    }
                }
            }
        }
    }
    
    public void saveData() {
        // Take snapshots to avoid concurrent modification while saving asynchronously
        Map<UUID, Integer> wantedSnapshot = new HashMap<>(wantedPlayers);
        Map<UUID, UUID> arrestedSnapshot = new HashMap<>(arrestedPlayers);

        // Save wanted players
        dataConfig.set("wanted", null);
        for (Map.Entry<UUID, Integer> entry : wantedSnapshot.entrySet()) {
            dataConfig.set("wanted." + entry.getKey().toString(), entry.getValue());
        }

        // Save arrested players
        dataConfig.set("arrested", null);
        for (Map.Entry<UUID, UUID> entry : arrestedSnapshot.entrySet()) {
            dataConfig.set("arrested." + entry.getKey().toString(), entry.getValue().toString());
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save wanted data: " + e.getMessage());
        }
    }

    private void markDirtyAndScheduleSave() {
        dirty = true;
        if (pendingSaveTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(pendingSaveTaskId);
        }
        // coalesce multiple updates within short window into one async disk write
        pendingSaveTaskId = plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                if (dirty) {
                    dirty = false;
                    saveData();
                }
            }
        }, 40L).getTaskId(); // ~2 seconds delay
    }
}
