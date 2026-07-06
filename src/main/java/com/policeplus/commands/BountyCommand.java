package com.policeplus.commands;

import com.policeplus.PolicePlus;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public class BountyCommand implements CommandExecutor {
    private final PolicePlus plugin;

    public BountyCommand(PolicePlus plugin) {
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
            case "add":
                return handleAdd(sender, args);
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
                    "§cUsage: /bounty set <player> <amount> [reason]");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("player_not_found",
                            "player", args[1]));
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    "§cInvalid amount: " + args[2]);
            return true;
        }

        String reason = "";
        if (args.length > 3) {
            reason = String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length));
        }

        Player issuer = sender instanceof Player ? (Player) sender : null;
        if (issuer == null) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    "§cOnly players can issue bounties.");
            return true;
        }

        plugin.getBountyManager().setBounty(issuer, target, amount, reason);
        return true;
    }

    private boolean handleAdd(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    "§cUsage: /bounty add <player> <amount> [reason]");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("player_not_found",
                            "player", args[1]));
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    "§cInvalid amount: " + args[2]);
            return true;
        }

        String reason = "";
        if (args.length > 3) {
            reason = String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length));
        }

        Player issuer = sender instanceof Player ? (Player) sender : null;
        if (issuer == null) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    "§cOnly players can issue bounties.");
            return true;
        }

        plugin.getBountyManager().increaseBounty(issuer, target, amount, reason);
        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    "§cUsage: /bounty remove <player>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("player_not_found",
                            "player", args[1]));
            return true;
        }

        plugin.getBountyManager().removeBounty(target);

        String message = plugin.getLanguageManager().getMessage("bounty_removed",
                "player", target.getName());
        sender.sendMessage(plugin.getLanguageManager().getPrefix() + message);

        return true;
    }

    private boolean handleList(CommandSender sender) {
        sender.sendMessage(plugin.getLanguageManager().getPrefix() + "§6=== Active Bounties ===");
        List<Map.Entry<String, Double>> bounties = plugin.getBountyManager().getSortedBounties();

        if (bounties.isEmpty()) {
            sender.sendMessage("§cNo active bounties.");
            return true;
        }

        for (Map.Entry<String, Double> entry : bounties) {
            sender.sendMessage(String.format("§a%s §7→ §6%s",
                    entry.getKey(),
                    plugin.getBountyManager().formatCurrency(entry.getValue())));
        }
        return true;
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                        "§cUsage: /bounty info <player>");
                return true;
            }
            Player player = (Player) sender;
            double bounty = plugin.getBountyManager().getBounty(player);
            if (bounty <= 0) {
                sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                        "§cYou don't have a bounty on your head.");
            } else {
                sender.sendMessage(plugin.getLanguageManager().getPrefix() + "§6Your Bounty: " +
                        plugin.getBountyManager().formatCurrency(bounty));
                String reason = plugin.getBountyManager().getBountyReason(player);
                if (!reason.isEmpty()) {
                    sender.sendMessage("§7Reason: " + reason);
                }
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

        double bounty = plugin.getBountyManager().getBounty(target);
        if (bounty <= 0) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + "§c" +
                    target.getName() + " doesn't have a bounty.");
        } else {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() + "§6" +
                    target.getName() + "'s Bounty: " +
                    plugin.getBountyManager().formatCurrency(bounty));
            String reason = plugin.getBountyManager().getBountyReason(target);
            if (!reason.isEmpty()) {
                sender.sendMessage("§7Reason: " + reason);
            }
            Player issuer = plugin.getBountyManager().getBountyIssuer(target);
            if (issuer != null) {
                sender.sendMessage("§7Issued by: " + issuer.getName());
            }
        }

        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(plugin.getLanguageManager().getPrefix() + "§6=== Bounty Command Usage ===");
        sender.sendMessage("§a/bounty set <player> <amount> [reason] §7- Set bounty");
        sender.sendMessage("§a/bounty add <player> <amount> [reason] §7- Add to bounty");
        sender.sendMessage("§a/bounty remove <player> §7- Remove bounty");
        sender.sendMessage("§a/bounty list §7- List all bounties");
        sender.sendMessage("§a/bounty info [player] §7- Check bounty info");
    }
}
