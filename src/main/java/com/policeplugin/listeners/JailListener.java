package com.policeplugin.listeners;

import com.policeplugin.PolicePlugin;
import com.policeplugin.managers.JailManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public class JailListener implements Listener {
    
    private final PolicePlugin plugin;
    
    public JailListener(PolicePlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        
        if (plugin.getJailManager().isJailed(player) && !player.hasPermission("policeplugin.bypass")) {
            event.setCancelled(true);
            player.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("jail_block_break_denied"));
        }
    }
    
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        
        if (plugin.getJailManager().isJailed(player) && !player.hasPermission("policeplugin.bypass")) {
            event.setCancelled(true);
            player.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("jail_block_place_denied"));
        }
    }
    
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        
        if (plugin.getJailManager().isJailed(player) && !player.hasPermission("policeplugin.bypass")) {
            if (event.getTo() == null || event.getFrom() == null) {
                return;
            }
            if (event.getTo().getWorld() == null || event.getFrom().getWorld() == null) {
                return;
            }
            String jailName = plugin.getJailManager().getJailName(player);
            if (jailName != null) {
                JailManager.JailInfo jail = plugin.getJailManager().getJail(jailName);
                if (jail != null) {
                    Location jailLocation = jail.getLocation();
                    if (jailLocation == null || jailLocation.getWorld() == null) {
                        return;
                    }
                    if (!event.getTo().getWorld().equals(jailLocation.getWorld())) {
                        // If player changed world, force-teleport to jail world location
                        player.teleport(jailLocation);
                        return;
                    }
                    double distance = event.getTo().distance(jailLocation);
                    int jailRadius = jail.getRadius();
                    
                    if (distance > jailRadius) {
                        // Teleport back to jail
                        player.teleport(jailLocation);
                        player.sendMessage(plugin.getLanguageManager().getPrefix() + 
                            plugin.getLanguageManager().getMessage("jail_area_leave_denied"));
                    }
                }
            }
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        // cuffs removed
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        // cuffs removed
    }
}
