package com.policeplugin.gui;

import com.policeplugin.PolicePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class PoliceGUI {
    
    public static void openPoliceGUI(Player player, PolicePlugin plugin) {
        Inventory gui = Bukkit.createInventory(null, 54, 
            plugin.getLanguageManager().getMessage("gui_police_title"));
        
        // Get wanted players
        var wantedPlayers = plugin.getWantedManager().getWantedPlayers();
        var arrestedPlayers = plugin.getWantedManager().getArrestedPlayers();
        int slot = 0;
        
        for (var entry : wantedPlayers.entrySet()) {
            if (slot >= 45) break; // Max 45 players in GUI
            
            String playerName = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            if (playerName != null) {
                ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) skull.getItemMeta();
                
                boolean isArrested = arrestedPlayers.containsKey(entry.getKey());
                
                if (isArrested && plugin.getWantedManager().isArrestedBy(Bukkit.getPlayer(entry.getKey()), player)) {
                    // Player is arrested by this police - show jail option
                    meta.setDisplayName("§6" + playerName + " §7(" + plugin.getLanguageManager().getMessage("status_arrested") + ")");
                    
                    List<String> lore = new ArrayList<>();
                    lore.add(plugin.getLanguageManager().getMessage("lore_wanted_level", "level", String.valueOf(entry.getValue())));
                    lore.add(plugin.getLanguageManager().getMessage("lore_status_arrested_by_you"));
                    lore.add(plugin.getLanguageManager().getMessage("lore_click_to_jail"));
                    
                    // Keep lore concise; jail availability will be checked on click
                    
                    meta.setLore(lore);
                    // Store target UUID and state in PDC
                    PersistentDataContainer pdc = meta.getPersistentDataContainer();
                    pdc.set(new NamespacedKey(plugin, "target_uuid"), PersistentDataType.STRING, entry.getKey().toString());
                    pdc.set(new NamespacedKey(plugin, "state"), PersistentDataType.STRING, "arrested_by_you");
                    skull.setItemMeta(meta);
                    gui.setItem(slot, skull);
                    slot++;
                } else if (!isArrested) {
                    // Player is wanted but not arrested - show arrest option
                    meta.setDisplayName("§c" + playerName);
                    
                    List<String> lore = new ArrayList<>();
                    lore.add(plugin.getLanguageManager().getMessage("lore_wanted_level", "level", String.valueOf(entry.getValue())));
                    lore.add(plugin.getLanguageManager().getMessage("gui_click_to_arrest_or_track"));
                    
                    meta.setLore(lore);
                    // Store target UUID and state in PDC
                    PersistentDataContainer pdc = meta.getPersistentDataContainer();
                    pdc.set(new NamespacedKey(plugin, "target_uuid"), PersistentDataType.STRING, entry.getKey().toString());
                    pdc.set(new NamespacedKey(plugin, "state"), PersistentDataType.STRING, "wanted");
                    skull.setItemMeta(meta);
                    gui.setItem(slot, skull);
                    slot++;
                }
                // Note: Players arrested by other police are not shown in GUI
            }
        }
        
        // Add control buttons
        ItemStack infoItem = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName(plugin.getLanguageManager().getMessage("info_title_police"));
        List<String> infoLore = new ArrayList<>();
        infoLore.add(plugin.getLanguageManager().getMessage("info_total_wanted_players", "count", String.valueOf(wantedPlayers.size())));
        infoLore.add(plugin.getLanguageManager().getMessage("info_use_compass"));
        infoLore.add(plugin.getLanguageManager().getMessage("info_click_player_heads"));
        infoLore.add(plugin.getLanguageManager().getMessage("info_max_arrest_distance", "distance", String.valueOf(plugin.getConfigManager().getArrestDistance())));
        infoLore.add(plugin.getLanguageManager().getMessage("info_compass_tracking", "status", (plugin.getCompassManager().isTracking(player) ? "§a" + plugin.getLanguageManager().getMessage("status_active") : "§c" + plugin.getLanguageManager().getMessage("status_inactive"))));
        infoMeta.setLore(infoLore);
        infoItem.setItemMeta(infoMeta);
        gui.setItem(49, infoItem);
        
        player.openInventory(gui);
    }
    
    public static void handleClick(Player player, int slot, PolicePlugin plugin) {
        if (slot >= 45) return; // Only player heads are clickable
        
        Inventory inv = player.getOpenInventory().getTopInventory();
        ItemStack clicked = inv.getItem(slot);
        
        if (clicked == null || clicked.getType() != Material.PLAYER_HEAD) return;
        
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String uuidStr = pdc.get(new NamespacedKey(plugin, "target_uuid"), PersistentDataType.STRING);
        // state is stored for potential future use (e.g., different click actions),
        // but current logic derives state from runtime checks
        // String state = pdc.get(new NamespacedKey(plugin, "state"), PersistentDataType.STRING);
        if (uuidStr == null) return;
        Player target = Bukkit.getPlayer(java.util.UUID.fromString(uuidStr));
        
        if (target == null) {
            player.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("player_not_found"));
            return;
        }
        
        // If arrested by this police, allow jailing; otherwise only track via compass
        if (plugin.getWantedManager().isArrested(target) && plugin.getWantedManager().isArrestedBy(target, player)) {
            handleJailArrestedPlayer(player, target, plugin);
        } else {
            // Always just (re)target compass here; arrest must be via cuffs/commands
            if (plugin.getCompassManager().isTracking(player)) {
                Player current = plugin.getCompassManager().getCompassTarget(player);
                if (current == null || !current.equals(target)) {
                    plugin.getCompassManager().removeCompass(player);
                    plugin.getCompassManager().giveCompass(player, target);
                }
            } else {
                plugin.getCompassManager().giveCompass(player, target);
            }
        }
        
        player.closeInventory();
    }
    
    // Removed direct arrest flow; arrest is handled via external cuff plugins or other mechanics
    
    private static void handleJailArrestedPlayer(Player police, Player target, PolicePlugin plugin) {
        // Check arrest distance
        if (!plugin.getJailManager().canArrestPlayer(police, target)) {
            int distance = plugin.getConfigManager().getArrestDistance();
            police.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("jail_too_far", "distance", String.valueOf(distance)));
            return;
        }
        
        // Get available jails
        var jails = plugin.getJailManager().getAllJails();
        if (jails.isEmpty()) {
            police.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("jail_no_jails"));
            return;
        }
        
        // Use first available jail
        String jailName = jails.keySet().iterator().next();
        
        // Jail the player based on wanted level
        plugin.getJailManager().jailPlayerByWantedLevel(target, jailName);
        
        // Clear wanted level and release from arrest
        plugin.getWantedManager().removeWanted(target);
        plugin.getWantedManager().releaseArrested(target);
        
        // Stop compass tracking and remove boss bar for the police
        plugin.getCompassManager().stopTracking(police);
        
        // Also stop compass tracking for the target player if they have any
        plugin.getCompassManager().stopTracking(target);
        
        police.sendMessage(plugin.getLanguageManager().getPrefix() + 
            plugin.getLanguageManager().getMessage("player_jailed_success"));
    }
    
    public static void handleRightClick(Player player, int slot, PolicePlugin plugin) {
        if (slot >= 45) return; // Only player heads are clickable
        
        Inventory inv = player.getOpenInventory().getTopInventory();
        ItemStack clicked = inv.getItem(slot);
        
        if (clicked == null || clicked.getType() != Material.PLAYER_HEAD) return;
        
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;
        
        String displayName = meta.getDisplayName();
        String playerName;
        
        // Check if player is arrested (has "§6" color and "(Arrested)" suffix)
        if (displayName.contains("§6") && displayName.contains("(Arrested)")) {
            // Don't allow right-click for arrested players
            return;
        } else {
            // Extract player name from normal format: "§cPlayerName"
            playerName = displayName.substring(2);
        }
        
        Player target = Bukkit.getPlayer(playerName);
        
        if (target == null) {
            player.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("player_not_found"));
            return;
        }
        
        // Give compass to track the player (same as left-click when not arrested)
        if (target != null) {
            plugin.getCompassManager().giveCompass(player, target);
            player.closeInventory();
        }
    }
}
