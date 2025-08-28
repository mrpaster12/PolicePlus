package com.policeplugin.commands;

import com.policeplugin.PolicePlugin;
import com.policeplugin.gui.PoliceGUI;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class WantedCommand implements CommandExecutor {
    
    private final PolicePlugin plugin;
    
    public WantedCommand(PolicePlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Everyone can check their own wanted level with "/wanted"
        if (args.length == 0) {
            // Show player's own wanted level
            if (sender instanceof Player) {
                Player player = (Player) sender;
                int wantedLevel = plugin.getWantedManager().getWantedLevel(player);
                sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                    plugin.getLanguageManager().getMessage("wanted_self_level", "level", String.valueOf(wantedLevel)));
            } else {
                sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                    plugin.getLanguageManager().getMessage("usage_wanted"));
            }
            return true;
        }
        
        // Subcommands require permission
        if (!sender.hasPermission("policeplugin.wanted")) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("no_permission"));
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "set":
                handleSet(sender, args);
                break;
                
            case "list":
                showWantedList(sender);
                break;
                
            case "arrest":
                handleArrest(sender, args);
                break;
                
            case "jail":
                handleJailPlayer(sender, args);
                break;

            case "unjail":
                handleUnjail(sender, args);
                break;
                
            case "reload":
                handleReload(sender);
                break;
                
            // debug subcommand removed
                
            case "help":
                showWantedHelp(sender);
                break;
                
            case "gui":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                        plugin.getLanguageManager().getMessage("usage_wanted_player_only"));
                    return true;
                }
                Player player = (Player) sender;
                PoliceGUI.openPoliceGUI(player, plugin);
                break;
                
            default:
                sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                    plugin.getLanguageManager().getMessage("usage_wanted_unknown"));
                break;
        }
        
        return true;
    }
    
    private void handleSet(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("usage_wanted_set"));
            return;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("player_not_found"));
            return;
        }
        
        int level = 0;
        if (args.length >= 3) {
            try {
                level = Integer.parseInt(args[2]);
                if (level < 0) {
                    sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                        plugin.getLanguageManager().getMessage("usage_wanted_invalid_level"));
                    return;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                    plugin.getLanguageManager().getMessage("usage_wanted_invalid_level"));
                return;
            }
        }
        
        if (level == 0) {
            // Remove wanted level
            plugin.getWantedManager().removeWanted(target);
        } else {
            // Set wanted level
            plugin.getWantedManager().setWantedLevel(target, level);
        }
    }
    
    // remove subcommand not used currently
    
    private void handleArrest(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("usage_wanted_player_only"));
            return;
        }
        
        if (args.length < 2) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("usage_wanted_arrest"));
            return;
        }
        
        Player police = (Player) sender;
        Player target = Bukkit.getPlayer(args[1]);
        
        if (target == null) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("player_not_found"));
            return;
        }
        
        // Check if player is wanted
        if (!plugin.getWantedManager().isWanted(target)) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("player_not_wanted"));
            return;
        }
        
        // Check if player is already arrested
        if (plugin.getWantedManager().isArrested(target)) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("player_already_arrested"));
            return;
        }
        
        // Check arrest distance
        if (!plugin.getJailManager().canArrestPlayer(police, target)) {
            int distance = plugin.getConfigManager().getArrestDistance();
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("jail_too_far", "distance", String.valueOf(distance)));
            return;
        }
        
        plugin.getWantedManager().arrestPlayer(target, police);
    }
    
    // jail create/delete/spawn/list/help moved to /jail command
    
    private void handleJailPlayer(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("usage_wanted_player_only"));
            return;
        }
        
        if (args.length < 2) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("usage_wanted_jail_jail"));
            return;
        }
        
        Player police = (Player) sender;
        Player target = Bukkit.getPlayer(args[1]);
        
        if (target == null) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("player_not_found"));
            return;
        }
        
        // Check if player is arrested
        if (!plugin.getWantedManager().isArrested(target)) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("player_not_arrested"));
            return;
        }
        
        // Check if police is the one who arrested the player
        Player arrestingPolice = plugin.getWantedManager().getArrestingPolice(target);
        if (arrestingPolice == null || !arrestingPolice.equals(police)) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("not_arresting_police"));
            return;
        }
        
        // Check arrest distance
        if (!plugin.getJailManager().canArrestPlayer(police, target)) {
            int distance = plugin.getConfigManager().getArrestDistance();
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("jail_too_far", "distance", String.valueOf(distance)));
            return;
        }
        
        // Get first available jail
        var jails = plugin.getJailManager().getAllJails();
        if (jails.isEmpty()) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("jail_no_jails"));
            return;
        }
        
        String jailName = jails.keySet().iterator().next();
        plugin.getJailManager().jailPlayerByWantedLevel(target, jailName);
        
        // Clear wanted level and release from arrest
        plugin.getWantedManager().removeWanted(target);
        plugin.getWantedManager().releaseArrested(target);
        
        // Stop compass tracking and remove boss bar
        plugin.getCompassManager().stopTracking(police);
        
        sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
            plugin.getLanguageManager().getMessage("player_jailed"));
    }
    
    private void handleUnjail(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("usage_wanted_unjail"));
            return;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("player_not_found"));
            return;
        }
        
        plugin.getJailManager().releasePlayer(target);
        sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
            plugin.getLanguageManager().getMessage("player_unjailed"));
    }
    
    // jail management moved to JailCommand
    
    // jail management moved to JailCommand
    
    // jail management moved to JailCommand
    
    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("policeplugin.admin")) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("no_permission"));
            return;
        }
        try {
            // Reload both config and language files
            plugin.getConfigManager().reloadConfig();
            plugin.getLanguageManager().loadLanguage();
            
            // Also refresh all displays to ensure consistency
            plugin.getDisplayManager().forceRefreshAllDisplays();
            
            // Reload handcuff settings so name/lore/material changes apply without restart
            try {
                plugin.getHandcuffManager().reloadConfig();
            } catch (Throwable t) {
                plugin.getLogger().warning("Could not reload handcuff settings: " + t.getMessage());
            }
            
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("wanted_reload_success"));
        } catch (Exception e) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("wanted_reload_error"));
            plugin.getLogger().severe("Error during reload: " + e.getMessage());
        }
    }

    // removed reloadlang/refresh subcommands; use reload

    // debug subcommand removed
    
    private void showWantedList(CommandSender sender) {
        var wantedPlayers = plugin.getWantedManager().getWantedPlayers();
        
        sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
            plugin.getLanguageManager().getMessage("wanted_list_header"));
        
        if (wantedPlayers.isEmpty()) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("wanted_list_empty"));
        } else {
            for (var entry : wantedPlayers.entrySet()) {
                String playerName = Bukkit.getOfflinePlayer(entry.getKey()).getName();
                if (playerName != null) {
                    String message = plugin.getLanguageManager().getMessage("wanted_list_entry", 
                        "player", playerName, "level", String.valueOf(entry.getValue()));
                    sender.sendMessage(message);
                }
            }
        }
    }
    
    private void showWantedHelp(CommandSender sender) {
        String prefix = plugin.getLanguageManager().getPrefix();
        
        sender.sendMessage(prefix + plugin.getLanguageManager().getMessage("wanted_help_title"));
        sender.sendMessage("");
        
        // Basic wanted commands
        sender.sendMessage(prefix + plugin.getLanguageManager().getMessage("wanted_help_self"));
        sender.sendMessage("");
        
        if (sender.hasPermission("policeplugin.wanted")) {
            sender.sendMessage(prefix + plugin.getLanguageManager().getMessage("wanted_help_set"));
            sender.sendMessage(prefix + plugin.getLanguageManager().getMessage("wanted_help_list"));
            sender.sendMessage(prefix + plugin.getLanguageManager().getMessage("wanted_help_jail"));
            sender.sendMessage(prefix + plugin.getLanguageManager().getMessage("wanted_help_unjail"));
            sender.sendMessage(prefix + plugin.getLanguageManager().getMessage("wanted_help_gui"));
            sender.sendMessage("");
        }
        
        // Admin commands
        if (sender.hasPermission("policeplugin.admin")) {
            sender.sendMessage(prefix + plugin.getLanguageManager().getMessage("wanted_help_reload"));
            sender.sendMessage(prefix + plugin.getLanguageManager().getMessage("wanted_help_reloadlang"));
            sender.sendMessage(prefix + plugin.getLanguageManager().getMessage("wanted_help_refresh"));
            // debug help removed
            sender.sendMessage("");
        }
        
        // Help command
        sender.sendMessage(prefix + plugin.getLanguageManager().getMessage("wanted_help_help"));
    }
    
    // jail management moved to JailCommand
    
    // jail management moved to JailCommand
}
