package com.policeplus.managers;

import com.policeplus.PolicePlus;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RankManager {

    private final PolicePlus plugin;
    private final Map<UUID, String> playerRanks;
    private final Map<UUID, PermissionAttachment> attachments;
    private final File dataFile;
    private final FileConfiguration dataConfig;

    public RankManager(PolicePlus plugin) {
        this.plugin = plugin;
        this.playerRanks = new ConcurrentHashMap<>();
        this.attachments = new ConcurrentHashMap<>();
        this.dataFile = new File(plugin.getDataFolder(), "ranks.yml");
        this.dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        loadData();
    }

    public Set<String> getDefinedRanks() {
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.isConfigurationSection("ranks.permissions")) {
            return new HashSet<>();
        }
        return cfg.getConfigurationSection("ranks.permissions").getKeys(false);
    }

    public String getDisplayNameForRank(String rankKey) {
        String path = "ranks.names." + rankKey;
        return plugin.getConfig().getString(path, rankKey);
    }

    public Map<String, String> getAllRankDisplayNames() {
        Map<String, String> map = new HashMap<>();
        for (String key : getDefinedRanks()) {
            map.put(key, getDisplayNameForRank(key));
        }
        return map;
    }

    public String resolveRankKey(String input) {
        if (input == null)
            return null;
        String normalized = normalize(input);
        // direct key match
        if (isValidRank(normalized))
            return normalized;
        // match by display name (case-insensitive)
        for (Map.Entry<String, String> e : getAllRankDisplayNames().entrySet()) {
            if (e.getValue() != null && normalize(e.getValue()).equals(normalized)) {
                return e.getKey();
            }
        }
        return null;
    }

    private String normalize(String s) {
        return s.trim().toLowerCase().replace(" ", "").replace("_", "").replace("-", "");
    }

    public boolean isValidRank(String rankKey) {
        return getDefinedRanks().contains(rankKey.toLowerCase());
    }

    public void setPlayerRank(Player player, String rankKey) {
        String normalized = rankKey.toLowerCase();
        playerRanks.put(player.getUniqueId(), normalized);
        applyPermissions(player, normalized);
        saveData();
    }

    public String getPlayerRank(Player player) {
        return playerRanks.get(player.getUniqueId());
    }

    public void clearPlayerRank(Player player) {
        playerRanks.remove(player.getUniqueId());
        removePermissions(player);
        saveData();
    }

    public void applyPermissions(Player player, String rankKey) {
        removePermissions(player);
        PermissionAttachment attachment = player.addAttachment(plugin);
        attachments.put(player.getUniqueId(), attachment);

        // Grant configured permissions for rank
        List<String> perms = plugin.getConfig().getStringList("ranks.permissions." + rankKey);
        for (String perm : perms) {
            attachment.setPermission(perm, true);
        }
    }

    public void removePermissions(Player player) {
        PermissionAttachment old = attachments.remove(player.getUniqueId());
        if (old != null) {
            try {
                player.removeAttachment(old);
            } catch (Throwable ignored) {
            }
        }
    }

    public void applyPermissionsIfRanked(Player player) {
        String rank = getPlayerRank(player);
        if (rank != null) {
            applyPermissions(player, rank);
        }
    }

    private void loadData() {
        if (dataFile.exists() && dataConfig.contains("players")) {
            for (String uuidString : dataConfig.getConfigurationSection("players").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidString);
                    String rank = dataConfig.getString("players." + uuidString);
                    if (rank != null) {
                        playerRanks.put(uuid, rank);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        // Re-apply for online players on reload
        for (Player p : Bukkit.getOnlinePlayers()) {
            applyPermissionsIfRanked(p);
        }
    }

    public void saveData() {
        dataConfig.set("players", null);
        for (Map.Entry<UUID, String> e : playerRanks.entrySet()) {
            dataConfig.set("players." + e.getKey().toString(), e.getValue());
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save ranks data: " + ex.getMessage());
        }
    }
}
