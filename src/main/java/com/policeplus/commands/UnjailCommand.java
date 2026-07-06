package com.policeplus.commands;

import com.policeplus.PolicePlus;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class UnjailCommand implements CommandExecutor {

    private final PolicePlus plugin;

    public UnjailCommand(PolicePlus plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Permission check — must be a cop (policeplus.police, policeplus.admin, or op)
        // Console senders are allowed (they bypass permission checks)
        if (sender instanceof Player && !PolicePlus.isCop((Player) sender)) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("no_permission"));
            return true;
        }

        // Usage: /unjail <player>
        if (args.length < 1) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    "&cUsage: /unjail <player>");
            return true;
        }

        // Find the target player (must be online)
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("player_not_found"));
            return true;
        }

        // Check if the target is currently jailed
        if (!plugin.getJailManager().isJailed(target)) {
            sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("player_not_jailed"));
            return true;
        }

        // Release the player
        plugin.getJailManager().releasePlayer(target);

        // Notify the executing cop
        sender.sendMessage(plugin.getLanguageManager().getPrefix() +
                plugin.getLanguageManager().getMessage("player_unjailed"));

        return true;
    }
}