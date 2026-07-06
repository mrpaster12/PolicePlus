package com.policeplus.commands;

import com.policeplus.PolicePlus;
import com.policeplus.managers.LogManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class LogCommand implements CommandExecutor {
    private final PolicePlus plugin;

    public LogCommand(PolicePlus plugin) {
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
            case "player":
                return handlePlayer(sender, args);
            case "action":
                return handleAction(sender, args);
            case "date":
                return handleDate(sender, args);
            case "today":
                return handleToday(sender);
            case "clean":
                return handleClean(sender, args);
            default:
                sendUsage(sender);
                return true;
        }
    }

    private boolean handlePlayer(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    "§cUsage: /log player <player>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("player_not_found",
                            "player", args[1]));
            return true;
        }

        List<LogManager.LogEntry> logs = plugin.getLogManager().getLogsForTarget(target);

        sender.sendMessage(plugin.getLanguageManager().getPrefix() + "§6=== Logs for " +
                target.getName() + " ===");

        if (logs.isEmpty()) {
            sender.sendMessage("§cNo logs found.");
            return true;
        }

        // Show last 10 logs
        int start = Math.max(0, logs.size() - 10);
        for (int i = start; i < logs.size(); i++) {
            LogManager.LogEntry entry = logs.get(i);
            sender.sendMessage("§a" + plugin.getLogManager().formatLogEntry(entry));
        }

        if (logs.size() > 10) {
            sender.sendMessage("§7(Showing last 10 of " + logs.size() + " logs)");
        }

        return true;
    }

    private boolean handleAction(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    "§cUsage: /log action <action>");
            return true;
        }

        String action = args[1].toUpperCase();
        List<LogManager.LogEntry> logs = plugin.getLogManager().getLogsByAction(action);

        sender.sendMessage(plugin.getLanguageManager().getPrefix() + "§6=== Logs for action: " +
                action + " ===");

        if (logs.isEmpty()) {
            sender.sendMessage("§cNo logs found.");
            return true;
        }

        // Show last 10 logs
        int start = Math.max(0, logs.size() - 10);
        for (int i = start; i < logs.size(); i++) {
            LogManager.LogEntry entry = logs.get(i);
            sender.sendMessage("§a" + plugin.getLogManager().formatLogEntry(entry));
        }

        if (logs.size() > 10) {
            sender.sendMessage("§7(Showing last 10 of " + logs.size() + " logs)");
        }

        return true;
    }

    private boolean handleDate(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    "§cUsage: /log date <YYYY-MM-DD> [endDate]");
            return true;
        }

        String startDate = args[1];
        String endDate = args.length > 2 ? args[2] : startDate;

        List<LogManager.LogEntry> logs = plugin.getLogManager().getLogsForRange(startDate, endDate);

        sender.sendMessage(plugin.getLanguageManager().getPrefix() + "§6=== Logs from " +
                startDate + " to " + endDate + " ===");

        if (logs.isEmpty()) {
            sender.sendMessage("§cNo logs found.");
            return true;
        }

        // Show all logs for date range
        int pageSize = 10;
        int page = 1;
        if (args.length > 3) {
            try {
                page = Integer.parseInt(args[3]);
            } catch (NumberFormatException ignored) {
            }
        }

        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, logs.size());

        for (int i = start; i < end; i++) {
            LogManager.LogEntry entry = logs.get(i);
            sender.sendMessage("§a" + plugin.getLogManager().formatLogEntry(entry));
        }

        int totalPages = (int) Math.ceil((double) logs.size() / pageSize);
        sender.sendMessage("§7(Page " + page + " of " + totalPages + ", Total: " + logs.size() + ")");

        return true;
    }

    private boolean handleToday(CommandSender sender) {
        String today = plugin.getLogManager().getTodayKey();
        List<LogManager.LogEntry> logs = plugin.getLogManager().getLogsForDate(today);

        sender.sendMessage(plugin.getLanguageManager().getPrefix() + "§6=== Today's Logs ===");

        if (logs.isEmpty()) {
            sender.sendMessage("§cNo logs for today.");
            return true;
        }

        for (LogManager.LogEntry entry : logs) {
            sender.sendMessage("§a" + plugin.getLogManager().formatLogEntry(entry));
        }

        sender.sendMessage("§7(Total: " + logs.size() + " logs)");

        return true;
    }

    private boolean handleClean(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    "§cUsage: /log clean <days>");
            return true;
        }

        int days;
        try {
            days = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    "§cInvalid number: " + args[1]);
            return true;
        }

        plugin.getLogManager().clearOldLogs(days);

        String message = plugin.getLanguageManager().getMessage("logs_cleaned", "days", String.valueOf(days));
        sender.sendMessage(plugin.getLanguageManager().getPrefix() + message);

        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(plugin.getLanguageManager().getPrefix() + "§6=== Log Command Usage ===");
        sender.sendMessage("§a/log player <player> §7- Show logs for player");
        sender.sendMessage("§a/log action <action> §7- Show logs by action");
        sender.sendMessage("§a/log date <YYYY-MM-DD> [end] [page] §7- Show logs for date range");
        sender.sendMessage("§a/log today §7- Show today's logs");
        sender.sendMessage("§a/log clean <days> §7- Delete logs older than N days");
    }
}
