package com.policeplus.commands;

import com.policeplus.PolicePlus;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RankCommand implements CommandExecutor {
    private final PolicePlus plugin;

    public RankCommand(PolicePlus plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "set":
                return handleSet(sender, args);
            case "remove":
                return handleRemove(sender, args);
            case "list":
                return handleList(sender);
            case "info":
                return handleInfo(sender, args);
            default:
                sendUsage(sender);
                return true;
        }
    }

    private boolean handleSet(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    "§cUsage: /rank set <player> <rank>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("player_not_found",
                            "player", args[1]));
            return true;
        }

        String rankInput = args[2];
        String rankKey = plugin.getRankManager().resolveRankKey(rankInput);

        if (rankKey == null) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    "§cRank not found: " + rankInput);
            return true;
        }

        String oldRank = plugin.getRankManager().getPlayerRank(target);
        plugin.getRankManager().setPlayerRank(target, rankKey);

        // Log the change
        plugin.getLogManager().logRankChange(sender.getName(), target, rankKey);

        // Record promotion/demotion in stats
        if (plugin.getStatsManager() != null) {
            if (oldRank != null && !oldRank.equals(rankKey)) {
                plugin.getStatsManager().recordPromotion(target);
            }
        }

        String message = plugin.getLanguageManager().getMessage("rank_set",
                "player", target.getName(),
                "rank", plugin.getRankManager().getDisplayNameForRank(rankKey));
        sender.sendMessage(plugin.getLanguageManager().getPrefix() + message);
        target.sendMessage(plugin.getLanguageManager().getPrefix() +
                plugin.getLanguageManager().getMessage("you_promoted",
                        "rank", plugin.getRankManager().getDisplayNameForRank(rankKey)));

        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    "§cUsage: /rank remove <player>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("player_not_found",
                            "player", args[1]));
            return true;
        }

        String oldRank = plugin.getRankManager().getPlayerRank(target);
        plugin.getRankManager().clearPlayerRank(target);

        String message = plugin.getLanguageManager().getMessage("rank_removed",
                "player", target.getName());
        sender.sendMessage(plugin.getLanguageManager().getPrefix() + message);

        return true;
    }

    private boolean handleList(CommandSender sender) {
        sender.sendMessage(plugin.getLanguageManager().getPrefix() + "§6=== Available Ranks ===");
        for (String rankKey : plugin.getRankManager().getDefinedRanks()) {
            String displayName = plugin.getRankManager().getDisplayNameForRank(rankKey);
            sender.sendMessage("§a" + rankKey + " §7→ " + displayName);
        }
        return true;
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                        "§cUsage: /rank info <player>");
                return true;
            }
            Player player = (Player) sender;
            String rank = plugin.getRankManager().getPlayerRank(player);
            if (rank == null) {
                sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                        "§cYou don't have a rank.");
            } else {
                sender.sendMessage(plugin.getLanguageManager().getPrefix() + "§6Your Rank: " +
                        plugin.getRankManager().getDisplayNameForRank(rank));
            }
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("player_not_found",
                            "player", args[1]));
            return true;
        }

        String rank = plugin.getRankManager().getPlayerRank(target);
        if (rank == null) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + "§c" +
                    target.getName() + " doesn't have a rank.");
        } else {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + "§6" +
                    target.getName() + "'s Rank: " +
                    plugin.getRankManager().getDisplayNameForRank(rank));
        }

        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(plugin.getLanguageManager().getPrefix() + "§6=== Rank Command Usage ===");
        sender.sendMessage("§a/rank set <player> <rank> §7- Set player rank");
        sender.sendMessage("§a/rank remove <player> §7- Remove player rank");
        sender.sendMessage("§a/rank list §7- List all ranks");
        sender.sendMessage("§a/rank info [player] §7- Check rank info");
    }
}
