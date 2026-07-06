package com.policeplus.commands;

import com.policeplus.PolicePlus;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * Backwards-compatibility wrapper: /wanted [args] is forwarded to /police wanted [args].
 * All logic lives in {@link PoliceCommand#handleWantedSubcommand}.
 */
public class WantedCommand implements CommandExecutor {

    private final PolicePlus plugin;

    public WantedCommand(PolicePlus plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Build the equivalent /police wanted args array
        String[] policeArgs = new String[args.length + 1];
        policeArgs[0] = "wanted";
        System.arraycopy(args, 0, policeArgs, 1, args.length);

        // Delegate to the /police command handler
        plugin.getCommand("police").getExecutor().onCommand(
                sender,
                plugin.getCommand("police"),
                "police",
                policeArgs
        );
        return true;
    }
}