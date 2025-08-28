package com.policeplugin.managers;

import com.policeplugin.PolicePlugin;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class JailManager {
    
    private final PolicePlugin plugin;
    private final Map<UUID, JailData> jailedPlayers; // UUID -> JailData
    private final Map<String, JailInfo> jails; // jail_name -> JailInfo
    private final File dataFile;
    private final FileConfiguration dataConfig;
    private int pendingSaveTaskId = -1;
    private boolean dirty = false;
    private int actionbarTaskId = -1;
    
    public static class JailInfo {
        private final String name;
        private final String id;
        private Location location;
        private Location spawnLocation;
        private int radius;
        
        public JailInfo(String name, String id, Location location) {
            this.name = name;
            this.id = id;
            this.location = location;
            this.radius = 10; // Default radius
        }
        
        public String getName() { return name; }
        public String getId() { return id; }
        public Location getLocation() { return location; }
        public void setLocation(Location location) { this.location = location; }
        public Location getSpawnLocation() { return spawnLocation; }
        public void setSpawnLocation(Location spawnLocation) { this.spawnLocation = spawnLocation; }
        public int getRadius() { return radius; }
        public void setRadius(int radius) { this.radius = radius; }
    }
    
    public static class JailData {
        private final String jailName;
        private final long releaseTime;
        
        public JailData(String jailName, long releaseTime) {
            this.jailName = jailName;
            this.releaseTime = releaseTime;
        }
        
        public String getJailName() { return jailName; }
        public long getReleaseTime() { return releaseTime; }
    }
    
    public JailManager(PolicePlugin plugin) {
        this.plugin = plugin;
        this.jailedPlayers = new HashMap<>();
        this.jails = new HashMap<>();
        this.dataFile = new File(plugin.getDataFolder(), "jail.yml");
        this.dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        loadData();
        startActionBarTask();
    }
    
    public boolean createJail(String name, String id, Location location) {
        if (jails.containsKey(name)) {
            return false; // Jail already exists
        }
        
        JailInfo jailInfo = new JailInfo(name, id, location);
        jails.put(name, jailInfo);
        markDirtyAndScheduleSave();
        return true;
    }
    
    public boolean deleteJail(String name) {
        if (!jails.containsKey(name)) {
            return false; // Jail doesn't exist
        }
        
        jails.remove(name);
        markDirtyAndScheduleSave();
        return true;
    }
    
    public JailInfo getJail(String name) {
        return jails.get(name);
    }
    
    public Map<String, JailInfo> getAllJails() {
        return new HashMap<>(jails);
    }
    
    public boolean setJailSpawn(String jailName, Location spawnLocation) {
        JailInfo jail = jails.get(jailName);
        if (jail == null) {
            return false;
        }
        
        jail.setSpawnLocation(spawnLocation);
        markDirtyAndScheduleSave();
        return true;
    }
    
    public void jailPlayer(Player player, String jailName, int minutes) {
        JailInfo jail = jails.get(jailName);
        if (jail == null) {
            return; // Jail doesn't exist
        }
        
        // Return handcuff to police if player was cuffed
        Player cuffer = plugin.getHandcuffManager().getCuffer(player);
        if (cuffer != null && plugin.getHandcuffManager().isCuffed(player)) {
            // Give handcuff item back to the police officer
            org.bukkit.inventory.ItemStack handcuffItem = plugin.getHandcuffManager().createHandcuffItem();
            java.util.HashMap<Integer, org.bukkit.inventory.ItemStack> leftover = cuffer.getInventory().addItem(handcuffItem);
            
            // If inventory is full, drop the item at police location
            if (!leftover.isEmpty()) {
                cuffer.getWorld().dropItemNaturally(cuffer.getLocation(), handcuffItem);
                cuffer.sendMessage(plugin.getLanguageManager().getPrefix() + 
                    plugin.getLanguageManager().getMessage("handcuff.returned_dropped"));
            } else {
                cuffer.sendMessage(plugin.getLanguageManager().getPrefix() + 
                    plugin.getLanguageManager().getMessage("handcuff.returned_inventory"));
            }
            
            // Remove cuff status
            plugin.getHandcuffManager().uncuffPlayer(player);
        }
        
        // Clear any cuff immobilization effects upon jailing
        try {
            player.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOW);
            player.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOW_DIGGING);
            player.removePotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS);
            player.setWalkSpeed(0.2f);
            player.setFlySpeed(0.1f);
        } catch (Throwable ignored) {}

        long releaseTime = System.currentTimeMillis() + (minutes * 60 * 1000L);
        JailData jailData = new JailData(jailName, releaseTime);
        jailedPlayers.put(player.getUniqueId(), jailData);
        saveData();
        
        // Teleport to jail
        player.teleport(jail.getLocation());
        
        String message = plugin.getLanguageManager().getMessage("jail_time_set", 
            "player", player.getName(), "time", String.valueOf(minutes));
        plugin.getServer().broadcastMessage(plugin.getLanguageManager().getPrefix() + message);
        
        // Update display for the jailed player
        plugin.getDisplayManager().updatePlayerDisplay(player);
    }
    
    public void jailPlayerByWantedLevel(Player player, String jailName) {
        int wantedLevel = plugin.getWantedManager().getWantedLevel(player);
        int jailTime = wantedLevel * plugin.getConfigManager().getJailTimePerWanted();
        jailTime = Math.min(jailTime, plugin.getConfigManager().getMaxJailTime());
        
        jailPlayer(player, jailName, jailTime);
    }
    
    public void releasePlayer(Player player) {
        JailData jailData = jailedPlayers.remove(player.getUniqueId());
        if (jailData != null) {
            markDirtyAndScheduleSave();
            
            // Clear wanted level if configured
            if (plugin.getConfigManager().isClearWantedOnRelease()) {
                plugin.getWantedManager().removeWanted(player);
                player.sendMessage(plugin.getLanguageManager().getPrefix() + 
                    plugin.getLanguageManager().getMessage("jail_wanted_cleared"));
            }
            
            // Safety: ensure movement speeds are reset on release
            try {
                player.setWalkSpeed(0.2f);
                player.setFlySpeed(0.1f);
            } catch (Throwable ignored) {}

            // Teleport to spawn location if available
            JailInfo jail = jails.get(jailData.getJailName());
            if (jail != null && jail.getSpawnLocation() != null) {
                player.teleport(jail.getSpawnLocation());
            }
            
            String message = plugin.getLanguageManager().getMessage("player_unjailed", 
                "player", player.getName());
            plugin.getServer().broadcastMessage(plugin.getLanguageManager().getPrefix() + message);
            
            // Update display for the released player
            plugin.getDisplayManager().updatePlayerDisplay(player);
        }
    }
    
    public boolean isJailed(Player player) {
        if (!jailedPlayers.containsKey(player.getUniqueId())) {
            return false;
        }
        
        JailData jailData = jailedPlayers.get(player.getUniqueId());
        if (System.currentTimeMillis() >= jailData.getReleaseTime()) {
            // Auto-release
            releasePlayer(player);
            return false;
        }
        
        return true;
    }
    
    public long getRemainingTime(Player player) {
        if (!isJailed(player)) {
            return 0;
        }
        
        JailData jailData = jailedPlayers.get(player.getUniqueId());
        return Math.max(0, jailData.getReleaseTime() - System.currentTimeMillis());
    }
    
    public String getJailName(Player player) {
        if (!isJailed(player)) {
            return null;
        }
        
        JailData jailData = jailedPlayers.get(player.getUniqueId());
        return jailData.getJailName();
    }
    
    public boolean canArrestPlayer(Player police, Player target) {
        int maxDistance = plugin.getConfigManager().getArrestDistance();
        double distance = police.getLocation().distance(target.getLocation());
        return distance <= maxDistance;
    }
    
    public Map<UUID, JailData> getJailedPlayers() {
        return new HashMap<>(jailedPlayers);
    }

    public String getLeastPopulatedJailName() {
        if (jails.isEmpty()) return null;
        Map<String, Integer> counts = new HashMap<>();
        for (String name : jails.keySet()) {
            counts.put(name, 0);
        }
        for (JailData data : jailedPlayers.values()) {
            if (data.getJailName() != null) {
                counts.computeIfPresent(data.getJailName(), (k, v) -> v + 1);
            }
        }
        String best = null;
        int bestCount = Integer.MAX_VALUE;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (e.getValue() < bestCount) {
                best = e.getKey();
                bestCount = e.getValue();
            }
        }
        return best;
    }
    
    private void loadData() {
        if (dataFile.exists()) {
            // Load jails
            if (dataConfig.contains("jails")) {
                for (String jailName : dataConfig.getConfigurationSection("jails").getKeys(false)) {
                    String id = dataConfig.getString("jails." + jailName + ".id");
                    Location location = loadLocation("jails." + jailName + ".location");
                    Location spawnLocation = loadLocation("jails." + jailName + ".spawn");
                    int radius = dataConfig.getInt("jails." + jailName + ".radius", 10);
                    
                    if (location != null) {
                        JailInfo jailInfo = new JailInfo(jailName, id, location);
                        jailInfo.setSpawnLocation(spawnLocation);
                        jailInfo.setRadius(radius);
                        jails.put(jailName, jailInfo);
                    }
                }
            }
            
            // Load jailed players
            if (dataConfig.contains("jailed_players")) {
                for (String uuidString : dataConfig.getConfigurationSection("jailed_players").getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(uuidString);
                        String jailName = dataConfig.getString("jailed_players." + uuidString + ".jail_name");
                        long releaseTime = dataConfig.getLong("jailed_players." + uuidString + ".release_time");
                        
                        JailData jailData = new JailData(jailName, releaseTime);
                        jailedPlayers.put(uuid, jailData);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid UUID in jail.yml: " + uuidString);
                    }
                }
            }
        }
    }
    
    private Location loadLocation(String path) {
        if (!dataConfig.contains(path)) {
            return null;
        }
        
        String worldName = dataConfig.getString(path + ".world");
        double x = dataConfig.getDouble(path + ".x");
        double y = dataConfig.getDouble(path + ".y");
        double z = dataConfig.getDouble(path + ".z");
        float yaw = (float) dataConfig.getDouble(path + ".yaw", 0);
        float pitch = (float) dataConfig.getDouble(path + ".pitch", 0);
        
        if (plugin.getServer().getWorld(worldName) != null) {
            return new Location(plugin.getServer().getWorld(worldName), x, y, z, yaw, pitch);
        }
        
        return null;
    }
    
    private void saveLocation(String path, Location location) {
        if (location != null) {
            dataConfig.set(path + ".world", location.getWorld().getName());
            dataConfig.set(path + ".x", location.getX());
            dataConfig.set(path + ".y", location.getY());
            dataConfig.set(path + ".z", location.getZ());
            dataConfig.set(path + ".yaw", location.getYaw());
            dataConfig.set(path + ".pitch", location.getPitch());
        }
    }
    
    public void saveData() {
        // Take snapshots to avoid concurrent modification while saving asynchronously
        Map<String, JailInfo> jailsSnapshot = new HashMap<>(jails);
        Map<UUID, JailData> jailedSnapshot = new HashMap<>(jailedPlayers);

        // Save jails
        for (JailInfo jail : jailsSnapshot.values()) {
            String path = "jails." + jail.getName();
            dataConfig.set(path + ".id", jail.getId());
            saveLocation(path + ".location", jail.getLocation());
            saveLocation(path + ".spawn", jail.getSpawnLocation());
            dataConfig.set(path + ".radius", jail.getRadius());
        }

        // Save jailed players
        for (Map.Entry<UUID, JailData> entry : jailedSnapshot.entrySet()) {
            String path = "jailed_players." + entry.getKey().toString();
            dataConfig.set(path + ".jail_name", entry.getValue().getJailName());
            dataConfig.set(path + ".release_time", entry.getValue().getReleaseTime());
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save jail data: " + e.getMessage());
        }
    }

    private void markDirtyAndScheduleSave() {
        dirty = true;
        if (pendingSaveTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(pendingSaveTaskId);
        }
        pendingSaveTaskId = plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                if (dirty) {
                    dirty = false;
                    saveData();
                }
            }
        }, 40L).getTaskId();
    }

    private void startActionBarTask() {
        if (actionbarTaskId != -1) return;
        actionbarTaskId = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!player.isOnline()) continue;
                    if (isJailed(player)) {
                        long remainingMs = getRemainingTime(player);
                        long totalSeconds = Math.max(0, remainingMs / 1000);
                        long minutes = totalSeconds / 60;
                        long seconds = totalSeconds % 60;
                        String msg = plugin.getLanguageManager().getMessage("jail_actionbar_remaining",
                            "minutes", String.valueOf(minutes),
                            "seconds", String.valueOf(seconds));
                        // Send action bar (bottom of screen)
                        player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                            net.md_5.bungee.api.chat.TextComponent.fromLegacyText(
                                plugin.getLanguageManager().getPrefix() + msg
                            ));
                    }
                }
            }
        }, 0L, 20L).getTaskId(); // update every second
    }

    public void stop() {
        if (actionbarTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(actionbarTaskId);
            actionbarTaskId = -1;
        }
        if (pendingSaveTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(pendingSaveTaskId);
            pendingSaveTaskId = -1;
        }
    }
}
