package com.policeplus.commands;

import com.policeplus.PolicePlus;
import com.policeplus.managers.JailManager;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public class JailCommand implements CommandExecutor {
    
    private final PolicePlus plugin;
    
    public JailCommand(PolicePlus plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("policeplus.wanted")) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("no_permission"));
            return true;
        }
        
        // If invoked as /jails (plural), always show the jails list directly
        if (label.equalsIgnoreCase("jails")) {
            showJailList(sender);
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
            case "createmine":
                handleCreateMineJail(sender, args);
                break;
            case "setpos1":
                handleSetPos1(sender, args);
                break;
            case "setpos2":
                handleSetPos2(sender, args);
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
            case "player":
                handleJailPlayerAdmin(sender, args);
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
    
    private void handleCreateMineJail(CommandSender sender, String[] args) {
        if (!sender.hasPermission("policeplus.admin")) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                plugin.getLanguageManager().getMessage("no_permission"));
            return;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                plugin.getLanguageManager().getMessage("usage_wanted_player_only"));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + "§cUsage: /jail createmine <name> <id>");
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
        plugin.getJailManager().createMineJail(jailName, jailId, location);

            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                plugin.getLanguageManager().getMessage("jail_mine_created", "jail_name", jailName));
    }

    private void handleSetPos1(CommandSender sender, String[] args) {
        if (!sender.hasPermission("policeplus.admin")) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                plugin.getLanguageManager().getMessage("no_permission"));
            return;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                plugin.getLanguageManager().getMessage("usage_wanted_player_only"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + "§cUsage: /jail setpos1 <name>");
            return;
        }

        Player player = (Player) sender;
        String jailName = args[1];

        JailManager.JailInfo jail = plugin.getJailManager().getJail(jailName);
        if (jail == null) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                plugin.getLanguageManager().getMessage("jail_not_found", "jail_name", jailName));
            return;
        }
        if (!jail.isMine()) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                plugin.getLanguageManager().getMessage("jail_time_jail_not_allowed"));
            return;
        }

        org.bukkit.block.Block targetBlock = player.getTargetBlockExact(10);
        if (targetBlock == null) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + plugin.getLanguageManager().getMessage("jail_must_look_block"));
            return;
        }

        if (plugin.getJailManager().setJailPos1(jailName, targetBlock.getLocation())) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                plugin.getLanguageManager().getMessage("jail_position_set",
                    "pos", "1", "jail_name", jailName,
                    "coords", targetBlock.getX() + ", " + targetBlock.getY() + ", " + targetBlock.getZ()));
        }
    }

    private void handleSetPos2(CommandSender sender, String[] args) {
        if (!sender.hasPermission("policeplus.admin")) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                plugin.getLanguageManager().getMessage("no_permission"));
            return;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                plugin.getLanguageManager().getMessage("usage_wanted_player_only"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + "§cUsage: /jail setpos2 <name>");
            return;
        }

        Player player = (Player) sender;
        String jailName = args[1];

        JailManager.JailInfo jail = plugin.getJailManager().getJail(jailName);
        if (jail == null) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                plugin.getLanguageManager().getMessage("jail_not_found", "jail_name", jailName));
            return;
        }
        if (!jail.isMine()) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                plugin.getLanguageManager().getMessage("jail_time_jail_not_allowed"));
            return;
        }

        org.bukkit.block.Block targetBlock = player.getTargetBlockExact(10);
        if (targetBlock == null) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + plugin.getLanguageManager().getMessage("jail_must_look_block"));
            return;
        }

        if (plugin.getJailManager().setJailPos2(jailName, targetBlock.getLocation())) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                plugin.getLanguageManager().getMessage("jail_position_set",
                    "pos", "2", "jail_name", jailName,
                    "coords", targetBlock.getX() + ", " + targetBlock.getY() + ", " + targetBlock.getZ()));
            plugin.getJailManager().fillCuboid(jailName);
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                plugin.getLanguageManager().getMessage("jail_mining_filled"));
        }
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
        
        Map<String, JailManager.JailInfo> jails = plugin.getJailManager().getAllJails();
        if (jails.isEmpty()) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("jail_no_jails"));
            return;
        }
        
        // Set as global spawn point for all jails (used as release hub)
        plugin.getJailManager().setGlobalSpawn(spawnLocation);
        
        int setCount = 0;
        for (String jailName : jails.keySet()) {
            plugin.getJailManager().setJailSpawn(jailName, spawnLocation);
            setCount++;
        }
        
        sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
            plugin.getLanguageManager().getMessage("jail_spawn_set_all", "count", String.valueOf(setCount)));
    }
    
    private void handleJailPlayerAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("policeplus.admin")) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("no_permission"));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("usage_jail_player"));
            return;
        }

        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("player_not_found"));
            return;
        }

        String jailName = args[2];
        if (plugin.getJailManager().getJail(jailName) == null) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("jail_not_found", "jail_name", jailName));
            return;
        }

        plugin.getJailManager().jailPlayerByWantedLevel(target, jailName);

        plugin.getWantedManager().removeWanted(target);
        if (plugin.getWantedManager().isArrested(target)) {
            plugin.getWantedManager().releaseArrested(target);
        }

        sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
            plugin.getLanguageManager().getMessage("player_jailed_success"));
    }

    private void showJailList(CommandSender sender) {
        Map<String, JailManager.JailInfo> jails = plugin.getJailManager().getAllJails();

        if (jails == null || jails.isEmpty()) {
            String noJailsMsg = plugin.getLanguageManager().getMessage("jail_list_jails_empty");
            sender.sendMessage(noJailsMsg != null ? noJailsMsg : "§cNo jails defined.");
            return;
        }

        sender.sendMessage("§b§m------§r §3§lRegistered Jails§r §b§m------");
        for (Map.Entry<String, JailManager.JailInfo> entry : jails.entrySet()) {
            JailManager.JailInfo jail = entry.getValue();
            String jailType = jail.getType() != null ? jail.getType() : "TIME";
            String locStr = jail.getLocation() != null && jail.getLocation().getWorld() != null
                ? String.format("%s %d, %d, %d",
                    jail.getLocation().getWorld().getName(),
                    jail.getLocation().getBlockX(),
                    jail.getLocation().getBlockY(),
                    jail.getLocation().getBlockZ())
                : "Not Set";

            sender.sendMessage("§7- §b" + jail.getName() + " §7| Type: §e" + jailType + " §7| Loc: §a" + locStr);
        }
        sender.sendMessage("§b§m-------------------------------------");
    }
    
    private void showJailHelp(CommandSender sender) {
        String prefix = plugin.getLanguageManager().getPrefix();
        
        sender.sendMessage(prefix + plugin.getLanguageManager().getMessage("jail_help_title"));
        sender.sendMessage("");
        
        sender.sendMessage(prefix + plugin.getLanguageManager().getMessage("jail_help_create"));
        sender.sendMessage(prefix + "  " + plugin.getLanguageManager().getMessage("jail_help_create_desc"));
        sender.sendMessage("");
        
        if (sender.hasPermission("policeplus.admin")) {
            sender.sendMessage(prefix + "§c/jail createmine <name> <id> §7- Create a mining labor jail");
            sender.sendMessage(prefix + "§c/jail setpos1 <name> §7- Set cuboid corner 1 for mine-jail");
            sender.sendMessage(prefix + "§c/jail setpos2 <name> §7- Set cuboid corner 2 for mine-jail");
            sender.sendMessage("");
        }
        
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