package com.policeplugin.listeners;

import com.policeplugin.PolicePlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class PlayerListener implements Listener {
    
    private final PolicePlugin plugin;
    
    public PlayerListener(PolicePlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        
        if (killer != null && killer != victim) {
            plugin.getWantedManager().onPlayerKill(killer, victim);
        }

        // Reset victim's wanted on death based on config
        if (plugin.getConfigManager().isResetWantedOnDeathEnabled() && plugin.getWantedManager().isWanted(victim)) {
            int threshold = plugin.getConfigManager().getMaxWantedLevelToResetOnDeath();
            int victimLevel = plugin.getWantedManager().getWantedLevel(victim);
            boolean shouldReset = threshold < 0 || victimLevel <= threshold;
            if (shouldReset) {
                plugin.getWantedManager().removeWanted(victim);
            }
        }
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Check if player is jailed
        if (plugin.getJailManager().isJailed(player)) {
            long remainingTime = plugin.getJailManager().getRemainingTime(player);
            int minutes = (int) (remainingTime / (1000 * 60));
            
            String message = plugin.getLanguageManager().getMessage("jail_time_remaining", 
                "time", String.valueOf(minutes));
            player.sendMessage(plugin.getLanguageManager().getPrefix() + message);
        }

        // Initialize all below-name scores on viewer's board and update player's own display
        plugin.getDisplayManager().initializeViewerScores(player);
        plugin.getDisplayManager().updatePlayerDisplay(player);
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Cleanup compass and boss bar
        plugin.getCompassManager().cleanupPlayer(player);

        // Cuffs removed

        // Optional: nothing specific to cleanup for display; tablist name resets on leave
    }
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        
        // Check if player is jailed and trying to break/place blocks
        if (plugin.getJailManager().isJailed(player) && !player.hasPermission("policeplugin.bypass")) {
            ItemStack item = event.getItem();
            
            if (item != null && (item.getType().name().contains("PICKAXE") || 
                                item.getType().name().contains("AXE") || 
                                item.getType().name().contains("SHOVEL") ||
                                item.getType().name().contains("HOE"))) {
                event.setCancelled(true);
                return;
            }
            
            // Cancel block breaking/placing
            if (event.getAction().name().contains("LEFT_CLICK") || 
                event.getAction().name().contains("RIGHT_CLICK")) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player)) return;
        // Prevent double trigger (main hand only)
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player police = event.getPlayer();
        Player target = (Player) event.getRightClicked();

        // Check cuff item in hand
        org.bukkit.inventory.ItemStack item = police.getInventory().getItemInMainHand();
        if (item == null || item.getType() == org.bukkit.Material.AIR) return;
        String matName = plugin.getConfig().getString("handcuff.item.material", "BLAZE_ROD");
        org.bukkit.Material cuffMat = org.bukkit.Material.matchMaterial(matName);
        if (cuffMat == null) cuffMat = org.bukkit.Material.BLAZE_ROD;
        if (item.getType() != cuffMat) return;

        // Permission check: police rank or cuffe.use
        if (!police.hasPermission("policeplugin.cuffe.use") && !police.hasPermission("policeplugin.police")) {
            police.sendMessage(plugin.getLanguageManager().getPrefix() + plugin.getLanguageManager().getMessage("no_permission"));
            return;
        }

        // Range check same as arrest distance
        if (!plugin.getJailManager().canArrestPlayer(police, target)) {
            int distance = plugin.getConfigManager().getArrestDistance();
            police.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("jail_too_far", "distance", String.valueOf(distance)));
            return;
        }

        // Require target to have at least 1 wanted level
        if (!plugin.getWantedManager().isWanted(target)) {
            police.sendMessage(plugin.getLanguageManager().getPrefix() + plugin.getLanguageManager().getMessage("player_not_wanted"));
            return;
        }

        // Apply cuff state and owner
        target.setMetadata("cuffed", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
        target.setMetadata("cuffed_by", new org.bukkit.metadata.FixedMetadataValue(plugin, police.getUniqueId().toString()));
        target.addScoreboardTag("cuffed");

        // Notify both players
        police.sendMessage(plugin.getLanguageManager().getPrefix() + plugin.getLanguageManager().getMessage("cuff_cuffed_notify_police", "player", target.getName()));
        target.sendMessage(plugin.getLanguageManager().getPrefix() + plugin.getLanguageManager().getMessage("cuff_cuffed_notify_target", "police", police.getName()));

        // If within arrest distance, arrest immediately
        if (!plugin.getWantedManager().isArrested(target) && plugin.getJailManager().canArrestPlayer(police, target)) {
            plugin.getWantedManager().arrestPlayer(target, police);
            police.sendMessage(plugin.getLanguageManager().getPrefix() + plugin.getLanguageManager().getMessage("player_arrested_success"));
        }
    }
}
