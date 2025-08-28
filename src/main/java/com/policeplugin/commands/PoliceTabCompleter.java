package com.policeplugin.commands;

import com.policeplugin.PolicePlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class PoliceTabCompleter implements TabCompleter {
    
    private final PolicePlugin plugin;
    
    public PoliceTabCompleter(PolicePlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String commandName = command.getName().toLowerCase();
        
        switch (commandName) {
            case "wanted":
                return completeWantedCommand(sender, args);
            case "compass":
                return completeCompassCommand(sender, args);
            case "jail":
                return completeJailCommand(sender, args);
            case "cuffe":
                return completeCuffCommand(sender, args);
            case "help":
                return new ArrayList<>();
            case "police":
                return completePoliceCommand(sender, args);
            default:
                return new ArrayList<>();
        }
    }
    
    private List<String> completeWantedCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("policeplugin.wanted")) {
            return new ArrayList<>();
        }
        
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            completions.add("set");
            completions.add("list");
            completions.add("jail");
            completions.add("unjail");
            completions.add("help");
            completions.add("gui");
            
            if (sender.hasPermission("policeplugin.admin")) {
                completions.add("reload");
                completions.add("debug");
            }
            
            return completions;
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("unjail")) {
                return getOnlinePlayerNames();
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("set")) {
                // Generate numbers from 0 to max_wanted_level dynamically
                return getWantedLevelOptions();
            }
        }
        
        return new ArrayList<>();
    }
    
    private List<String> completeJailCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("policeplugin.wanted")) {
            return new ArrayList<>();
        }
        
        if (args.length == 1) {
            return Arrays.asList("create", "delete", "spawn", "list", "help");
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("create")) {
                return Arrays.asList("jail_name");
            } else if (args[0].equalsIgnoreCase("delete")) {
                return getJailNames();
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("create")) {
                return Arrays.asList("jail_id");
            }
        }
        
        return new ArrayList<>();
    }

    
    private List<String> completeCompassCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("policeplugin.compass")) {
            return new ArrayList<>();
        }
        
        if (args.length == 1) {
            // Return only wanted players for compass
            return getWantedPlayerNames();
        }
        
        return new ArrayList<>();
    }
    
    private List<String> getOnlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .collect(Collectors.toList());
    }
    
    private List<String> getWantedLevelOptions() {
        int maxLevel = plugin.getConfigManager().getMaxWantedLevel();
        List<String> levelOptions = new ArrayList<>();
        // Add 0 for removing wanted status
        levelOptions.add("0");
        // Add numbers from 1 to max_wanted_level
        for (int i = 1; i <= maxLevel; i++) {
            levelOptions.add(String.valueOf(i));
        }
        return levelOptions;
    }

    private List<String> getWantedPlayerNames() {
        List<String> wantedPlayers = new ArrayList<>();
        var wantedMap = plugin.getWantedManager().getWantedPlayers();
        
        for (var entry : wantedMap.entrySet()) {
            String playerName = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            if (playerName != null) {
                wantedPlayers.add(playerName);
            }
        }
        
        return wantedPlayers;
    }
    
    private List<String> getJailNames() {
        List<String> jailNames = new ArrayList<>();
        var jails = plugin.getJailManager().getAllJails();
        
        for (String jailName : jails.keySet()) {
            jailNames.add(jailName);
        }
        
        return jailNames;
    }
    
    // removed legacy policejob completer

    private List<String> completePoliceCommand(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            
            if (sender.hasPermission("policeplugin.police")) {
                completions.add("jail");
                completions.add("arrest");
            }
            
            return completions;
        }
        
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("arrest")) {
                return getOnlinePlayerNames();
            }
        }
        
        // Rank autocompletion removed
        
        return new ArrayList<>();
    }

    private List<String> completeCuffCommand(CommandSender sender, String[] args) {
        boolean canUse = sender.hasPermission("policeplugin.cuffe.use") || sender.hasPermission("policeplugin.police");
        boolean canGive = sender.hasPermission("policeplugin.cuffe.give");

        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            if (canGive) list.add("give");
            if (canUse) list.add("uncuffe");
            if (canUse) {
                // also allow typing a player name directly (no subcmd)
                list.addAll(getOnlinePlayerNames());
            }
            return list;
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("give") && canGive) {
                return getOnlinePlayerNames();
            }
            if (args[0].equalsIgnoreCase("uncuffe") && canUse) {
                return getOnlinePlayerNames();
            }
        }

        return new ArrayList<>();
    }
}
