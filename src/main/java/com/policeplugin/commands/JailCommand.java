package com.policeplugin.commands;

import com.policeplugin.PolicePlugin;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class JailCommand implements CommandExecutor {
    
    private final PolicePlugin plugin;
    
    public JailCommand(PolicePlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("policeplugin.wanted")) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("no_permission"));
            return true;
        }
        
        if (args.length == 0) {
            showJailHelp(sender);
            return true;
        }
        
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "create":
                handleCreateJail(sender, args);
                break;
            case "delete":
                handleDeleteJail(sender, args);
                break;
            case "spawn":
                handleSetSpawn(sender);
                break;
            case "list":
                showJailList(sender);
                break;
            case "help":
                showJailHelp(sender);
                break;
            default:
                sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                    plugin.getLanguageManager().getMessage("jail_unknown_subcommand"));
                break;
        }
        
        return true;
    }
    
    private void handleCreateJail(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("usage_wanted_player_only"));
            return;
        }
        
        if (args.length < 3) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("usage_jail_create"));
            return;
        }
        
        Player player = (Player) sender;
        String jailName = args[1];
        String jailId = args[2];
        
        if (plugin.getJailManager().getJail(jailName) != null) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("jail_already_exists", "jail_name", jailName));
            return;
        }
        
        Location location = player.getLocation();
        plugin.getJailManager().createJail(jailName, jailId, location);
        
        sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
            plugin.getLanguageManager().getMessage("jail_created", "jail_name", jailName));
    }
    
    private void handleDeleteJail(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("usage_jail_delete"));
            return;
        }
        
        String jailName = args[1];
        
        if (plugin.getJailManager().getJail(jailName) == null) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("jail_not_found", "jail_name", jailName));
            return;
        }
        
        plugin.getJailManager().deleteJail(jailName);
        sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
            plugin.getLanguageManager().getMessage("jail_deleted", "jail_name", jailName));
    }
    
    private void handleSetSpawn(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("usage_wanted_player_only"));
            return;
        }
        
        Player player = (Player) sender;
        Location spawnLocation = player.getLocation();
        
        var jails = plugin.getJailManager().getAllJails();
        if (jails.isEmpty()) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("jail_no_jails"));
            return;
        }
        
        int setCount = 0;
        for (String jailName : jails.keySet()) {
            plugin.getJailManager().setJailSpawn(jailName, spawnLocation);
            setCount++;
        }
        
        sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
            plugin.getLanguageManager().getMessage("jail_spawn_set_all", "count", String.valueOf(setCount)));
    }
    
    private void showJailList(CommandSender sender) {
        var jails = plugin.getJailManager().getAllJails();
        
        sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
            plugin.getLanguageManager().getMessage("jail_list_jails_header"));
        
        if (jails.isEmpty()) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("jail_list_jails_empty"));
        } else {
            for (var entry : jails.entrySet()) {
                String jailName = entry.getKey();
                String jailId = entry.getValue().getId();
                String message = plugin.getLanguageManager().getMessage("jail_list_jails_entry", 
                    "jail_name", jailName, "jail_id", jailId);
                sender.sendMessage(message);
            }
        }
    }
    
    private void showJailHelp(CommandSender sender) {
        String prefix = plugin.getLanguageManager().getPrefix();
        
        sender.sendMessage(prefix + plugin.getLanguageManager().getMessage("jail_help_title"));
        sender.sendMessage("");
        
        sender.sendMessage(prefix + plugin.getLanguageManager().getMessage("jail_help_create"));
        sender.sendMessage(prefix + "  " + plugin.getLanguageManager().getMessage("jail_help_create_desc"));
        sender.sendMessage("");
        
        sender.sendMessage(prefix + plugin.getLanguageManager().getMessage("jail_help_delete"));
        sender.sendMessage(prefix + "  " + plugin.getLanguageManager().getMessage("jail_help_delete_desc"));
        sender.sendMessage("");
        
        sender.sendMessage(prefix + plugin.getLanguageManager().getMessage("jail_help_spawn"));
        sender.sendMessage(prefix + "  " + plugin.getLanguageManager().getMessage("jail_help_spawn_desc"));
        sender.sendMessage("");
        
        sender.sendMessage(prefix + plugin.getLanguageManager().getMessage("jail_help_list"));
        sender.sendMessage(prefix + "  " + plugin.getLanguageManager().getMessage("jail_help_list_desc"));
        sender.sendMessage("");
        
        sender.sendMessage(prefix + plugin.getLanguageManager().getMessage("jail_help_help"));
        sender.sendMessage(prefix + "  " + plugin.getLanguageManager().getMessage("jail_help_help_desc"));
    }
}


