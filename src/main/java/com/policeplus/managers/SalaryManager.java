package com.policeplus.managers;

import com.policeplus.PolicePlus;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SalaryManager - مدیریت حقوق‌های پلیسی
 * Manages automatic salary payments based on player ranks
 * پرداخت خودکار حقوق برای بازیکنان بر اساس رتبه‌شان
 */
public class SalaryManager {
    private final PolicePlus plugin;
    private final Map<UUID, Long> lastPaymentTime;
    private BukkitTask salaryTask;
    private boolean enabled;
    private long paymentInterval; // in ticks (20 ticks = 1 second)
    private double baseMultiplier;

    public SalaryManager(PolicePlus plugin) {
        this.plugin = plugin;
        this.lastPaymentTime = new ConcurrentHashMap<>();
        this.enabled = plugin.getConfig().getBoolean("salary.enabled", true);

        // Convert minutes to ticks (default 60 minutes = 72000 ticks)
        int minutes = plugin.getConfig().getInt("salary.payment_interval_minutes", 60);
        this.paymentInterval = (long) minutes * 60 * 20;

        this.baseMultiplier = plugin.getConfig().getDouble("salary.base_multiplier", 1.0);

        if (enabled) {
            startSalaryTask();
        }
    }

    /**
     * Start the automatic salary payment task
     * شروع کار خودکار پرداخت حقوق
     */
    private void startSalaryTask() {
        if (salaryTask != null) {
            salaryTask.cancel();
        }

        salaryTask = Bukkit.getScheduler().runTaskTimer(plugin, this::processPayments,
                paymentInterval, paymentInterval);

        plugin.getLogger().info("Salary system started - Payment interval: " +
                (paymentInterval / 20 / 60) + " minutes");
    }

    /**
     * Process salary payments for all online players
     * پردازش پرداخت حقوق برای تمام بازیکنان آنلاین
     */
    private void processPayments() {
        if (!enabled)
            return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerUUID = player.getUniqueId();

            // Check if player should be paid (not in jail)
            if (isPlayerInJail(player)) {
                continue;
            }

            // Get player's rank
            String playerRank = plugin.getRankManager().getPlayerRank(player);

            if (playerRank == null || playerRank.isEmpty()) {
                continue; // Player has no rank, no salary
            }

            // Get salary for this rank
            double salary = getRankSalary(playerRank);

            if (salary <= 0) {
                continue;
            }

            // Apply multipliers
            salary = applySalaryMultipliers(player, playerRank, salary);

            // Pay the player
            payPlayer(player, salary);

            // Update last payment time
            lastPaymentTime.put(playerUUID, System.currentTimeMillis());

            // Broadcast payment notification
            String message = plugin.getLanguageManager()
                    .getMessage("salary_received")
                    .replace("{amount}", formatCurrency(salary))
                    .replace("{rank}", plugin.getRankManager().getAllRankDisplayNames()
                            .getOrDefault(playerRank, playerRank));

            player.sendMessage(plugin.getLanguageManager().getPrefix() + message);
        }
    }

    /**
     * Get salary for a specific rank from config
     * دریافت حقوق برای رتبه‌ای خاص
     */
    public double getRankSalary(String rankKey) {
        String path = "ranks." + rankKey + ".salary";
        return plugin.getConfig().getDouble(path, 0.0);
    }

    /**
     * Apply salary multipliers (bonuses, penalties)
     * اعمال ضرایب حقوق (بونوس، کسر)
     */
    private double applySalaryMultipliers(Player player, String rank, double baseSalary) {
        double multiplier = baseMultiplier;

        // Online bonus multiplier
        if (plugin.getConfig().getBoolean("salary.online_bonus.enabled", false)) {
            int minutes = plugin.getConfig().getInt("salary.online_bonus.minutes_required", 30);
            long onlineTime = (System.currentTimeMillis() - getPlayerOnlineStart(player)) / 1000 / 60;

            if (onlineTime >= minutes) {
                double bonus = plugin.getConfig().getDouble("salary.online_bonus.multiplier", 1.1);
                multiplier *= bonus;
            }
        }

        // Rank multiplier
        if (plugin.getConfig().getBoolean("salary.rank_bonus.enabled", true)) {
            double rankBonus = plugin.getConfig().getDouble("salary.rank_bonus." + rank, 1.0);
            multiplier *= rankBonus;
        }

        // Wanted level penalty
        int wantedLevel = getPlayerWantedLevel(player);
        if (wantedLevel > 0 && plugin.getConfig().getBoolean("salary.wanted_penalty.enabled", true)) {
            double penalty = plugin.getConfig().getDouble("salary.wanted_penalty.multiplier", 0.5);
            multiplier *= penalty;
        }

        return baseSalary * multiplier;
    }

    /**
     * Pay player using Vault economy integration
     * پرداخت به بازیکن
     */
    public void payPlayer(Player player, double amount) {
        // This would integrate with Vault economy plugin
        // For now, we're setting up the infrastructure

        // TODO: Integrate with Vault EconomyAPI
        // if (VaultIntegration.isEnabled()) {
        // VaultIntegration.depositMoney(player, amount);
        // }

        // Log the payment
        plugin.getLogManager().logSalaryPayment(player, amount);
        // Record salary earned in stats
        if (plugin.getStatsManager() != null) {
            plugin.getStatsManager().addSalary(player, amount);
        }
    }

    /**
     * Check if player is currently in jail
     * بررسی اینکه آیا بازیکن در زندان است
     */
    private boolean isPlayerInJail(Player player) {
        // Check with JailManager if player is jailed
        // For now, return false (assumes no jail system integration yet)
        return false;
    }

    /**
     * Get player's wanted level
     * دریافت سطح وانتد بازیکن
     */
    private int getPlayerWantedLevel(Player player) {
        return plugin.getWantedManager().getWantedLevel(player);
    }

    /**
     * Get when player came online
     * زمانی که بازیکن آنلاین شد
     */
    private long getPlayerOnlineStart(Player player) {
        // This would track player login time
        // For now, use current time (would need to track separately)
        return System.currentTimeMillis();
    }

    /**
     * Format currency for display
     * فرمت‌کردن پول برای نمایش
     */
    public String formatCurrency(double amount) {
        String symbol = plugin.getConfig().getString("salary.currency_symbol", "$");
        return String.format("%s%.2f", symbol, amount);
    }

    /**
     * Get total salary earned by player
     * دریافت کل حقوق دریافت‌شده توسط بازیکن
     */
    public double getPlayerTotalSalary(Player player) {
        // This would be stored in a persistent location
        // For now, return 0 (would need implementation)
        return 0.0;
    }

    /**
     * Get next payment time for player
     * زمان پرداخت بعدی برای بازیکن
     */
    public long getNextPaymentTime(Player player) {
        Long lastPayment = lastPaymentTime.getOrDefault(player.getUniqueId(), 0L);
        long nextPayment = lastPayment + paymentInterval * 50; // Convert ticks to ms
        return Math.max(0, nextPayment - System.currentTimeMillis());
    }

    /**
     * Get formatted time until next payment
     * زمان فرمت‌شده تا پرداخت بعدی
     */
    public String getFormattedTimeUntilPayment(Player player) {
        long ms = getNextPaymentTime(player);
        long minutes = ms / 1000 / 60;
        long seconds = (ms / 1000) % 60;

        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }

    /**
     * Reload salary configuration
     * بارگذاری مجدد تنظیمات حقوق
     */
    public void reload() {
        this.enabled = plugin.getConfig().getBoolean("salary.enabled", true);
        int minutes = plugin.getConfig().getInt("salary.payment_interval_minutes", 60);
        this.paymentInterval = (long) minutes * 60 * 20;
        this.baseMultiplier = plugin.getConfig().getDouble("salary.base_multiplier", 1.0);

        if (enabled) {
            startSalaryTask();
        } else {
            if (salaryTask != null) {
                salaryTask.cancel();
                salaryTask = null;
            }
        }
    }

    /**
     * Shutdown the salary system
     * خاموشی سیستم حقوق
     */
    public void shutdown() {
        if (salaryTask != null) {
            salaryTask.cancel();
        }
    }

    /**
     * Check if salary system is enabled
     * بررسی فعال بودن سیستم حقوق
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Set whether salary system is enabled
     * فعال/غیرفعال کردن سیستم حقوق
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            startSalaryTask();
        } else {
            if (salaryTask != null) {
                salaryTask.cancel();
                salaryTask = null;
            }
        }
    }
}
