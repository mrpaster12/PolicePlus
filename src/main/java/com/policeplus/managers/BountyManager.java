package com.policeplus.managers;

import com.policeplus.PolicePlus;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BountyManager {
    private final PolicePlus plugin;
    private final Map<UUID, Double> bounties; // Player UUID → Bounty amount
    private final Map<UUID, String> bountyReasons;
    private final Map<UUID, UUID> bountyIssuers; // Player UUID → Issuer UUID
    private final File dataFile;
    private final FileConfiguration dataConfig;
    private int pendingSaveTaskId = -1;
    private volatile boolean dirty = false;

    public BountyManager(PolicePlus plugin) {
        this.plugin = plugin;
        this.bounties = new ConcurrentHashMap<>();
        this.bountyReasons = new ConcurrentHashMap<>();
        this.bountyIssuers = new ConcurrentHashMap<>();
        this.dataFile = new File(plugin.getDataFolder(), "bounties.yml");
        this.dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        loadData();
    }

    public void setBounty(Player issuer, Player target, double amount, String reason) {
        UUID targetUUID = target.getUniqueId();
        bounties.put(targetUUID, amount);
        bountyReasons.put(targetUUID, reason != null ? reason : "");
        bountyIssuers.put(targetUUID, issuer.getUniqueId());
        markDirtyAndScheduleSave();

        // Log the bounty
        plugin.getLogManager().logBountyChange(issuer, target, amount, reason);

        // Record bounty issuance in stats
        if (plugin.getStatsManager() != null) {
            plugin.getStatsManager().recordBountyIssued(issuer, amount);
        }

        // Notify all online players
        String message = plugin.getLanguageManager().getMessage("bounty_set",
                "player", target.getName(),
                "amount", formatCurrency(amount),
                "issuer", issuer.getName());
        Bukkit.broadcastMessage(plugin.getLanguageManager().getPrefix() + message);
    }

    public void increaseBounty(Player issuer, Player target, double amount, String reason) {
        UUID targetUUID = target.getUniqueId();
        double currentBounty = bounties.getOrDefault(targetUUID, 0.0);
        double newBounty = currentBounty + amount;
        setBounty(issuer, target, newBounty, reason);
    }

    public void removeBounty(Player target) {
        UUID targetUUID = target.getUniqueId();
        if (bounties.remove(targetUUID) != null) {
            bountyReasons.remove(targetUUID);
            bountyIssuers.remove(targetUUID);
            markDirtyAndScheduleSave();

            String message = plugin.getLanguageManager().getMessage("bounty_removed",
                    "player", target.getName());
            Bukkit.broadcastMessage(plugin.getLanguageManager().getPrefix() + message);
        }
    }

    /**
     * Silently removes a bounty from a player without broadcasting.
     * Used when a bounty is claimed via kill to avoid redundant broadcast messages.
     */
    public void removeBountySilently(Player target) {
        UUID targetUUID = target.getUniqueId();
        if (bounties.remove(targetUUID) != null) {
            bountyReasons.remove(targetUUID);
            bountyIssuers.remove(targetUUID);
            markDirtyAndScheduleSave();
        }
    }

    public double getBounty(Player player) {
        return bounties.getOrDefault(player.getUniqueId(), 0.0);
    }

    public boolean hasBounty(Player player) {
        return bounties.containsKey(player.getUniqueId());
    }

    public String getBountyReason(Player player) {
        return bountyReasons.getOrDefault(player.getUniqueId(), "");
    }

    public Player getBountyIssuer(Player target) {
        UUID issuerUUID = bountyIssuers.get(target.getUniqueId());
        return issuerUUID != null ? Bukkit.getPlayer(issuerUUID) : null;
    }

    public Map<UUID, Double> getAllBounties() {
        return new HashMap<>(bounties);
    }

    public List<Map.Entry<String, Double>> getSortedBounties() {
        List<Map.Entry<String, Double>> list = new ArrayList<>();
        for (Map.Entry<UUID, Double> entry : bounties.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                list.add(new AbstractMap.SimpleEntry<>(player.getName(), entry.getValue()));
            }
        }
        list.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        return list;
    }

    public String formatCurrency(double amount) {
        return String.format("%.2f", amount);
    }

    /**
     * Add a bounty amount to a player's head by UUID.
     * If the player already has a bounty, the amount is added to the existing bounty.
     */
    public void addBounty(UUID targetUUID, double amount) {
        double current = bounties.getOrDefault(targetUUID, 0.0);
        bounties.put(targetUUID, current + amount);
        markDirtyAndScheduleSave();
    }

    /**
     * Remove a bounty by player UUID.
     */
    public void removeBounty(UUID targetUUID) {
        if (bounties.remove(targetUUID) != null) {
            bountyReasons.remove(targetUUID);
            bountyIssuers.remove(targetUUID);
            markDirtyAndScheduleSave();
        }
    }

    /**
     * Returns a formatted list of all active bounties for display.
     */
    public List<String> getFormattedBountyList() {
        List<String> list = new ArrayList<>();
        for (Map.Entry<UUID, Double> entry : bounties.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                String reason = bountyReasons.getOrDefault(entry.getKey(), "");
                String issuerName = "Unknown";
                UUID issuerUUID = bountyIssuers.get(entry.getKey());
                if (issuerUUID != null) {
                    Player issuer = Bukkit.getPlayer(issuerUUID);
                    if (issuer != null) issuerName = issuer.getName();
                }
                String line = "§c" + player.getName() + " §7- §e$" + formatCurrency(entry.getValue());
                if (reason != null && !reason.isEmpty()) {
                    line += " §7| §f" + reason;
                }
                line += " §7| by §6" + issuerName;
                list.add(line);
            }
        }
        return list;
    }

    private void loadData() {
        if (dataFile.exists() && dataConfig.contains("bounties")) {
            for (String uuidString : dataConfig.getConfigurationSection("bounties").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidString);
                    double amount = dataConfig.getDouble("bounties." + uuidString + ".amount", 0);
                    String reason = dataConfig.getString("bounties." + uuidString + ".reason", "");
                    String issuerString = dataConfig.getString("bounties." + uuidString + ".issuer");

                    bounties.put(uuid, amount);
                    bountyReasons.put(uuid, reason);
                    if (issuerString != null) {
                        bountyIssuers.put(uuid, UUID.fromString(issuerString));
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in bounties.yml: " + uuidString);
                }
            }
        }
    }

    public void saveData() {
        Map<UUID, Double> snapshot = new HashMap<>(bounties);

        dataConfig.set("bounties", null);
        for (Map.Entry<UUID, Double> entry : snapshot.entrySet()) {
            String basePath = "bounties." + entry.getKey().toString();
            dataConfig.set(basePath + ".amount", entry.getValue());
            dataConfig.set(basePath + ".reason", bountyReasons.get(entry.getKey()));
            UUID issuer = bountyIssuers.get(entry.getKey());
            if (issuer != null) {
                dataConfig.set(basePath + ".issuer", issuer.toString());
            }
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save bounties data: " + e.getMessage());
        }
    }

    private void markDirtyAndScheduleSave() {
        dirty = true;
        if (pendingSaveTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(pendingSaveTaskId);
        }
        pendingSaveTaskId = plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            if (dirty) {
                dirty = false;
                saveData();
            }
        }, 40L).getTaskId();
    }
}
