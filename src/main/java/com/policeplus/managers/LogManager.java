package com.policeplus.managers;

import com.policeplus.PolicePlus;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LogManager {
    private final PolicePlus plugin;
    private final Map<String, List<LogEntry>> logs;
    private final File logsFolder;
    private final SimpleDateFormat dateFormat;
    private final SimpleDateFormat fileFormat;

    public static class LogEntry {
        public final long timestamp;
        public final String action;
        public final String actor;
        public final String target;
        public final String reason;
        public final Map<String, String> details;

        public LogEntry(String action, String actor, String target, String reason) {
            this.timestamp = System.currentTimeMillis();
            this.action = action;
            this.actor = actor;
            this.target = target;
            this.reason = reason;
            this.details = new ConcurrentHashMap<>();
        }

        // Constructor for loading from file
        public LogEntry(String action, String actor, String target, String reason, long timestamp) {
            this.timestamp = timestamp;
            this.action = action;
            this.actor = actor;
            this.target = target;
            this.reason = reason;
            this.details = new ConcurrentHashMap<>();
        }

        public void addDetail(String key, String value) {
            details.put(key, value);
        }

        public String getFormattedTime() {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return sdf.format(new Date(timestamp));
        }
    }

    public LogManager(PolicePlus plugin) {
        this.plugin = plugin;
        this.logs = new ConcurrentHashMap<>();
        this.logsFolder = new File(plugin.getDataFolder(), "logs");
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        this.fileFormat = new SimpleDateFormat("yyyy-MM-dd");

        if (!logsFolder.exists()) {
            logsFolder.mkdirs();
        }
        loadLogs();
    }

    /**
     * Log a wanted level change
     */
    public void logWantedChange(Player police, Player target, int newLevel, String reason) {
        LogEntry entry = new LogEntry("WANTED_CHANGE",
                police.getName(), target.getName(), reason);
        entry.addDetail("new_level", String.valueOf(newLevel));
        entry.addDetail("target_uuid", target.getUniqueId().toString());
        entry.addDetail("police_uuid", police.getUniqueId().toString());
        addLog(entry);
    }

    /**
     * Log a jail action
     */
    public void logJail(Player police, Player target, String jailName, int minutes, String reason) {
        LogEntry entry = new LogEntry("JAIL",
                police.getName(), target.getName(), reason);
        entry.addDetail("jail_name", jailName);
        entry.addDetail("duration_minutes", String.valueOf(minutes));
        entry.addDetail("target_uuid", target.getUniqueId().toString());
        entry.addDetail("police_uuid", police.getUniqueId().toString());
        addLog(entry);
    }

    /**
     * Log a handcuff action
     */
    public void logHandcuff(Player police, Player target, String action) {
        LogEntry entry = new LogEntry("HANDCUFF_" + action.toUpperCase(),
                police.getName(), target.getName(), "");
        entry.addDetail("target_uuid", target.getUniqueId().toString());
        entry.addDetail("police_uuid", police.getUniqueId().toString());
        addLog(entry);
    }

    /**
     * Log a rank change
     */
    public void logRankChange(String changer, Player target, String newRank) {
        LogEntry entry = new LogEntry("RANK_CHANGE", changer, target.getName(), "");
        entry.addDetail("new_rank", newRank);
        entry.addDetail("target_uuid", target.getUniqueId().toString());
        addLog(entry);
    }

    /**
     * Log a bounty change
     */
    public void logBountyChange(Player issuer, Player target, double amount, String reason) {
        LogEntry entry = new LogEntry("BOUNTY_CHANGE",
                issuer.getName(), target.getName(), reason);
        entry.addDetail("bounty_amount", String.valueOf(amount));
        entry.addDetail("target_uuid", target.getUniqueId().toString());
        entry.addDetail("issuer_uuid", issuer.getUniqueId().toString());
        addLog(entry);
    }

    /**
     * Log a general action
     */
    public void logAction(String action, String actor, String target, String reason, Map<String, String> details) {
        LogEntry entry = new LogEntry(action, actor, target, reason);
        if (details != null) {
            entry.details.putAll(details);
        }
        addLog(entry);
    }

    /**
     * Log salary payment
     * لاگ کردن پرداخت حقوق
     */
    public void logSalaryPayment(Player player, double amount) {
        LogEntry entry = new LogEntry("SALARY_PAYMENT", "SYSTEM", player.getName(),
                "Automatic salary payment");
        entry.addDetail("amount", String.valueOf(amount));
        entry.addDetail("player_uuid", player.getUniqueId().toString());
        addLog(entry);
    }

    private void addLog(LogEntry entry) {
        String dateKey = fileFormat.format(new Date(entry.timestamp));
        logs.computeIfAbsent(dateKey, k -> new ArrayList<>()).add(entry);

        // Auto-save asynchronously
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::saveLogs);
    }

    /**
     * Get logs for a specific date
     */
    public List<LogEntry> getLogsForDate(String date) {
        return logs.getOrDefault(date, new ArrayList<>());
    }

    /**
     * Get logs for a specific player (as target)
     */
    public List<LogEntry> getLogsForTarget(Player target) {
        String targetName = target.getName();
        List<LogEntry> result = new ArrayList<>();
        for (List<LogEntry> dateLog : logs.values()) {
            for (LogEntry entry : dateLog) {
                if (entry.target.equalsIgnoreCase(targetName)) {
                    result.add(entry);
                }
            }
        }
        return result;
    }

    /**
     * Get logs for a specific action
     */
    public List<LogEntry> getLogsByAction(String action) {
        List<LogEntry> result = new ArrayList<>();
        for (List<LogEntry> dateLog : logs.values()) {
            for (LogEntry entry : dateLog) {
                if (entry.action.equalsIgnoreCase(action)) {
                    result.add(entry);
                }
            }
        }
        return result;
    }

    /**
     * Get logs for a date range
     */
    public List<LogEntry> getLogsForRange(String startDate, String endDate) {
        List<LogEntry> result = new ArrayList<>();
        for (Map.Entry<String, List<LogEntry>> entry : logs.entrySet()) {
            if (entry.getKey().compareTo(startDate) >= 0 && entry.getKey().compareTo(endDate) <= 0) {
                result.addAll(entry.getValue());
            }
        }
        return result;
    }

    /**
     * Format a log entry for display
     */
    public String formatLogEntry(LogEntry entry) {
        return String.format("[%s] %s: %s → %s (reason: %s)",
                entry.getFormattedTime(), entry.action, entry.actor, entry.target, entry.reason);
    }

    /**
     * Get today's date in log format
     */
    public String getTodayKey() {
        return fileFormat.format(new Date());
    }

    private void loadLogs() {
        File[] files = logsFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String dateKey = file.getName().replace(".yml", "");
                FileConfiguration config = YamlConfiguration.loadConfiguration(file);
                List<LogEntry> dateLog = new ArrayList<>();

                if (config.contains("logs")) {
                    for (String entryKey : config.getConfigurationSection("logs").getKeys(false)) {
                        String basePath = "logs." + entryKey + ".";
                        long timestamp = config.getLong(basePath + "timestamp", System.currentTimeMillis());
                        LogEntry entry = new LogEntry(
                                config.getString(basePath + "action", "UNKNOWN"),
                                config.getString(basePath + "actor", "UNKNOWN"),
                                config.getString(basePath + "target", "UNKNOWN"),
                                config.getString(basePath + "reason", ""),
                                timestamp);

                        // Restore details
                        if (config.contains(basePath + "details")) {
                            for (String detailKey : config.getConfigurationSection(basePath + "details")
                                    .getKeys(false)) {
                                entry.addDetail(detailKey, config.getString(basePath + "details." + detailKey));
                            }
                        }
                        dateLog.add(entry);
                    }
                }
                logs.put(dateKey, dateLog);
            }
        }
    }

    public void saveLogs() {
        for (Map.Entry<String, List<LogEntry>> entry : logs.entrySet()) {
            File file = new File(logsFolder, entry.getKey() + ".yml");
            FileConfiguration config = new YamlConfiguration();

            int index = 0;
            for (LogEntry log : entry.getValue()) {
                String basePath = "logs." + index + ".";
                config.set(basePath + "timestamp", log.timestamp);
                config.set(basePath + "action", log.action);
                config.set(basePath + "actor", log.actor);
                config.set(basePath + "target", log.target);
                config.set(basePath + "reason", log.reason);

                for (Map.Entry<String, String> detail : log.details.entrySet()) {
                    config.set(basePath + "details." + detail.getKey(), detail.getValue());
                }
                index++;
            }

            try {
                config.save(file);
            } catch (IOException e) {
                plugin.getLogger().severe("Could not save logs: " + e.getMessage());
            }
        }
    }

    public void clearOldLogs(int days) {
        long cutoffTime = System.currentTimeMillis() - (days * 24L * 60 * 60 * 1000);
        List<String> keysToRemove = new ArrayList<>();

        for (Map.Entry<String, List<LogEntry>> entry : logs.entrySet()) {
            entry.getValue().removeIf(log -> log.timestamp < cutoffTime);
            if (entry.getValue().isEmpty()) {
                keysToRemove.add(entry.getKey());
            }
        }

        for (String key : keysToRemove) {
            logs.remove(key);
            new File(logsFolder, key + ".yml").delete();
        }
    }
}
