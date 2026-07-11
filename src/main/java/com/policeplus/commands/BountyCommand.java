package com.policeplus.commands;

import com.policeplus.PolicePlus;
import com.policeplus.managers.BountyManager;
import com.policeplus.utils.PermissionUtils;
import net.milkbowl.vault.economy.Economy;
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
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "set":
            case "add":
                return handleAdd(sender, args);
            case "remove":
            case "delete":
                return handleRemove(sender, args);
            case "list":
                return handleList(sender);
            case "info":
                return handleInfo(sender, args);
            default:
                sendHelp(sender);
                return true;
        }
    }

    private boolean handleAdd(CommandSender sender, String[] args) {
        // Permission check — must have policeplus.bounty.set or master cop permission
        if (sender instanceof Player && !PermissionUtils.hasPolicePermission((Player) sender, "policeplus.bounty.set")) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("no_permission"));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("cmd_bounty_set_usage"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("player_not_found"));
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("bounty_invalid_amount",
                            "min", String.valueOf(plugin.getConfigManager().getBountyMin()),
                            "max", String.valueOf(plugin.getConfigManager().getBountyMax())));
            return true;
        }

        // Block negative and zero amounts immediately to prevent exploits
        if (amount <= 0) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    "§cBounty amount must be greater than zero!");
            return true;
        }

        double minBounty = plugin.getConfigManager().getBountyMin();
        double maxBounty = plugin.getConfigManager().getBountyMax();

        if (amount < minBounty || amount > maxBounty) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("bounty_invalid_amount",
                            "min", String.valueOf(minBounty),
                            "max", String.valueOf(maxBounty)));
            return true;
        }

        String reason = "";
        if (args.length > 3) {
            reason = String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length));
        }

        Player issuer = sender instanceof Player ? (Player) sender : null;

        // Economy check: deduct money from player sender if economy is enabled
        Economy economy = PolicePlus.getEconomy();
        if (economy != null && issuer != null) {
            double playerBalance = economy.getBalance(issuer);
            if (playerBalance < amount) {
                sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                        plugin.getLanguageManager().getMessage("bounty_insufficient_funds",
                                "balance", String.format("%.2f", playerBalance)));
                return true;
            }
            economy.withdrawPlayer(issuer, amount);
        }

        // Set the bounty via BountyManager (handles logging, stats, broadcast)
        if (issuer != null) {
            plugin.getBountyManager().setBounty(issuer, target, amount, reason);
        } else {
            // Console command — add bounty directly
            plugin.getBountyManager().addBounty(target.getUniqueId(), amount);
            String message = plugin.getLanguageManager().getMessage("bounty_set",
                    "player", target.getName(),
                    "amount", plugin.getBountyManager().formatCurrency(amount),
                    "issuer", "Console");
            Bukkit.broadcastMessage(plugin.getLanguageManager().getPrefix() + message);
        }

        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (sender instanceof Player && !PermissionUtils.hasPolicePermission((Player) sender, "policeplus.bounty.remove")) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("no_permission"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("cmd_bounty_remove_usage"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("player_not_found"));
            return true;
        }

        plugin.getBountyManager().removeBounty(target);
        sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                plugin.getLanguageManager().getMessage("bounty_removed_success",
                        "player", target.getName()));
        return true;
    }

    private boolean handleList(CommandSender sender) {
        List<String> list = plugin.getBountyManager().getFormattedBountyList();
        if (list.isEmpty()) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("bounty_list_empty"));
            return true;
        }

        sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                plugin.getLanguageManager().getMessage("bounty_list_header"));
        for (String entry : list) {
            sender.sendMessage(entry);
        }
        return true;
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            if (sender instanceof Player) {
                showBountyInfo(sender, (Player) sender);
            } else {
                sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                        plugin.getLanguageManager().getMessage("cmd_bounty_info_usage"));
            }
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("player_not_found"));
            return true;
        }

        showBountyInfo(sender, target);
        return true;
    }

    private void showBountyInfo(CommandSender sender, Player target) {
        BountyManager bm = plugin.getBountyManager();
        if (!bm.hasBounty(target)) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("bounty_info_none"));
            return;
        }

        sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                plugin.getLanguageManager().getMessage("bounty_info_header"));
        sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                plugin.getLanguageManager().getMessage("bounty_info_amount",
                        "amount", bm.formatCurrency(bm.getBounty(target))));

        String reason = bm.getBountyReason(target);
        if (reason != null && !reason.isEmpty()) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("bounty_info_reason",
                            "reason", reason));
        }

        Player issuer = bm.getBountyIssuer(target);
        if (issuer != null) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("bounty_info_issuer",
                            "issuer", issuer.getName()));
        }
    }

    private void sendHelp(CommandSender sender) {
        String prefix = plugin.getLanguageManager().getPrefix();
        sender.sendMessage(prefix + plugin.getLanguageManager().getMessage("cmd_bounty_usage"));
        sender.sendMessage(prefix + plugin.getLanguageManager().getMessage("cmd_bounty_set_usage"));
        sender.sendMessage(prefix + plugin.getLanguageManager().getMessage("cmd_bounty_add_usage"));
        sender.sendMessage(prefix + plugin.getLanguageManager().getMessage("cmd_bounty_remove_usage"));
        sender.sendMessage(prefix + plugin.getLanguageManager().getMessage("cmd_bounty_info_usage"));
    }
}