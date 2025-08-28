package com.policeplugin.commands;

import com.policeplugin.PolicePlugin;
import com.policeplugin.gui.PoliceGUI;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PoliceCommand implements CommandExecutor {
    
    private final PolicePlugin plugin;
    
    public PoliceCommand(PolicePlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Rank system removed

        // Subcommand: /police jail — jail the player you arrested into the least populated jail
        if (args.length >= 1 && args[0].equalsIgnoreCase("jail")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                    plugin.getLanguageManager().getMessage("usage_wanted_player_only"));
                return true;
            }
            Player police = (Player) sender;
            // Find the arrested player for this police
            var arrestedMap = plugin.getWantedManager().getArrestedPlayers();
            Player target = null;
            for (var entry : arrestedMap.entrySet()) {
                if (entry.getValue().equals(police.getUniqueId())) {
                    target = Bukkit.getPlayer(entry.getKey());
                    break;
                }
            }
            if (target == null) {
                sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                    plugin.getLanguageManager().getMessage("player_not_arrested"));
                return true;
            }
            String jailName = plugin.getJailManager().getLeastPopulatedJailName();
            if (jailName == null) {
                sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                    plugin.getLanguageManager().getMessage("jail_no_jails"));
                return true;
            }
            plugin.getJailManager().jailPlayerByWantedLevel(target, jailName);
            plugin.getWantedManager().removeWanted(target);
            plugin.getWantedManager().releaseArrested(target);
            plugin.getCompassManager().stopTracking(police);
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("player_jailed_success"));
            return true;
        }

        // Subcommand: /police arrest <player> — manual arrest (without cuffs)
        if (args.length >= 2 && args[0].equalsIgnoreCase("arrest")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                    plugin.getLanguageManager().getMessage("usage_wanted_player_only"));
                return true;
            }
            Player police = (Player) sender;
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                    plugin.getLanguageManager().getMessage("player_not_found"));
                return true;
            }
            if (!plugin.getWantedManager().isWanted(target)) {
                sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                    plugin.getLanguageManager().getMessage("player_not_wanted"));
                return true;
            }
            if (plugin.getWantedManager().isArrested(target)) {
                sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                    plugin.getLanguageManager().getMessage("player_already_arrested"));
                return true;
            }
            if (!plugin.getJailManager().canArrestPlayer(police, target)) {
                int distance = plugin.getConfigManager().getArrestDistance();
                sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                    plugin.getLanguageManager().getMessage("jail_too_far", "distance", String.valueOf(distance)));
                return true;
            }
            plugin.getWantedManager().arrestPlayer(target, police);
            plugin.getCompassManager().stopTracking(police);
            plugin.getCompassManager().stopTracking(target);
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("player_arrested_success"));
            return true;
        }

        // If no arguments, show help or open GUI
        if (args.length == 0) {
            Player player = (Player) sender;
            PoliceGUI.openPoliceGUI(player, plugin);
            return true;
        }

        // Unknown subcommand, show help
        sender.sendMessage(plugin.getLanguageManager().getPrefix() + "§cUnknown subcommand. Use /wanted help for wanted management or /police for police GUI.");
        return true;
    }
}
