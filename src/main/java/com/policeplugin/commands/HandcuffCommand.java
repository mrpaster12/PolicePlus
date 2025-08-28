package com.policeplugin.commands;

import com.policeplugin.PolicePlugin;
import com.policeplugin.managers.HandcuffManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class HandcuffCommand implements CommandExecutor {

    private final PolicePlugin plugin;
    private final HandcuffManager manager;

    public HandcuffCommand(PolicePlugin plugin) {
        this.plugin = plugin;
        this.manager = plugin.getHandcuffManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("cuffe")) {
            if (args.length >= 1 && args[0].equalsIgnoreCase("give")) return give(sender, args);
            if (!(sender instanceof Player)) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("handcuff.console"));
                return true;
            }
            Player p = (Player) sender;
            return cuffCmd(p, args);
        } else if (command.getName().equalsIgnoreCase("uncuffe")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(plugin.getLanguageManager().getMessage("handcuff.console"));
                return true;
            }
            Player p = (Player) sender;
            return uncuffCmd(p, args);
        }
        return false;
    }

    private boolean cuffCmd(Player p, String[] args) {
        if (!p.hasPermission("policeplugin.handcuff.cuff")) {
            p.sendMessage(plugin.getLanguageManager().getMessage("no_permission"));
            return true;
        }
        Player target;
        if (args.length < 1) {
            target = findNearest(p, 3.0);
            if (target == null) {
                p.sendMessage(plugin.getLanguageManager().getMessage("handcuff.no_player_nearby"));
                return true;
            }
        } else {
            target = Bukkit.getPlayer(args[0]);
        }
        if (target == null) {
            p.sendMessage(plugin.getLanguageManager().getMessage("player_not_found"));
            return true;
        }
        if (target.equals(p)) {
            p.sendMessage(plugin.getLanguageManager().getMessage("handcuff.cannot_cuff_self"));
            return true;
        }
        if (manager.isCuffed(target)) {
            p.sendMessage(plugin.getLanguageManager().getMessage("handcuff.already_cuffed"));
            return true;
        }
        if (manager.cuffPlayer(p, target)) {
            p.sendMessage(plugin.getLanguageManager().getMessage("handcuff.cuffed", "player", target.getName()));
            target.sendMessage(plugin.getLanguageManager().getMessage("handcuff.cuffed_by", "player", p.getName()));
        }
        return true;
    }

    private boolean uncuffCmd(Player p, String[] args) {
        if (!p.hasPermission("policeplugin.handcuff.uncuff")) {
            p.sendMessage(plugin.getLanguageManager().getMessage("no_permission"));
            return true;
        }
        if (args.length < 1) {
            p.sendMessage(plugin.getLanguageManager().getMessage("handcuff.usage.uncuff"));
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            p.sendMessage(plugin.getLanguageManager().getMessage("player_not_found"));
            return true;
        }
        if (!manager.isCuffed(target)) {
            p.sendMessage(plugin.getLanguageManager().getMessage("handcuff.not_cuffed"));
            return true;
        }
        if (manager.uncuffPlayer(target)) {
            p.sendMessage(plugin.getLanguageManager().getMessage("handcuff.uncuffed", "player", target.getName()));
            target.sendMessage(plugin.getLanguageManager().getMessage("handcuff.uncuffed_by", "player", p.getName()));
        }
        return true;
    }

    private boolean give(CommandSender sender, String[] args) {
        if (!sender.hasPermission("policeplugin.handcuff.give")) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("no_permission"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("handcuff.usage.give"));
            return true;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("player_not_found"));
            return true;
        }
        int amount = 1;
        if (args.length > 2) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[2])));
            } catch (NumberFormatException ignored) {}
        }
        var item = plugin.getHandcuffManager().createHandcuffItem();
        item.setAmount(amount);
        var leftover = target.getInventory().addItem(item);
        if (leftover.isEmpty()) {
            sender.sendMessage(plugin.getLanguageManager().getMessage("handcuff.given", "player", target.getName(), "amount", String.valueOf(amount)));
            target.sendMessage(plugin.getLanguageManager().getMessage("handcuff.received", "amount", String.valueOf(amount)));
        } else {
            sender.sendMessage(plugin.getLanguageManager().getMessage("handcuff.inventory_full"));
        }
        return true;
    }

    private Player findNearest(Player p, double max) {
        Player best = null; double dmin = Double.MAX_VALUE;
        for (Player t : p.getWorld().getPlayers()) {
            if (t.equals(p)) continue; double d = p.getLocation().distance(t.getLocation());
            if (d <= max && d < dmin) { dmin = d; best = t; }
        }
        return best;
    }
}


