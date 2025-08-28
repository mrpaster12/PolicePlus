package com.policeplugin.listeners;

import com.policeplugin.PolicePlugin;
import com.policeplugin.gui.PoliceGUI;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class CompassListener implements Listener {
    
    private final PolicePlugin plugin;
    
    public CompassListener(PolicePlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();
        
        if (title.equals(plugin.getLanguageManager().getMessage("gui_police_title"))) {
            event.setCancelled(true);
            
            if (event.isRightClick()) {
                PoliceGUI.handleRightClick(player, event.getRawSlot(), plugin);
            } else {
                PoliceGUI.handleClick(player, event.getRawSlot(), plugin);
            }
        }
    }
    
    @EventHandler
    public void onCompassUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        if (item != null && item.getType() == Material.COMPASS) {
            Player target = plugin.getCompassManager().getCompassTarget(player);
            
            if (target != null) {
                String message = plugin.getLanguageManager().getMessage("compass_tracking", 
                    "player", target.getName());
                player.sendMessage(plugin.getLanguageManager().getPrefix() + message);
            } else {
                String message = plugin.getLanguageManager().getMessage("compass_no_target");
                player.sendMessage(plugin.getLanguageManager().getPrefix() + message);
            }
        }
    }
}
