package com.policeplus.listeners;

import com.policeplus.PolicePlus;
import com.policeplus.gui.PoliceGUI;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class CompassListener implements Listener {
    
    private final PolicePlus plugin;
    
    public CompassListener(PolicePlus plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Safety: prevent -999 index crash when clicking outside GUI boundary
        if (event.getClickedInventory() == null) return;
        if (event.getSlot() < 0) return;

        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();
        
        // Handle multi-step jailing GUIs (Duration, Type, Jail Selection)
        if (PoliceGUI.handleSubGUIClick(player, title, event.getSlot(), plugin)) {
            event.setCancelled(true);
            return;
        }

        // Handle original Police GUI
        if (title.equals(plugin.getLanguageManager().getMessage("gui_police_title"))) {
            event.setCancelled(true);

            if (event.isRightClick()) {
                PoliceGUI.handleRightClick(player, event.getSlot(), plugin);
            } else {
                PoliceGUI.handleClick(player, event.getSlot(), plugin);
            }
        }
    }

    /**
     * Intercepts chat messages when a cop is in the custom duration input flow.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChatInput(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (PoliceGUI.isAwaitingCustomInput(player)) {
            event.setCancelled(true);
            // Must sync to main thread for GUI operations
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                PoliceGUI.handleChatInput(player, event.getMessage(), plugin);
            });
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
