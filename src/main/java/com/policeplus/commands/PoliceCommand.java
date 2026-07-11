package com.policeplus.commands;

import com.policeplus.PolicePlus;
import com.policeplus.gui.PoliceGUI;
import com.policeplus.managers.JailManager;
import com.policeplus.utils.PermissionUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

public class PoliceCommand implements CommandExecutor {
    
    private final PolicePlus plugin;
    
    public PoliceCommand(PolicePlus plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // No arguments — open GUI for players, show usage for console
        if (args.length == 0) {
            if (sender instanceof Player) {
                PoliceGUI.openPoliceGUI((Player) sender, plugin);
            } else {
                sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("usage_wanted_player_only"));
            }
            return true;
        }

        // Subcommand: /police help
        if (args[0].equalsIgnoreCase("help")) {
            showPoliceHelp(sender);
            return true;
        }

        // Subcommand: /police reload
        if (args[0].equalsIgnoreCase("reload")) {
            handleReload(sender);
            return true;
        }

        // Subcommand: /police debugjails
        if (args[0].equalsIgnoreCase("debugjails")) {
            handleDebugJails(sender);
            return true;
        }

        // Subcommand: /police wanted <args>
        if (args[0].equalsIgnoreCase("wanted")) {
            String[] wantedArgs = new String[args.length - 1];
            System.arraycopy(args, 1, wantedArgs, 0, args.length - 1);
            handleWantedSubcommand(sender, wantedArgs);
            return true;
        }

        // Subcommand: /police jail
        if (args[0].equalsIgnoreCase("jail")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                    plugin.getLanguageManager().getMessage("usage_wanted_player_only"));
                return true;
            }
            Player police = (Player) sender;

            // Check if global jail spawn is set (prerequisite)
            if (!plugin.getJailManager().isGlobalSpawnSet()) {
                sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                    plugin.getLanguageManager().getMessage("jail_spawn_not_set"));
                return true;
            }

            // Find the arrested player for this police
            Map<UUID, UUID> arrestedMap = plugin.getWantedManager().getArrestedPlayers();
            Player target = null;
            for (Map.Entry<UUID, UUID> entry : arrestedMap.entrySet()) {
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
            // Check proximity to the selected jail cell
            if (!plugin.getJailManager().isNearJailLocation(police, jailName)) {
                sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                    plugin.getLanguageManager().getMessage("jail_not_near_jail_cell", "jail", jailName));
                return true;
            }
            // Get wanted level BEFORE clearing it (for economy reward calculation)
            int wantedLevel = plugin.getWantedManager().getWantedLevel(target);

            plugin.getJailManager().jailPlayerByWantedLevel(target, jailName);
            plugin.getWantedManager().removeWanted(target);
            plugin.getWantedManager().releaseArrested(target);
            plugin.getCompassManager().stopTracking(police);

            // Economy reward
            try {
                if (plugin.getConfigManager().isEconomyEnabled() && PolicePlus.getEconomy() != null) {
                    if (wantedLevel <= 0) wantedLevel = 1;
                    double reward = wantedLevel * plugin.getConfigManager().getEconomyRewardPerWantedLevel();
                    PolicePlus.getEconomy().depositPlayer(police, reward);
                    police.sendMessage(plugin.getLanguageManager().getPrefix() +
                        plugin.getLanguageManager().getMessage("economy_reward",
                            "amount", String.format("%.0f", reward),
                            "player", target.getName(),
                            "level", String.valueOf(wantedLevel)));
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("Could not pay economy reward: " + t.getMessage());
            }

            // Bounty payout: if jailed player has an active bounty and remove_on_jail is true
            try {
                if (plugin.getConfigManager().isBountyRemoveOnJail() && plugin.getBountyManager().hasBounty(target)) {
                    double bountyAmount = plugin.getBountyManager().getBounty(target);
                    if (bountyAmount > 0 && PolicePlus.getEconomy() != null) {
                        PolicePlus.getEconomy().depositPlayer(police, bountyAmount);
                        police.sendMessage(plugin.getLanguageManager().getPrefix() +
                                plugin.getLanguageManager().getMessage("bounty_claimed",
                                        "amount", plugin.getBountyManager().formatCurrency(bountyAmount),
                                        "player", target.getName()));
                    }
                    plugin.getBountyManager().removeBounty(target);
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("Could not process bounty payout: " + t.getMessage());
            }

            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("player_jailed_success"));
            return true;
        }

        // Unknown subcommand
        sender.sendMessage(plugin.getLanguageManager().getPrefix() +
            "§cUnknown subcommand. Use §e/police help §cfor available commands.");
        return true;
    }

    // ========================= /police wanted <args> =========================

    /**
     * Handles all wanted subcommands under /police wanted.
     * This is the consolidated logic from the old /wanted command.
     */
    private void handleWantedSubcommand(CommandSender sender, String[] args) {
        // /police wanted (no further args) — show help menu for players, usage for console
        if (args.length == 0) {
            if (sender instanceof Player) {
                showWantedHelp(sender);
            } else {
                sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                    plugin.getLanguageManager().getMessage("usage_wanted"));
            }
            return;
        }
        
        // Help is available to everyone
        if (args[0].equalsIgnoreCase("help")) {
            showWantedHelp(sender);
            return;
        }

        // Other subcommands require permission
        if (!sender.hasPermission("policeplus.wanted")) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("no_permission"));
            return;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "set":
                handleWantedSet(sender, args);
                break;

            case "add":
                handleWantedAdd(sender, args);
                break;
                
            case "list":
                showWantedList(sender);
                break;
                
            case "jail":
                handleWantedJailPlayer(sender, args);
                break;

            case "unjail":
                handleWantedUnjail(sender, args);
                break;
                
            case "reload":
                handleWantedReload(sender);
                break;
                
            case "gui":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                        plugin.getLanguageManager().getMessage("usage_wanted_player_only"));
                    return;
                }
                Player player = (Player) sender;
                PoliceGUI.openPoliceGUI(player, plugin);
                break;
                
            default:
                sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                    plugin.getLanguageManager().getMessage("usage_wanted_unknown"));
                break;
        }
    }

    private void handleWantedSet(CommandSender sender, String[] args) {
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
            plugin.getWantedManager().removeWanted(target);
        } else {
            plugin.getWantedManager().setWantedLevel(target, level, null);
        }
    }

    private void handleWantedAdd(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("usage_wanted_add"));
            return;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("player_not_found"));
            return;
        }
        
        int level;
        try {
            level = Integer.parseInt(args[2]);
            if (level <= 0) {
                sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                    plugin.getLanguageManager().getMessage("usage_wanted_invalid_level"));
                return;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("usage_wanted_invalid_level"));
            return;
        }
        
        String reason = null;
        if (args.length >= 4) {
            StringBuilder reasonBuilder = new StringBuilder();
            for (int i = 3; i < args.length; i++) {
                if (reasonBuilder.length() > 0) reasonBuilder.append(" ");
                reasonBuilder.append(args[i]);
            }
            reason = reasonBuilder.toString();
        }
        
        plugin.getWantedManager().addWanted(target, level, reason);
        
        sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
            plugin.getLanguageManager().getMessage("wanted_add_success",
                "player", target.getName(), "level", String.valueOf(level)));
    }

    private void handleWantedJailPlayer(CommandSender sender, String[] args) {
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
        
        if (!plugin.getWantedManager().isArrested(target)) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("player_not_arrested"));
            return;
        }

        // Check if global jail spawn is set (prerequisite)
        if (!plugin.getJailManager().isGlobalSpawnSet()) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("jail_spawn_not_set"));
            return;
        }
        
        Player arrestingPolice = plugin.getWantedManager().getArrestingPolice(target);
        if (arrestingPolice == null || !arrestingPolice.equals(police)) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("not_arresting_police"));
            return;
        }
        
        if (!plugin.getJailManager().canArrestPlayer(police, target)) {
            int distance = plugin.getConfigManager().getArrestDistance();
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("jail_too_far", "distance", String.valueOf(distance)));
            return;
        }

        Map<String, JailManager.JailInfo> jails = plugin.getJailManager().getAllJails();
        if (jails.isEmpty()) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("jail_no_jails"));
            return;
        }
        
        String jailName = jails.keySet().iterator().next();
        // Check proximity to the selected jail cell
        if (!plugin.getJailManager().isNearJailLocation(police, jailName)) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("jail_not_near_jail_cell", "jail", jailName));
            return;
        }
        // Get wanted level BEFORE clearing it (for economy reward calculation)
        int wantedLevel = plugin.getWantedManager().getWantedLevel(target);

        plugin.getJailManager().jailPlayerByWantedLevel(target, jailName);
        
        plugin.getWantedManager().removeWanted(target);
        plugin.getWantedManager().releaseArrested(target);
        
        plugin.getCompassManager().stopTracking(police);

        // Economy reward
        try {
            if (plugin.getConfigManager().isEconomyEnabled() && PolicePlus.getEconomy() != null) {
                if (wantedLevel <= 0) wantedLevel = 1;
                double reward = wantedLevel * plugin.getConfigManager().getEconomyRewardPerWantedLevel();
                PolicePlus.getEconomy().depositPlayer(police, reward);
                police.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("economy_reward",
                        "amount", String.format("%.0f", reward),
                        "player", target.getName(),
                        "level", String.valueOf(wantedLevel)));
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not pay economy reward: " + t.getMessage());
        }

        // Bounty payout: if jailed player has an active bounty and remove_on_jail is true
        try {
            if (plugin.getConfigManager().isBountyRemoveOnJail() && plugin.getBountyManager().hasBounty(target)) {
                double bountyAmount = plugin.getBountyManager().getBounty(target);
                if (bountyAmount > 0 && PolicePlus.getEconomy() != null) {
                    PolicePlus.getEconomy().depositPlayer(police, bountyAmount);
                    police.sendMessage(plugin.getLanguageManager().getPrefix() +
                            plugin.getLanguageManager().getMessage("bounty_claimed",
                                    "amount", plugin.getBountyManager().formatCurrency(bountyAmount),
                                    "player", target.getName()));
                }
                plugin.getBountyManager().removeBounty(target);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not process bounty payout: " + t.getMessage());
        }
        
        sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
            plugin.getLanguageManager().getMessage("player_jailed"));
    }

    private void handleWantedUnjail(CommandSender sender, String[] args) {
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

    private void handleWantedReload(CommandSender sender) {
        // Delegate to the comprehensive reload handler
        handleReload(sender);
    }

    /**
     * Comprehensive reload handler for /police reload and /police wanted reload.
     * Reloads all configs, language, and refreshes all manager caches.
     */
    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("policeplus.admin")) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("no_permission"));
            return;
        }
        try {
            // 0. Cancel all running tasks BEFORE reloading to prevent duplicates
            try {
                plugin.getCompassManager().stopAll();
            } catch (Throwable t) {
                plugin.getLogger().warning("Could not stop compass tasks: " + t.getMessage());
            }

            // 1. Reload config.yml and re-cache all settings
            plugin.getConfigManager().reloadConfig();

            // 2. Reload language file (re-reads en.yml/fa.yml based on config)
            plugin.getLanguageManager().reloadLanguage();

            // 3. Reload handcuff settings (name, lore, material, blindness, etc.)
            try {
                plugin.getHandcuffManager().reloadConfig();
            } catch (Throwable t) {
                plugin.getLogger().warning("Could not reload handcuff settings: " + t.getMessage());
            }

            // 4. Restart wanted auto-decay scheduler (cancels old task first)
            try {
                plugin.getWantedManager().restartAutoDecay();
            } catch (Throwable t) {
                plugin.getLogger().warning("Could not restart auto-decay scheduler: " + t.getMessage());
            }

            // 5. Refresh salary manager cached settings
            try {
                plugin.getSalaryManager().reload();
            } catch (Throwable t) {
                plugin.getLogger().warning("Could not reload salary settings: " + t.getMessage());
            }

            // 6. Reload jail manager data from disk (cancels old tasks first)
            try {
                plugin.getJailManager().reloadJails();
            } catch (Throwable t) {
                plugin.getLogger().warning("Could not reload jail data: " + t.getMessage());
            }

            // 7. Refresh display system (tablist, below-name, star cache)
            plugin.getDisplayManager().forceRefreshAllDisplays();

            // 8. Restart BossBar updater task (cancels old task first)
            try {
                plugin.getDisplayManager().restartBossBarUpdater();
            } catch (Throwable t) {
                plugin.getLogger().warning("Could not restart boss bar updater: " + t.getMessage());
            }

            // 9. Restart handcuff timeout checker (cancels old task first)
            try {
                plugin.startHandcuffTimeoutChecker();
            } catch (Throwable t) {
                plugin.getLogger().warning("Could not restart handcuff timeout checker: " + t.getMessage());
            }

            // 9. Save message with freshly loaded language
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("wanted_reload_success"));
            plugin.getLogger().info("Plugin reloaded successfully by " + sender.getName());
        } catch (Exception e) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("wanted_reload_error"));
            plugin.getLogger().severe("Error during reload: " + e.getMessage());
        }
    }

    private void showWantedList(CommandSender sender) {
        Map<UUID, Integer> wantedPlayers = plugin.getWantedManager().getWantedPlayers();
        
        sender.sendMessage(plugin.getLanguageManager().getPrefix() + 
            plugin.getLanguageManager().getMessage("wanted_list_header"));
        
        if (wantedPlayers.isEmpty()) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("wanted_list_empty"));
        } else {
            for (Map.Entry<UUID, Integer> entry : wantedPlayers.entrySet()) {
                String playerName = Bukkit.getOfflinePlayer(entry.getKey()).getName();
                if (playerName != null) {
                    String reason = plugin.getWantedManager().getWantedReason(entry.getKey());
                    String message = plugin.getLanguageManager().getMessage("wanted_list_entry", 
                        "player", playerName, "level", String.valueOf(entry.getValue()));
                    if (reason != null && !reason.isEmpty()) {
                        message += " §7- " + reason;
                    }
                    sender.sendMessage(message);
                }
            }
        }
    }

    private void showWantedHelp(CommandSender sender) {
        sendClickable(sender, "§b§m----------§r §3§lPolice-Plus Help Menu§r §b§m----------", null, null);
        sender.sendMessage("");

        sendClickable(sender,
                "§b/police wanted §7- " + plugin.getLanguageManager().getMessage("help_desc_gui"),
                "/police wanted", "§eClick for wanted commands help");

        if (sender.hasPermission("policeplus.wanted")) {
            sendClickable(sender,
                    "§b/police wanted set <player> [level] §7- " + plugin.getLanguageManager().getMessage("help_desc_set"),
                    "/police wanted set", "§eClick to set wanted level");
            sendClickable(sender,
                    "§b/police wanted add <player> <level> [reason] §7- " + plugin.getLanguageManager().getMessage("help_desc_add"),
                    "/police wanted add", "§eClick to add wanted level");
            sendClickable(sender,
                    "§b/police wanted list §7- " + plugin.getLanguageManager().getMessage("help_desc_list"),
                    "/police wanted list", "§eClick to list wanted players");
            sendClickable(sender,
                    "§b/police wanted jail <player> §7- " + plugin.getLanguageManager().getMessage("help_desc_jail"),
                    "/police wanted jail", "§eClick to jail a suspect");
            sendClickable(sender,
                    "§b/police wanted unjail <player> §7- " + plugin.getLanguageManager().getMessage("help_desc_unjail"),
                    "/police wanted unjail", "§eClick to unjail a player");
            sendClickable(sender,
                    "§b/police wanted gui §7- " + plugin.getLanguageManager().getMessage("help_desc_gui"),
                    "/police wanted gui", "§eClick to open police GUI");
            sendClickable(sender,
                    "§b/cuffe <player> §7- " + plugin.getLanguageManager().getMessage("help_desc_cuff"),
                    "/cuffe", "§eClick to cuff a player");
            sendClickable(sender,
                    "§b/uncuffe <player> §7- " + plugin.getLanguageManager().getMessage("help_desc_uncuff"),
                    "/uncuffe", "§eClick to uncuff a player");
            sendClickable(sender,
                    "§b/jail create <name> <id> §7- " + plugin.getLanguageManager().getMessage("help_desc_jail_create"),
                    "/jail create", "§eClick to create a jail");
            sendClickable(sender,
                    "§b/compass <player> §7- " + plugin.getLanguageManager().getMessage("help_desc_compass"),
                    "/compass", "§eClick to track a wanted player");
            sendClickable(sender,
                    "§b/rank set <player> <rank> §7- " + plugin.getLanguageManager().getMessage("help_desc_rank"),
                    "/rank", "§eClick to manage ranks");
            sendClickable(sender,
                    "§b/bounty set <player> <amount> [reason] §7- " + plugin.getLanguageManager().getMessage("help_desc_bounty"),
                    "/bounty", "§eClick to manage bounties");
        }

        if (sender.hasPermission("policeplus.admin")) {
            sender.sendMessage("");
            sendClickable(sender,
                    "§c/police wanted reload §7- " + plugin.getLanguageManager().getMessage("help_desc_reload"),
                    "/police wanted reload", "§eClick to reload plugin");
        }

        sender.sendMessage("");
        sendClickable(sender, "§b§m------------------------------------------", null, null);
    }

    // ========================= /police help =========================

    private void showPoliceHelp(CommandSender sender) {
        sender.spigot().sendMessage(
                net.md_5.bungee.api.chat.TextComponent.fromLegacyText(
                        "§b§m----------§r §3§lPolice-Plus Commands§r §b§m----------")[0]);
        sender.sendMessage("");

        // Police GUI
        if (sender instanceof Player && PermissionUtils.hasPolicePermission((Player) sender, "policeplus.gui")) {
            sendClickable(sender,
                    "§b/police §7- " + plugin.getLanguageManager().getMessage("help_desc_gui"),
                    "/police", "§eClick to open Police GUI");
            sendClickable(sender,
                    "§b/police jail §7- " + plugin.getLanguageManager().getMessage("help_desc_jail"),
                    "/police jail", "§eClick to jail arrested suspect");
        }

        // Wanted commands (now under /police wanted)
        sendClickable(sender,
                "§b/police wanted §7- " + plugin.getLanguageManager().getMessage("help_desc_gui"),
                "/police wanted", "§eClick for wanted commands help");
        if (sender.hasPermission("policeplus.wanted")) {
            sendClickable(sender,
                    "§b/police wanted help §7- Full wanted command help",
                    "/police wanted help", "§eClick for full help menu");
        }

        if (sender.hasPermission("policeplus.admin")) {
            sendClickable(sender,
                    "§c/police reload §7- " + plugin.getLanguageManager().getMessage("help_desc_reload"),
                    "/police reload", "§eClick to reload all plugin configs");
        }

        sender.sendMessage("");
        sendClickable(sender, "§b§m------------------------------------------", null, null);
    }

    // ========================= /police debugjails =========================

    /**
     * Debug command that prints the size and contents of JailManager's in-memory jails map.
     */
    private void handleDebugJails(CommandSender sender) {
        if (!sender.hasPermission("policeplus.admin")) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                plugin.getLanguageManager().getMessage("no_permission"));
            return;
        }

        Map<String, JailManager.JailInfo> allJails = plugin.getJailManager().getAllJails();
        sender.sendMessage("§b§m----------§r §3§lDebug Jails§r §b§m----------");
        sender.sendMessage("§7In-memory jail count: §e" + allJails.size());

        if (allJails.isEmpty()) {
            sender.sendMessage("§cNo jails found in memory! The jail map is empty.");
            sender.sendMessage("§7If you just created a jail, check the console for save/load debug logs.");
        } else {
            for (Map.Entry<String, JailManager.JailInfo> entry : allJails.entrySet()) {
                String name = entry.getKey();
                JailManager.JailInfo jail = entry.getValue();
                String loc = jail.getLocation() != null
                    ? jail.getLocation().getWorld().getName() + " " +
                      jail.getLocation().getBlockX() + ", " +
                      jail.getLocation().getBlockY() + ", " +
                      jail.getLocation().getBlockZ()
                    : "null";
                sender.sendMessage("§a" + name + " §7| ID: §f" + jail.getId() +
                    " §7| Type: §e" + jail.getType() +
                    " §7| Loc: §f" + loc);
            }
        }

        // Also show jailed players
        Map<UUID, JailManager.JailData> jailedPlayers = plugin.getJailManager().getJailedPlayers();
        sender.sendMessage("§7Jailed players: §e" + jailedPlayers.size());
        for (Map.Entry<UUID, JailManager.JailData> entry : jailedPlayers.entrySet()) {
            String uuid = entry.getKey().toString();
            JailManager.JailData data = entry.getValue();
            sender.sendMessage("  §c" + uuid + " §7-> jail: §e" + data.getJailName() +
                " §7| type: " + (data.isBlocksType() ? "BLOCKS (" + data.getRequiredBlocks() + " blocks)" : "TIME"));
        }

        sender.sendMessage("§7Global spawn set: " + (plugin.getJailManager().isGlobalSpawnSet() ? "§aYes" : "§cNo"));
        sender.sendMessage("§b§m------------------------------------------");
    }

    private void sendClickable(CommandSender sender, String message, String clickCommand, String hoverText) {
        net.md_5.bungee.api.chat.TextComponent component =
                new net.md_5.bungee.api.chat.TextComponent(
                        net.md_5.bungee.api.chat.TextComponent.fromLegacyText(message));

        if (clickCommand != null) {
            component.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                    net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, clickCommand));
        }
        if (hoverText != null) {
            component.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                    net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                    new net.md_5.bungee.api.chat.BaseComponent[]{
                            new net.md_5.bungee.api.chat.TextComponent(hoverText)}));
        }

        sender.spigot().sendMessage(component);
    }
}