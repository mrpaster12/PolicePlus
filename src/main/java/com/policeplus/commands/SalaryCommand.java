package com.policeplus.commands;

import com.policeplus.PolicePlus;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;

/**
 * SalaryCommand - دستورات مدیریت حقوق
 * Manages salary system commands and information
 * مدیریت دستورات سیستم حقوق
 */
public class SalaryCommand implements CommandExecutor {
    private final PolicePlus plugin;

    public SalaryCommand(PolicePlus plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Require player context
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("player_only"));
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            return handleInfo(player);
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "info":
                return handleInfo(player);
            case "list":
                return handleListRanks(sender);
            case "pay":
                return handlePay(sender, args);
            case "enable":
                return handleEnable(sender);
            case "disable":
                return handleDisable(sender);
            case "status":
                return handleStatus(sender);
            default:
                return handleHelp(sender);
        }
    }

    /**
     * Handle /salary info - Check own salary info
     * بررسی اطلاعات حقوق خود
     */
    private boolean handleInfo(Player player) {
        String rank = plugin.getRankManager().getPlayerRank(player);

        if (rank == null || rank.isEmpty()) {
            player.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("salary_no_rank"));
            return true;
        }

        // Get salary for player's rank
        double rankSalary = plugin.getSalaryManager().getRankSalary(rank);
        String displayRank = plugin.getRankManager().getAllRankDisplayNames()
                .getOrDefault(rank, rank);

        player.sendMessage(plugin.getLanguageManager().getPrefix() +
                plugin.getLanguageManager().getMessage("salary_info_header"));

        player.sendMessage("§e┌─────────────────────────────");
        player.sendMessage("§e│ §6" + plugin.getLanguageManager().getMessage("rank") +
                ": §f" + displayRank);
        player.sendMessage("§e│ §6" + plugin.getLanguageManager().getMessage("salary_amount") +
                ": §f" + plugin.getSalaryManager().formatCurrency(rankSalary));

        String nextPayment = plugin.getSalaryManager().getFormattedTimeUntilPayment(player);
        player.sendMessage("§e│ §6" + plugin.getLanguageManager().getMessage("salary_next_payment") +
                ": §f" + nextPayment);

        player.sendMessage("§e└─────────────────────────────");

        return true;
    }

    /**
     * Handle /salary list - Show all salary tiers
     * نمایش تمام سطوح حقوق
     */
    private boolean handleListRanks(CommandSender sender) {
        sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                plugin.getLanguageManager().getMessage("salary_list_header"));

        Set<String> ranks = plugin.getRankManager().getDefinedRanks();
        Map<String, String> displayNames = plugin.getRankManager().getAllRankDisplayNames();

        if (ranks.isEmpty()) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("salary_list_empty"));
            return true;
        }

        for (String rank : ranks) {
            double salary = plugin.getSalaryManager().getRankSalary(rank);
            String displayName = displayNames.getOrDefault(rank, rank);

            String message = plugin.getLanguageManager().getMessage("salary_list_entry")
                    .replace("{rank}", displayName)
                    .replace("{salary}", plugin.getSalaryManager().formatCurrency(salary));

            sender.sendMessage("§7  • " + message);
        }

        return true;
    }

    /**
     * Handle /salary pay - Force payment (admin only)
     * پرداخت فوری حقوق (فقط ادمین)
     */
    private boolean handlePay(CommandSender sender, String[] args) {
        if (!sender.hasPermission("policeplus.salary.pay")) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("no_permission"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("cmd_salary_pay_usage"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("player_not_found"));
            return true;
        }

        String rank = plugin.getRankManager().getPlayerRank(target);
        if (rank == null || rank.isEmpty()) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("salary_no_rank"));
            return true;
        }

        double salary = plugin.getSalaryManager().getRankSalary(rank);
        if (salary <= 0) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("salary_zero"));
            return true;
        }

        // Process payment
        plugin.getSalaryManager().payPlayer(target, salary);

        String message = plugin.getLanguageManager().getMessage("salary_paid_success")
                .replace("{player}", target.getName())
                .replace("{amount}", plugin.getSalaryManager().formatCurrency(salary));

        sender.sendMessage(plugin.getLanguageManager().getPrefix() + message);

        return true;
    }

    /**
     * Handle /salary enable - Enable salary system
     * فعال‌کردن سیستم حقوق
     */
    private boolean handleEnable(CommandSender sender) {
        if (!sender.hasPermission("policeplus.salary.admin")) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("no_permission"));
            return true;
        }

        if (plugin.getSalaryManager().isEnabled()) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("salary_already_enabled"));
            return true;
        }

        plugin.getSalaryManager().setEnabled(true);
        sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                plugin.getLanguageManager().getMessage("salary_enabled"));

        return true;
    }

    /**
     * Handle /salary disable - Disable salary system
     * غیرفعال‌کردن سیستم حقوق
     */
    private boolean handleDisable(CommandSender sender) {
        if (!sender.hasPermission("policeplus.salary.admin")) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("no_permission"));
            return true;
        }

        if (!plugin.getSalaryManager().isEnabled()) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("salary_already_disabled"));
            return true;
        }

        plugin.getSalaryManager().setEnabled(false);
        sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                plugin.getLanguageManager().getMessage("salary_disabled"));

        return true;
    }

    /**
     * Handle /salary status - Show system status
     * نمایش وضعیت سیستم
     */
    private boolean handleStatus(CommandSender sender) {
        if (!sender.hasPermission("policeplus.salary.admin")) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("no_permission"));
            return true;
        }

        String status = plugin.getSalaryManager().isEnabled() ? "§a✓ Enabled" : "§c✗ Disabled";
        sender.sendMessage(plugin.getLanguageManager().getPrefix() + "§6Salary Status: " + status);

        return true;
    }

    /**
     * Handle /salary help - Show help
     * نمایش کمک
     */
    private boolean handleHelp(CommandSender sender) {
        sender.sendMessage(plugin.getLanguageManager().getPrefix() + "§6=== Salary Commands ===");
        sender.sendMessage("§e/salary info §7- Check your salary information");
        sender.sendMessage("§e/salary list §7- Show all salary tiers");

        if (sender.hasPermission("policeplus.salary.pay")) {
            sender.sendMessage("§e/salary pay <player> §7- Force payment");
        }

        if (sender.hasPermission("policeplus.salary.admin")) {
            sender.sendMessage("§e/salary enable §7- Enable salary system");
            sender.sendMessage("§e/salary disable §7- Disable salary system");
            sender.sendMessage("§e/salary status §7- Show system status");
        }

        return true;
    }
}
