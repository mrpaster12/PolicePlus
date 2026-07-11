package com.policeplus.commands;

import com.policeplus.PolicePlus;
import com.policeplus.utils.PermissionUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import com.policeplus.managers.JailManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class PoliceTabCompleter implements TabCompleter {

    private final PolicePlus plugin;

    public PoliceTabCompleter(PolicePlus plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String commandName = command.getName().toLowerCase();

        switch (commandName) {
            case "wanted":
            case "w":
                return completeWantedCommand(sender, args);
            case "compass":
            case "c":
                return completeCompassCommand(sender, args);
            case "jail":
            case "j":
                return completeJailCommand(sender, args);
            case "cuffe":
            case "cuff":
                return completeCuffCommand(sender, args);
            case "uncuffe":
            case "uncuff":
                return completeUncuffCommand(sender, args);
            case "police":
            case "p":
                return completePoliceCommand(sender, args);
            case "rank":
            case "r":
                return completeRankCommand(sender, args);
            case "bounty":
            case "b":
                return completeBountyCommand(sender, args);
            case "log":
            case "l":
                return completeLogCommand(sender, args);
            case "salary":
            case "sal":
                return completeSalaryCommand(sender, args);
            case "stats":
            case "stat":
                return completeStatsCommand(sender, args);
            case "unjail":
                return completeUnjailCommand(sender, args);
            default:
                return new ArrayList<>();
        }
    }

    // ========================= /wanted =========================

    private List<String> getReasonSuggestions() {
        return Arrays.asList("theft", "murder", "assault", "robbery", "vandalism", "trespassing");
    }

    private List<String> completeWantedCommand(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            if (sender.hasPermission("policeplus.wanted")) {
                completions.add("set");
                completions.add("add");
                completions.add("list");
                completions.add("jail");
                completions.add("unjail");
                completions.add("gui");
            }
            completions.add("help");
            if (sender.hasPermission("policeplus.admin")) {
                completions.add("reload");
            }
            return filterStartsWith(completions, args[0]);
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "set":
                case "add":
                case "jail":
                case "unjail":
                    return filterStartsWith(getOnlinePlayerNames(), args[1]);
                default:
                    return new ArrayList<>();
            }
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("add")) {
                return filterStartsWith(getWantedLevelOptions(), args[2]);
            }
        }

        if (args.length == 4) {
            if (args[0].equalsIgnoreCase("add")) {
                return filterStartsWith(getReasonSuggestions(), args[3]);
            }
        }

        return new ArrayList<>();
    }

    // ========================= /jail =========================

    private List<String> completeJailCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("policeplus.wanted")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            List<String> completions = new ArrayList<>(Arrays.asList("create", "delete", "spawn", "list", "help"));
            if (sender.hasPermission("policeplus.admin")) {
                completions.add("createmine");
                completions.add("setpos1");
                completions.add("setpos2");
                completions.add("player");
            }
            return filterStartsWith(completions, args[0]);
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "delete":
                    return filterStartsWith(getJailNames(), args[1]);
                case "create":
                    return Arrays.asList("<jail_name>");
                case "createmine":
                    return Arrays.asList("<jail_name>");
                case "setpos1":
                    return filterStartsWith(getMineJailNames(), args[1]);
                case "setpos2":
                    return filterStartsWith(getMineJailNames(), args[1]);
                case "player":
                    return filterStartsWith(getOnlinePlayerNames(), args[1]);
                default:
                    return new ArrayList<>();
            }
        }

        if (args.length == 3) {
            switch (args[0].toLowerCase()) {
                case "create":
                case "createmine":
                    return Arrays.asList("<jail_id>");
                case "player":
                    return filterStartsWith(getJailNames(), args[2]);
                default:
                    return new ArrayList<>();
            }
        }

        return new ArrayList<>();
    }

    // ========================= /compass =========================

    private List<String> completeCompassCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("policeplus.compass")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            return filterStartsWith(getWantedPlayerNames(), args[0]);
        }

        return new ArrayList<>();
    }

    // ========================= /police =========================

    private List<String> completePoliceCommand(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            completions.add("wanted");
            if (sender instanceof org.bukkit.entity.Player && PermissionUtils.hasPolicePermission((org.bukkit.entity.Player) sender, "policeplus.gui")) {
                completions.add("jail");
                completions.add("job");
                completions.add("help");
            }
            if (sender.hasPermission("policeplus.admin")) {
                completions.add("reload");
                completions.add("debugjails");
            }
            return filterStartsWith(completions, args[0]);
        }

        // /police wanted <subcommand> ...
        if (args[0].equalsIgnoreCase("wanted")) {
            return completeWantedSubcommand(sender, args);
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "job":
                    return filterStartsWith(Arrays.asList("set"), args[1]);
                default:
                    return new ArrayList<>();
            }
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("job") && args[1].equalsIgnoreCase("set")) {
                return filterStartsWith(getOnlinePlayerNames(), args[2]);
            }
        }

        if (args.length == 4) {
            if (args[0].equalsIgnoreCase("job") && args[1].equalsIgnoreCase("set")) {
                return filterStartsWith(getRankNames(), args[3]);
            }
        }

        return new ArrayList<>();
    }

    /**
     * Tab completions for /police wanted <subcommand>.
     * args[0] = "wanted", args[1] = subcommand, args[2] = player, args[3] = level, etc.
     */
    private List<String> completeWantedSubcommand(CommandSender sender, String[] args) {
        // args.length == 2 means we are typing the wanted subcommand (args[1])
        if (args.length == 2) {
            List<String> completions = new ArrayList<>();
            if (sender.hasPermission("policeplus.wanted")) {
                completions.add("set");
                completions.add("add");
                completions.add("list");
                completions.add("jail");
                completions.add("unjail");
                completions.add("gui");
            }
            completions.add("help");
            if (sender.hasPermission("policeplus.admin")) {
                completions.add("reload");
            }
            return filterStartsWith(completions, args[1]);
        }

        // args.length == 3 means we are typing the player name (args[2])
        if (args.length == 3) {
            switch (args[1].toLowerCase()) {
                case "set":
                case "add":
                case "jail":
                case "unjail":
                    return filterStartsWith(getOnlinePlayerNames(), args[2]);
                default:
                    return new ArrayList<>();
            }
        }

        // args.length == 4 means we are typing the wanted level (args[3])
        if (args.length == 4) {
            if (args[1].equalsIgnoreCase("set") || args[1].equalsIgnoreCase("add")) {
                return filterStartsWith(getWantedLevelOptions(), args[3]);
            }
        }

        return new ArrayList<>();
    }

    // ========================= /cuffe =========================

    private List<String> completeCuffCommand(CommandSender sender, String[] args) {
        boolean canUse = sender instanceof Player && PermissionUtils.hasPolicePermission((Player) sender, "policeplus.handcuff.cuff");
        boolean canGive = sender instanceof Player && PermissionUtils.hasPolicePermission((Player) sender, "policeplus.handcuff.give");

        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            if (canGive) list.add("give");
            if (canUse) list.addAll(getOnlinePlayerNames());
            return filterStartsWith(list, args[0]);
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("give") && canGive) {
                return filterStartsWith(getOnlinePlayerNames(), args[1]);
            }
        }

        return new ArrayList<>();
    }

    // ========================= /uncuffe =========================

    private List<String> completeUncuffCommand(CommandSender sender, String[] args) {
        boolean canUse = sender instanceof Player && PermissionUtils.hasPolicePermission((Player) sender, "policeplus.handcuff.uncuff");

        if (args.length == 1 && canUse) {
            return filterStartsWith(getOnlinePlayerNames(), args[0]);
        }

        return new ArrayList<>();
    }

    // ========================= /rank =========================

    private List<String> completeRankCommand(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            completions.add("list");
            completions.add("info");
            if (sender.hasPermission("policeplus.rank.manage")) {
                completions.add("set");
                completions.add("remove");
            }
            return filterStartsWith(completions, args[0]);
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "set":
                case "remove":
                case "info":
                    return filterStartsWith(getOnlinePlayerNames(), args[1]);
                default:
                    return new ArrayList<>();
            }
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("set")) {
                return filterStartsWith(getRankNames(), args[2]);
            }
        }

        return new ArrayList<>();
    }

    // ========================= /bounty =========================

    private List<String> completeBountyCommand(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>(Arrays.asList("set", "add", "remove", "delete", "list", "info", "help"));
            return filterStartsWith(completions, args[0]);
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "remove":
                case "delete":
                    // Suggest only players who have active bounties
                    return filterStartsWith(getBountyHolderNames(), args[1]);
                case "set":
                case "add":
                case "info":
                    return filterStartsWith(getOnlinePlayerNames(), args[1]);
                default:
                    return new ArrayList<>();
            }
        }

        return new ArrayList<>();
    }

    // ========================= /log =========================

    private List<String> completeLogCommand(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> completions = Arrays.asList("player", "action", "date", "today", "clean");
            return filterStartsWith(completions, args[0]);
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("player")) {
                return filterStartsWith(getOnlinePlayerNames(), args[1]);
            }
        }

        return new ArrayList<>();
    }

    // ========================= /salary =========================

    private List<String> completeSalaryCommand(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>(Arrays.asList("info", "list"));
            if (sender.hasPermission("policeplus.salary.admin")) {
                completions.add("enable");
                completions.add("disable");
                completions.add("status");
            }
            if (sender.hasPermission("policeplus.salary.pay")) {
                completions.add("pay");
            }
            return filterStartsWith(completions, args[0]);
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("pay") && sender.hasPermission("policeplus.salary.pay")) {
                return filterStartsWith(getOnlinePlayerNames(), args[1]);
            }
        }

        return new ArrayList<>();
    }

    // ========================= /stats =========================

    private List<String> completeStatsCommand(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> completions = Arrays.asList("me", "top", "arrests", "bounty", "efficiency");
            return filterStartsWith(completions, args[0]);
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("me") || args[0].equalsIgnoreCase("top")) {
                return new ArrayList<>();
            }
            return filterStartsWith(getOnlinePlayerNames(), args[1]);
        }

        return new ArrayList<>();
    }

    // ========================= /unjail =========================

    private List<String> completeUnjailCommand(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return filterStartsWith(getOnlinePlayerNames(), args[0]);
        }
        return new ArrayList<>();
    }

    // ========================= Utility Methods =========================

    private List<String> getOnlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .collect(Collectors.toList());
    }

    private List<String> getWantedLevelOptions() {
        int maxLevel = plugin.getConfigManager().getMaxWantedLevel();
        List<String> levelOptions = new ArrayList<>();
        levelOptions.add("0");
        for (int i = 1; i <= maxLevel; i++) {
            levelOptions.add(String.valueOf(i));
        }
        return levelOptions;
    }

    private List<String> getWantedPlayerNames() {
        List<String> wantedPlayers = new ArrayList<>();
        Map<UUID, Integer> wantedMap = plugin.getWantedManager().getWantedPlayers();

        for (Map.Entry<UUID, Integer> entry : wantedMap.entrySet()) {
            Player online = Bukkit.getPlayer(entry.getKey());
            if (online != null) {
                wantedPlayers.add(online.getName());
            }
        }

        return wantedPlayers;
    }

    private List<String> getJailNames() {
        return new ArrayList<>(plugin.getJailManager().getAllJails().keySet());
    }

    private List<String> getMineJailNames() {
        List<String> names = new ArrayList<>();
        for (Map.Entry<String, JailManager.JailInfo> entry : plugin.getJailManager().getAllJails().entrySet()) {
            if (entry.getValue().isMine()) {
                names.add(entry.getKey());
            }
        }
        return names;
    }

    private List<String> getBountyHolderNames() {
        List<String> names = new ArrayList<>();
        for (java.util.UUID uuid : plugin.getBountyManager().getAllBounties().keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                names.add(player.getName());
            }
        }
        return names;
    }

    private List<String> getRankNames() {
        return new ArrayList<>(plugin.getRankManager().getDefinedRanks());
    }

    private List<String> filterStartsWith(List<String> completions, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return completions;
        }
        String lowerPrefix = prefix.toLowerCase();
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(lowerPrefix))
                .collect(Collectors.toList());
    }
}