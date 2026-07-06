package com.policeplus.managers;

import com.policeplus.PolicePlus;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CompassManager {

    private final PolicePlus plugin;
    private final Map<UUID, UUID> compassTargets; // Player UUID -> Target UUID
    private final Map<UUID, Integer> compassTasks; // Player UUID -> Task ID

    public CompassManager(PolicePlus plugin) {
        this.plugin = plugin;
        this.compassTargets = new ConcurrentHashMap<>();
        this.compassTasks = new ConcurrentHashMap<>();
    }

    /**
     * Cancels ALL active compass tracking tasks and clears state.
     * Should be called on plugin reload to prevent duplicate tasks.
     */
    public void stopAll() {
        for (Integer taskId : compassTasks.values()) {
            if (taskId != null) {
                plugin.getServer().getScheduler().cancelTask(taskId);
            }
        }
        compassTasks.clear();
        compassTargets.clear();
    }

    public void giveCompass(Player player, Player target) {
        compassTargets.put(player.getUniqueId(), target.getUniqueId());

        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();
        meta.setDisplayName("§a§lCompass - " + target.getName());
        compass.setItemMeta(meta);

        player.getInventory().addItem(compass);

        // Start compass tracking
        startCompassTracking(player, target);

        String message = plugin.getLanguageManager().getMessage("compass_given",
                "player", target.getName());
        player.sendMessage(plugin.getLanguageManager().getPrefix() + message);
    }

    public void startTrackingWithoutCompass(Player player, Player target) {
        compassTargets.put(player.getUniqueId(), target.getUniqueId());

        // Start compass tracking without giving compass item
        startCompassTracking(player, target);

        String message = plugin.getLanguageManager().getMessage("compass_tracking_started",
                "player", target.getName());
        player.sendMessage(plugin.getLanguageManager().getPrefix() + message);
    }

    public void removeCompass(Player player) {
        UUID playerUUID = player.getUniqueId();
        compassTargets.remove(playerUUID);

        // Stop compass tracking
        Integer taskId = compassTasks.remove(playerUUID);
        if (taskId != null) {
            plugin.getServer().getScheduler().cancelTask(taskId);
        }

        // Remove compass from inventory
        player.getInventory().remove(Material.COMPASS);
    }

    public Player getCompassTarget(Player player) {
        UUID targetUUID = compassTargets.get(player.getUniqueId());
        if (targetUUID != null) {
            return plugin.getServer().getPlayer(targetUUID);
        }
        return null;
    }

    public boolean isTracking(Player player) {
        return compassTargets.containsKey(player.getUniqueId());
    }

    private void startCompassTracking(Player player, Player target) {
        int taskId = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !target.isOnline()) {
                    removeCompass(player);
                    return;
                }

                // Update compass direction
                Location playerLoc = player.getLocation();
                Location targetLoc = target.getLocation();

                if (playerLoc.getWorld().equals(targetLoc.getWorld())) {
                    player.setCompassTarget(targetLoc);

                    // Update boss bar with distance
                    double distance = playerLoc.distance(targetLoc);
                    if (distance <= plugin.getConfigManager().getCompassMaxDistance()) {
                        updateBossBar(player, target, distance);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, plugin.getConfigManager().getCompassUpdateInterval()).getTaskId();

        compassTasks.put(player.getUniqueId(), taskId);
    }

    private final Map<UUID, org.bukkit.boss.BossBar> playerBossBars = new ConcurrentHashMap<>();

    private void updateBossBar(Player player, Player target, double distance) {
        // Get target coordinates
        Location targetLoc = target.getLocation();
        String coordinates = String.format("X: %.0f, Y: %.0f, Z: %.0f",
                targetLoc.getX(), targetLoc.getY(), targetLoc.getZ());

        String title = plugin.getLanguageManager().getMessage("compass_distance_with_coords",
                "distance", String.valueOf((int) distance), "coords", coordinates);

        // Create or update boss bar
        org.bukkit.boss.BossBar bossBar = playerBossBars.get(player.getUniqueId());
        if (bossBar == null) {
            bossBar = plugin.getServer().createBossBar(
                    title,
                    org.bukkit.boss.BarColor.BLUE,
                    org.bukkit.boss.BarStyle.SOLID);
            bossBar.addPlayer(player);
            playerBossBars.put(player.getUniqueId(), bossBar);
        } else {
            bossBar.setTitle(title);
        }

        // Calculate progress based on distance (closer = higher progress)
        double maxDistance = plugin.getConfigManager().getCompassMaxDistance();
        double progress = Math.max(0.1, 1.0 - (distance / maxDistance));
        bossBar.setProgress(progress);
    }

    public void removeBossBar(Player player) {
        org.bukkit.boss.BossBar bossBar = playerBossBars.remove(player.getUniqueId());
        if (bossBar != null) {
            bossBar.removePlayer(player);
            bossBar.removeAll();
        }
    }

    public void cleanupPlayer(Player player) {
        removeCompass(player);
        removeBossBar(player);
    }

    public void stopTracking(Player player) {
        cleanupPlayer(player);

        // Reset compass target to spawn
        if (player.isOnline()) {
            player.setCompassTarget(player.getWorld().getSpawnLocation());
        }
    }
}
