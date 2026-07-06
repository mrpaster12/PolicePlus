package com.policeplus.managers;

import com.policeplus.PolicePlus;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.scheduler.BukkitRunnable;

public class WantedManager {
    private int pendingSaveTaskId = -1;
    private volatile boolean dirty = false;

    private final PolicePlus plugin;
    private final Map<UUID, Integer> wantedPlayers;
    private final Map<UUID, String> wantedReasons; // UUID -> reason for being wanted
    private final Map<UUID, UUID> arrestedPlayers; // arrested player -> arresting police
    private final File dataFile;
    private final FileConfiguration dataConfig;
    private int autoDecayTaskId = -1;

    public WantedManager(PolicePlus plugin) {
        this.plugin = plugin;
        this.wantedPlayers = new ConcurrentHashMap<>();
        this.wantedReasons = new ConcurrentHashMap<>();
        this.arrestedPlayers = new ConcurrentHashMap<>();
        this.dataFile = new File(plugin.getDataFolder(), "wanted.yml");
        this.dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        loadData();
    }

    public void addWanted(Player player, int level) {
        addWanted(player, level, null);
    }

    public void addWanted(Player player, int level, String reason) {
        int currentLevel = getWantedLevel(player);
        int newLevel = Math.min(currentLevel + level, plugin.getConfigManager().getMaxWantedLevel());
        wantedPlayers.put(player.getUniqueId(), newLevel);
        if (reason != null && !reason.isEmpty()) {
            wantedReasons.put(player.getUniqueId(), reason);
        }
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

    public void setWantedLevel(Player player, int level, String reason) {
        int maxLevel = plugin.getConfigManager().getMaxWantedLevel();
        int finalLevel = Math.min(level, maxLevel);
        wantedPlayers.put(player.getUniqueId(), finalLevel);
        if (reason != null && !reason.isEmpty()) {
            wantedReasons.put(player.getUniqueId(), reason);
        }
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
            wantedReasons.remove(player.getUniqueId());
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

    public String getWantedReason(Player player) {
        return wantedReasons.getOrDefault(player.getUniqueId(), null);
    }

    public String getWantedReason(UUID uuid) {
        return wantedReasons.getOrDefault(uuid, null);
    }

    /**
     * Starts the auto-decay scheduler that decreases wanted levels over time.
     */
    public void startAutoDecay() {
        if (autoDecayTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(autoDecayTaskId);
            autoDecayTaskId = -1;
        }

        if (!plugin.getConfigManager().isWantedAutoRemove()) {
            return;
        }

        long intervalTicks = plugin.getConfigManager().getWantedRemoveIntervalMinutes() * 60L * 20L;

        autoDecayTaskId = new BukkitRunnable() {
            @Override
            public void run() {
                boolean changed = false;
                for (Map.Entry<UUID, Integer> entry : new HashMap<>(wantedPlayers).entrySet()) {
                    UUID uuid = entry.getKey();
                    int currentLevel = entry.getValue();
                    Player onlinePlayer = plugin.getServer().getPlayer(uuid);
                    if (onlinePlayer != null && onlinePlayer.isOnline()) {
                        changed = true;
                        if (currentLevel <= 1) {
                            // Remove wanted entirely
                            wantedPlayers.remove(uuid);
                            wantedReasons.remove(uuid);

                            // Stop compass tracking
                            plugin.getCompassManager().stopTracking(onlinePlayer);

                            String message = plugin.getLanguageManager().getMessage("wanted_removed",
                                    "player", onlinePlayer.getName());
                            plugin.getServer().broadcastMessage(plugin.getLanguageManager().getPrefix() + message);
                        } else {
                            // Decrease by 1
                            wantedPlayers.put(uuid, currentLevel - 1);
                        }

                        // Update visual displays
                        plugin.getDisplayManager().updatePlayerDisplay(onlinePlayer);
                        plugin.getDisplayManager().updateAllPlayersDisplay();
                        plugin.getDisplayManager().updateTargetForAllViewers(onlinePlayer);
                    }
                }
                // Single debounced save after processing all players
                if (changed) {
                    markDirtyAndScheduleSave();
                }
            }
        }.runTaskTimer(plugin, intervalTicks, intervalTicks).getTaskId();

        plugin.getLogger().info("Auto-decay scheduler started with interval: " +
                plugin.getConfigManager().getWantedRemoveIntervalMinutes() + " minutes");
    }

    /**
     * Stops the auto-decay scheduler.
     */
    public void stopAutoDecay() {
        if (autoDecayTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(autoDecayTaskId);
            autoDecayTaskId = -1;
        }
    }

    /**
     * Restarts the auto-decay scheduler (used after config reload).
     */
    public void restartAutoDecay() {
        stopAutoDecay();
        startAutoDecay();
    }

    public void arrestPlayer(Player arrested, Player police) {
        arrestedPlayers.put(arrested.getUniqueId(), police.getUniqueId());
        markDirtyAndScheduleSave();

        // Stop compass tracking for both police and arrested player
        plugin.getCompassManager().stopTracking(police);
        plugin.getCompassManager().stopTracking(arrested);

        // Record arrest in stats
        if (plugin.getStatsManager() != null) {
            plugin.getStatsManager().recordArrest(police);
        }

        String message = plugin.getLanguageManager().getMessage("player_arrested",
                "player", arrested.getName(), "police", police.getName());
        plugin.getServer().broadcastMessage(plugin.getLanguageManager().getPrefix() + message);
    }

    public void releaseArrested(Player player) {
        if (arrestedPlayers.remove(player.getUniqueId()) != null) {
            markDirtyAndScheduleSave();

            // Stop compass tracking for this player
            plugin.getCompassManager().stopTracking(player);

            // Send release notification to cops only (not broadcast to entire server)
            String copMessage = plugin.getLanguageManager().getMessage("jail_release_cop_notify",
                    "player", player.getName());
            for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
                if (PolicePlus.isCop(onlinePlayer)) {
                    onlinePlayer.sendMessage(plugin.getLanguageManager().getPrefix() + copMessage);
                }
            }
            // Send direct message to the released player
            String playerMessage = plugin.getLanguageManager().getMessage("jail_release_player_notify");
            player.sendMessage(plugin.getLanguageManager().getPrefix() + playerMessage);
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
        // Police are immune to automatic wanted level increases (e.g., killing suspects)
        if (PolicePlus.isCop(killer)) {
            return;
        }
        if (plugin.getConfigManager().isWantedOnKill()) {
            addWanted(killer, plugin.getConfigManager().getWantedLevelPerKill());
        }
    }

    private void notifyPoliceAboutWantedPlayer(Player wantedPlayer) {
        // Send beautified multi-line alert to all online cops
        String reason = getWantedReason(wantedPlayer);
        if (reason == null || reason.isEmpty()) {
            reason = "-";
        }
        String alertRaw = plugin.getLanguageManager().getMessage("alerts.wanted_added",
                "player", wantedPlayer.getName(),
                "level", String.valueOf(getWantedLevel(wantedPlayer)),
                "reason", reason);
        // Split multi-line block scalar and send each line
        String[] lines = alertRaw.split("\n");
        for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
            if (PolicePlus.isCop(onlinePlayer) && !onlinePlayer.equals(wantedPlayer)) {
                for (String line : lines) {
                    onlinePlayer.sendMessage(line.replace('&', '§'));
                }
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

            // Load wanted reasons
            if (dataConfig.contains("reasons")) {
                for (String uuidString : dataConfig.getConfigurationSection("reasons").getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(uuidString);
                        String reason = dataConfig.getString("reasons." + uuidString);
                        if (reason != null && !reason.isEmpty()) {
                            wantedReasons.put(uuid, reason);
                        }
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid UUID in reasons section: " + uuidString);
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

        // Save wanted reasons
        Map<UUID, String> reasonsSnapshot = new HashMap<>(wantedReasons);
        dataConfig.set("reasons", null);
        for (Map.Entry<UUID, String> entry : reasonsSnapshot.entrySet()) {
            dataConfig.set("reasons." + entry.getKey().toString(), entry.getValue());
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
