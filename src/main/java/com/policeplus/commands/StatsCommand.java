package com.policeplus.commands;

import com.policeplus.PolicePlus;
import com.policeplus.managers.StatsManager.PlayerStats;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * StatsCommand - دستورات آمار و رزومه
 * Commands for viewing player statistics and rankings
 * دستورات مشاهده آمار و رتبه‌بندی بازیکنان
 */
public class StatsCommand implements CommandExecutor {
    private final PolicePlus plugin;

    public StatsCommand(PolicePlus plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return handleHelp(sender);
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "me":
                return handleMeStats(sender);
            case "player":
                return handlePlayerStats(sender, args);
            case "top":
                return handleTop(sender, args);
            case "arrests":
                return handleTopArrests(sender);
            case "bounty":
                return handleTopBounty(sender);
            case "efficiency":
                return handleTopEfficiency(sender);
            default:
                return handleHelp(sender);
        }
    }

    /**
     * Handle /stats me - View own stats
     * مشاهده آمار خودم
     */
    private boolean handleMeStats(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("player_only"));
            return true;
        }

        Player player = (Player) sender;
        PlayerStats stats = plugin.getStatsManager().getPlayerStats(player.getUniqueId());

        if (stats == null) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("stats_not_found"));
            return true;
        }

        displayStats(player, stats);
        return true;
    }

    /**
     * Handle /stats player <name> - View specific player stats
     * مشاهده آمار بازیکن خاص
     */
    private boolean handlePlayerStats(CommandSender sender, String[] args) {
        if (!sender.hasPermission("policeplus.stats.view")) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("no_permission"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("cmd_stats_player_usage"));
            return true;
        }

        String playerName = args[1];
        PlayerStats stats = plugin.getStatsManager().getPlayerStatsByName(playerName);

        if (stats == null) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("player_not_found"));
            return true;
        }

        displayStats(sender, stats);
        return true;
    }

    /**
     * Handle /stats top - Show top players
     * نمایش بهترین بازیکنان
     */
    private boolean handleTop(CommandSender sender, String[] args) {
        if (!sender.hasPermission("policeplus.stats.view")) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("no_permission"));
            return true;
        }

        int limit = 10;
        if (args.length > 1) {
            try {
                limit = Integer.parseInt(args[1]);
                limit = Math.min(limit, 50); // Max 50
            } catch (NumberFormatException e) {
                limit = 10;
            }
        }

        sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                plugin.getLanguageManager().getMessage("stats_top_header")
                        .replace("{metric}", "Overall Efficiency"));

        List<PlayerStats> topPlayers = plugin.getStatsManager().getTopByEfficiency(limit);
        int rank = 1;
        for (PlayerStats stats : topPlayers) {
            String line = plugin.getLanguageManager().getMessage("stats_rank_entry")
                    .replace("{rank}", String.valueOf(rank))
                    .replace("{player}", stats.playerName)
                    .replace("{value}", String.format("%.2f", stats.getEfficiencyRating()));
            sender.sendMessage("§7  " + line);
            rank++;
        }

        return true;
    }

    /**
     * Handle /stats arrests - Top arrests
     * بهترین‌های دستگیری
     */
    private boolean handleTopArrests(CommandSender sender) {
        if (!sender.hasPermission("policeplus.stats.view")) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("no_permission"));
            return true;
        }

        sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                plugin.getLanguageManager().getMessage("stats_top_header")
                        .replace("{metric}", "Arrests"));

        List<PlayerStats> topPlayers = plugin.getStatsManager().getTopArresters(10);
        int rank = 1;
        for (PlayerStats stats : topPlayers) {
            String line = plugin.getLanguageManager().getMessage("stats_arrest_entry")
                    .replace("{rank}", String.valueOf(rank))
                    .replace("{player}", stats.playerName)
                    .replace("{arrests}", String.valueOf(stats.arrests));
            sender.sendMessage("§7  " + line);
            rank++;
        }

        return true;
    }

    /**
     * Handle /stats bounty - Top bounty issuers
     * بهترین‌های صادرکننده مامورت
     */
    private boolean handleTopBounty(CommandSender sender) {
        if (!sender.hasPermission("policeplus.stats.view")) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("no_permission"));
            return true;
        }

        sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                plugin.getLanguageManager().getMessage("stats_top_header")
                        .replace("{metric}", "Bounties Issued"));

        List<PlayerStats> topPlayers = plugin.getStatsManager().getTopBountyIssuers(10);
        int rank = 1;
        for (PlayerStats stats : topPlayers) {
            String amount = plugin.getConfig().getString("salary.currency_symbol", "$") +
                    String.format("%.2f", stats.totalBountyIssued);
            String line = plugin.getLanguageManager().getMessage("stats_bounty_entry")
                    .replace("{rank}", String.valueOf(rank))
                    .replace("{player}", stats.playerName)
                    .replace("{amount}", amount);
            sender.sendMessage("§7  " + line);
            rank++;
        }

        return true;
    }

    /**
     * Handle /stats efficiency - Top by efficiency
     * بهترین‌های کارایی
     */
    private boolean handleTopEfficiency(CommandSender sender) {
        return handleTop(sender, new String[] { "top" });
    }

    /**
     * Display stats for a player
     * نمایش آمار برای یک بازیکن
     */
    private void displayStats(CommandSender sender, PlayerStats stats) {
        sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                plugin.getLanguageManager().getMessage("stats_info_header")
                        .replace("{player}", stats.playerName));

        sender.sendMessage("§e┌─────────────────────────────");
        sender.sendMessage("§e│ §6Arrests: §f" + stats.arrests);
        sender.sendMessage("§e│ §6Jails: §f" + stats.jails);
        sender.sendMessage("§e│ §6Handcuffs: §f" + stats.handcuffs);

        String bountyAmount = plugin.getConfig().getString("salary.currency_symbol", "$") +
                String.format("%.2f", stats.totalBountyIssued);
        sender.sendMessage("§e│ §6Bounties Issued: §f" + bountyAmount);

        sender.sendMessage("§e│ §6Efficiency: §f" + String.format("%.2f", stats.getEfficiencyRating()) + "/10");
        sender.sendMessage("§e│ §6Avg Arrests/Day: §f" +
                String.format("%.2f", stats.getAverageArrestsPerDay()));
        sender.sendMessage("§e│ §6Total Salary: §f" +
                plugin.getConfig().getString("salary.currency_symbol", "$") +
                String.format("%.2f", stats.totalSalaryEarned));
        sender.sendMessage("§e│ §6Playtime: §f" + stats.getPlaytimeDays() + "d " +
                stats.getPlaytimeHours() + "h " + stats.getPlaytimeMinutes() + "m");
        sender.sendMessage("§e└─────────────────────────────");
    }

    /**
     * Handle /stats help
     * نمایش کمک
     */
    private boolean handleHelp(CommandSender sender) {
        sender.sendMessage(plugin.getLanguageManager().getPrefix() + "§6=== Stats Commands ===");
        sender.sendMessage("§e/stats me §7- View your own statistics");

        if (sender.hasPermission("policeplus.stats.view")) {
            sender.sendMessage("§e/stats player <name> §7- View a player's statistics");
            sender.sendMessage("§e/stats top [limit] §7- Show top players by efficiency");
            sender.sendMessage("§e/stats arrests §7- Show top players by arrests");
            sender.sendMessage("§e/stats bounty §7- Show top bounty issuers");
            sender.sendMessage("§e/stats efficiency §7- Show top by efficiency");
        }

        return true;
    }
}
