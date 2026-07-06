package com.policeplus.managers;

import com.policeplus.PolicePlus;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * StatsManager - مدیریت آمار بازیکنان
 * Manages player statistics and performance tracking
 * ردیابی آمار و عملکرد بازیکنان
 */
public class StatsManager {
    private final PolicePlus plugin;
    private final Map<UUID, PlayerStats> playerStats;
    private final File statsFolder;
    private final File statsFile;

    public StatsManager(PolicePlus plugin) {
        this.plugin = plugin;
        this.playerStats = new ConcurrentHashMap<>();
        this.statsFolder = new File(plugin.getDataFolder(), "stats");
        this.statsFile = new File(statsFolder, "stats.yml");

        if (!statsFolder.exists()) {
            statsFolder.mkdirs();
        }

        loadStats();
    }

    /**
     * Player statistics inner class
     * کلاس آمار بازیکن
     */
    public static class PlayerStats {
        public final UUID playerUUID;
        public final String playerName;
        public long createdAt;

        public int arrests = 0;
        public int jails = 0;
        public int handcuffs = 0;
        public double totalBountyIssued = 0.0;
        public int timesPromoted = 0;
        public int timesDemoted = 0;
        public long playtimeSeconds = 0;
        public double totalSalaryEarned = 0.0;
        public int daysSinceJoin = 0;

        public PlayerStats(UUID playerUUID, String playerName) {
            this.playerUUID = playerUUID;
            this.playerName = playerName;
            this.createdAt = System.currentTimeMillis();
        }

        public double getAverageArrestsPerDay() {
            if (daysSinceJoin == 0)
                return 0;
            return (double) arrests / daysSinceJoin;
        }

        public double getEfficiencyRating() {
            // Rating based on arrests, jails, bounties issued
            double score = (arrests * 1.0) + (jails * 1.5) + (totalBountyIssued / 100);
            return Math.min(10.0, score);
        }

        public int getPlaytimeDays() {
            return (int) (playtimeSeconds / 86400); // 24*60*60
        }

        public int getPlaytimeHours() {
            return (int) ((playtimeSeconds % 86400) / 3600);
        }

        public int getPlaytimeMinutes() {
            return (int) ((playtimeSeconds % 3600) / 60);
        }
    }

    /**
     * Record an arrest
     * ثبت یک دستگیری
     */
    public void recordArrest(Player police) {
        UUID uuid = police.getUniqueId();
        PlayerStats stats = playerStats.computeIfAbsent(uuid,
                k -> new PlayerStats(uuid, police.getName()));
        stats.arrests++;
        markDirty();
    }

    /**
     * Record a jail
     * ثبت یک زندانی‌کردن
     */
    public void recordJail(Player police) {
        UUID uuid = police.getUniqueId();
        PlayerStats stats = playerStats.computeIfAbsent(uuid,
                k -> new PlayerStats(uuid, police.getName()));
        stats.jails++;
        markDirty();
    }

    /**
     * Record a handcuff
     * ثبت یک دستبندی
     */
    public void recordHandcuff(Player police) {
        UUID uuid = police.getUniqueId();
        PlayerStats stats = playerStats.computeIfAbsent(uuid,
                k -> new PlayerStats(uuid, police.getName()));
        stats.handcuffs++;
        markDirty();
    }

    /**
     * Record a bounty issued
     * ثبت یک مامورت‌های صادرشده
     */
    public void recordBountyIssued(Player issuer, double amount) {
        UUID uuid = issuer.getUniqueId();
        PlayerStats stats = playerStats.computeIfAbsent(uuid,
                k -> new PlayerStats(uuid, issuer.getName()));
        stats.totalBountyIssued += amount;
        markDirty();
    }

    /**
     * Record a promotion
     * ثبت یک ارتقا
     */
    public void recordPromotion(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerStats stats = playerStats.computeIfAbsent(uuid,
                k -> new PlayerStats(uuid, player.getName()));
        stats.timesPromoted++;
        markDirty();
    }

    /**
     * Record a demotion
     * ثبت یک تنزل
     */
    public void recordDemotion(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerStats stats = playerStats.computeIfAbsent(uuid,
                k -> new PlayerStats(uuid, player.getName()));
        stats.timesDemoted++;
        markDirty();
    }

    /**
     * Add salary to player's total
     * افزودن حقوق به کل بازیکن
     */
    public void addSalary(Player player, double amount) {
        UUID uuid = player.getUniqueId();
        PlayerStats stats = playerStats.computeIfAbsent(uuid,
                k -> new PlayerStats(uuid, player.getName()));
        stats.totalSalaryEarned += amount;
        markDirty();
    }

    /**
     * Get player stats
     * دریافت آمار بازیکن
     */
    public PlayerStats getPlayerStats(UUID playerUUID) {
        return playerStats.get(playerUUID);
    }

    /**
     * Get player stats by name
     * دریافت آمار بر اساس نام
     */
    public PlayerStats getPlayerStatsByName(String playerName) {
        return playerStats.values().stream()
                .filter(s -> s.playerName.equalsIgnoreCase(playerName))
                .findFirst()
                .orElse(null);
    }

    /**
     * Get top players by arrests
     * شناسایی بهترین‌ها بر اساس دستگیری
     */
    public List<PlayerStats> getTopArresters(int limit) {
        return playerStats.values().stream()
                .sorted(Comparator.comparingInt((PlayerStats p) -> p.arrests).reversed())
                .limit(limit)
                .toList();
    }

    /**
     * Get top players by efficiency
     * شناسایی بهترین‌ها بر اساس کارایی
     */
    public List<PlayerStats> getTopByEfficiency(int limit) {
        return playerStats.values().stream()
                .sorted(Comparator.comparingDouble(PlayerStats::getEfficiencyRating).reversed())
                .limit(limit)
                .toList();
    }

    /**
     * Get top bounty issuers
     * شناسایی بهترین‌های صادرکننده مامورت
     */
    public List<PlayerStats> getTopBountyIssuers(int limit) {
        return playerStats.values().stream()
                .sorted(Comparator.comparingDouble((PlayerStats p) -> p.totalBountyIssued).reversed())
                .limit(limit)
                .toList();
    }

    /**
     * Load stats from file
     * بارگذاری آمار از فایل
     */
    public void loadStats() {
        if (!statsFile.exists()) {
            plugin.getLogger().info("Stats file not found - creating new");
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(statsFile);

        if (config.contains("players")) {
            for (String uuidString : config.getConfigurationSection("players").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidString);
                    String basePath = "players." + uuidString + ".";

                    PlayerStats stats = new PlayerStats(uuid,
                            config.getString(basePath + "name", "Unknown"));

                    stats.arrests = config.getInt(basePath + "arrests", 0);
                    stats.jails = config.getInt(basePath + "jails", 0);
                    stats.handcuffs = config.getInt(basePath + "handcuffs", 0);
                    stats.totalBountyIssued = config.getDouble(basePath + "total_bounty_issued", 0.0);
                    stats.timesPromoted = config.getInt(basePath + "times_promoted", 0);
                    stats.timesDemoted = config.getInt(basePath + "times_demoted", 0);
                    stats.playtimeSeconds = config.getLong(basePath + "playtime_seconds", 0);
                    stats.totalSalaryEarned = config.getDouble(basePath + "total_salary_earned", 0.0);
                    stats.createdAt = config.getLong(basePath + "created_at", System.currentTimeMillis());

                    // Calculate days since join
                    stats.daysSinceJoin = Math.max(1,
                            (int) ((System.currentTimeMillis() - stats.createdAt) / 86400000));

                    playerStats.put(uuid, stats);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID format: " + uuidString);
                }
            }
        }

        plugin.getLogger().info("Loaded stats for " + playerStats.size() + " players");
    }

    /**
     * Save stats to file
     * ذخیره آمار در فایل
     */
    public void saveStats() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                FileConfiguration config = new YamlConfiguration();

                for (PlayerStats stats : playerStats.values()) {
                    String basePath = "players." + stats.playerUUID + ".";
                    config.set(basePath + "name", stats.playerName);
                    config.set(basePath + "arrests", stats.arrests);
                    config.set(basePath + "jails", stats.jails);
                    config.set(basePath + "handcuffs", stats.handcuffs);
                    config.set(basePath + "total_bounty_issued", stats.totalBountyIssued);
                    config.set(basePath + "times_promoted", stats.timesPromoted);
                    config.set(basePath + "times_demoted", stats.timesDemoted);
                    config.set(basePath + "playtime_seconds", stats.playtimeSeconds);
                    config.set(basePath + "total_salary_earned", stats.totalSalaryEarned);
                    config.set(basePath + "created_at", stats.createdAt);
                }

                config.save(statsFile);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to save stats: " + e.getMessage());
            }
        });
    }

    /**
     * Mark stats as dirty (needs saving)
     * علامت‌گذاری برای نیاز به ذخیره
     */
    private void markDirty() {
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, this::saveStats, 100L);
    }

    /**
     * Shutdown the stats manager
     * خاموشی مدیریت آمار
     */
    public void shutdown() {
        saveStats();
    }

    /**
     * Get all player stats
     * دریافت تمام آمار
     */
    public Collection<PlayerStats> getAllPlayerStats() {
        return playerStats.values();
    }

    /**
     * Get stats size
     * تعداد بازیکنان ثبت‌شده
     */
    public int getTotalPlayersTracked() {
        return playerStats.size();
    }
}
