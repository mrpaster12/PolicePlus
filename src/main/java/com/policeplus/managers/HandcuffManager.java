package com.policeplus.managers;

import com.policeplus.PolicePlus;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.inventory.ItemStack;
import java.util.Set;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class HandcuffManager {

    private final PolicePlus plugin;
    private final Map<UUID, UUID> cuffedPlayers; // cuffed -> cuffer
    private final Map<UUID, Long> cuffTimes; // cuffed -> startedAt
    private final Map<UUID, BossBar> copBossBars; // cop UUID -> BossBar showing suspect info

    private Material handcuffItem;
    private String handcuffName;
    private String handcuffLore;
    private String handcuffNameColor;
    private boolean handcuffNameBold;
    private String handcuffLoreColor;
    private int maxCuffSeconds;
    private final NamespacedKey cuffKey;
    private boolean acceptPlainItem;
    private static final String CUFF_TEAM_NAME = "police_cuffed_nocollide";

    // Elastic drag system constants
    private static final double LEASH_RADIUS = 5.0D;       // Suspect cannot walk beyond this from cop
    private static final double VELOCITY_DISTANCE = 4.5D;   // Start sliding suspect toward cop
    private static final double TELEPORT_DISTANCE = 8.0D;   // Emergency teleport threshold
    private static final double BEHIND_OFFSET = 1.5D;       // Teleport behind cop offset
    private static final double VELOCITY_STRENGTH = 0.35D;  // Sliding velocity multiplier
    private static final int SLOWNESS_AMPLIFIER = 4;        // Slowness effect strength
    private static final int SLOWNESS_DURATION_TICKS = Integer.MAX_VALUE / 4;

    // Flag to suppress fall/wall damage for cuffed suspects during drag teleport
    private final Set<UUID> suppressDamage = ConcurrentHashMap.newKeySet();

    // Track suspects who disconnected while cuffed (combat-logging protection)
    // Maps suspect UUID -> cop UUID at time of disconnect
    private final Map<UUID, UUID> combatLoggers = new ConcurrentHashMap<>();
    // Track when each combat-logger was recorded (for automatic expiry)
    private final Map<UUID, Long> combatLoggerTimestamps = new ConcurrentHashMap<>();
    // Maximum age for combat-logger entries (24 hours in milliseconds)
    private static final long COMBAT_LOGGER_MAX_AGE_MS = 24L * 60 * 60 * 1000;

    public HandcuffManager(PolicePlus plugin) {
        this.plugin = plugin;
        this.cuffedPlayers = new ConcurrentHashMap<>();
        this.cuffTimes = new ConcurrentHashMap<>();
        this.copBossBars = new ConcurrentHashMap<>();
        this.cuffKey = new NamespacedKey(plugin, "handcuff_item");
    }

    /**
     * No-op — dragging is now handled by the 2-tick elastic drag task.
     * Kept for backward compatibility with PolicePlus lifecycle calls.
     */
    public void start() {
        // No-op — elastic drag task is started in PolicePlus
    }

    public void stop() {
        suppressDamage.clear();
    }

    public void loadConfig() {
        // No need to save config.yml here — ConfigManager.fillMissingKeys() handles
        // inserting missing defaults without stripping comments.
        // plugin.saveConfig() was removed to prevent stripping bilingual comments.
        // Lock material to BLAZE_ROD regardless of config
        this.handcuffItem = Material.BLAZE_ROD;
        // Use ConfigManager cached values where possible
        this.handcuffName = plugin.getConfigManager().getHandcuffName();
        this.handcuffLore = plugin.getConfigManager().getHandcuffLore();
        this.handcuffNameColor = plugin.getConfigManager().getHandcuffNameColor();
        this.handcuffNameBold = plugin.getConfigManager().isHandcuffNameBold();
        this.handcuffLoreColor = plugin.getConfigManager().getHandcuffLoreColor();
        this.maxCuffSeconds = plugin.getConfigManager().getHandcuffMaxTime();
        this.acceptPlainItem = plugin.getConfigManager().isHandcuffAcceptPlainItem();
    }

    public void checkTimeouts() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = cuffTimes.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> e = it.next();
            if ((now - e.getValue()) / 1000L > maxCuffSeconds) {
                Player p = plugin.getServer().getPlayer(e.getKey());
                if (p != null) {
                    uncuffPlayer(p);
                    p.sendMessage(plugin.getLanguageManager().getMessage("handcuff.timeout"));
                }
                cuffedPlayers.remove(e.getKey());
                it.remove();
            }
        }
    }

    public boolean isCuffed(Player player) {
        return cuffedPlayers.containsKey(player.getUniqueId());
    }

    public Player getCuffer(Player cuffed) {
        UUID id = cuffedPlayers.get(cuffed.getUniqueId());
        return id == null ? null : plugin.getServer().getPlayer(id);
    }

    public boolean cuffPlayer(Player cuffer, Player target) {
        if (isCuffed(target))
            return false;
        cuffedPlayers.put(target.getUniqueId(), cuffer.getUniqueId());
        cuffTimes.put(target.getUniqueId(), System.currentTimeMillis());

        // Record handcuff in stats
        if (plugin.getStatsManager() != null) {
            plugin.getStatsManager().recordHandcuff(cuffer);
        }

        // Apply strong slowness so suspect walks very slowly (but can still walk)
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, SLOWNESS_DURATION_TICKS,
                SLOWNESS_AMPLIFIER, false, false, false));
        // Prevent sprinting
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, SLOWNESS_DURATION_TICKS,
                255, false, false, false));
        // Conditionally apply blindness based on config
        if (plugin.getConfigManager().isHandcuffApplyBlindness()) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, SLOWNESS_DURATION_TICKS,
                    255, false, false, false));
        } else {
            target.removePotionEffect(PotionEffectType.BLINDNESS);
        }

        // Mark as arrested in wanted system
        try {
            if (!plugin.getWantedManager().isArrested(target)) {
                plugin.getWantedManager().arrestPlayer(target, cuffer);
            }
        } catch (Throwable ignored) {
        }

        // Disable collision between suspect and cop so suspect doesn't block the cop
        setNoCollision(target, true);

        // Show BossBar to the cop
        showCopBossBar(cuffer, target);

        return true;
    }

    // ========================================================================
    //  Elastic Drag System — 2-tick task with velocity + teleport fallback
    // ========================================================================

    /**
     * Called every 2 ticks by a scheduled task in PolicePlus.
     * For every active handcuff pair:
     *   - Distance < 4.5: do nothing (suspect is close enough)
     *   - Distance 4.5–8.0: smooth velocity pull toward cop
     *   - Distance > 8.0: emergency teleport behind cop
     */
    public void handleElasticDrag() {
        for (Map.Entry<UUID, UUID> entry : cuffedPlayers.entrySet()) {
            UUID suspectUUID = entry.getKey();
            UUID copUUID = entry.getValue();

            Player suspect = plugin.getServer().getPlayer(suspectUUID);
            Player cop = plugin.getServer().getPlayer(copUUID);
            if (suspect == null || !suspect.isOnline() || suspect.isDead()) continue;
            if (cop == null || !cop.isOnline() || cop.isDead()) continue;

            // Must be in the same world
            if (!suspect.getWorld().equals(cop.getWorld())) {
                emergencyTeleport(suspect, cop);
                continue;
            }

            double distance = suspect.getLocation().distance(cop.getLocation());

            if (distance > TELEPORT_DISTANCE) {
                // Emergency teleport — cop ran/jumped/teleported away
                emergencyTeleport(suspect, cop);
            } else if (distance > VELOCITY_DISTANCE) {
                // Smooth sliding pull — gentle velocity toward cop
                Vector direction = cop.getLocation().toVector()
                        .subtract(suspect.getLocation().toVector());
                // Flatten to horizontal-only; we control the Y component ourselves below.
                direction.setY(0);
                if (direction.lengthSquared() <= 1.0E-4) {
                    // Suspect is basically stacked on the cop already — nothing to pull.
                    continue;
                }
                direction.normalize();

                Vector pull = direction.multiply(VELOCITY_STRENGTH);
                // FIX: a grounded real player's client largely cancels a pure-horizontal
                // server-side velocity within a tick or two (ground-friction correction),
                // which is why the suspect never visibly moved and the distance kept
                // growing until it always hit the TELEPORT_DISTANCE branch above.
                // A small upward nudge briefly lifts the suspect off the ground so the
                // horizontal pull actually registers client-side.
                if (suspect.isOnGround()) {
                    pull.setY(0.20D);
                } else {
                    pull.setY(suspect.getVelocity().getY()); // don't fight existing fall/jump motion
                }

                suppressDamage.add(suspectUUID);
                suspect.setVelocity(pull);
                // Clear suppress damage after a short delay
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    suppressDamage.remove(suspectUUID);
                }, 4L);
            }
            // distance <= VELOCITY_DISTANCE: do nothing, suspect is close enough
        }
    }

    /**
     * Emergency teleport: moves the suspect 1.5 blocks behind the cop,
     * preserving the suspect's original yaw/pitch to prevent camera shake.
     */
    private void emergencyTeleport(Player suspect, Player cop) {
        Location target = cop.getLocation().clone().add(
                cop.getLocation().getDirection().multiply(-BEHIND_OFFSET));
        target.setY(cop.getLocation().getY());

        // Passability check — fallback to cop location if blocked
        if (!isPassableForPlayer(target)) {
            target = cop.getLocation().clone();
        }

        // Preserve suspect's head rotation to prevent camera jitter
        target.setYaw(suspect.getLocation().getYaw());
        target.setPitch(suspect.getLocation().getPitch());

        suppressDamage.add(suspect.getUniqueId());
        suspect.teleport(target);
        // Clear suppress damage after a short delay
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            suppressDamage.remove(suspect.getUniqueId());
        }, 20L);
    }

    /**
     * Returns the leash radius — suspect cannot walk beyond this distance from the cop.
     */
    public double getLeashRadius() {
        return LEASH_RADIUS;
    }

    // ========================================================================
    //  End of Elastic Drag System
    // ========================================================================

    /**
     * Checks if damage should be suppressed for a cuffed suspect (fall/wall damage during drag).
     * Call this from HandcuffListener's damage handler.
     */
    public boolean shouldSuppressDamage(Player player) {
        return suppressDamage.contains(player.getUniqueId()) && isCuffed(player);
    }

    /**
     * Returns the custom handcuff item to the cop who cuffed the suspect.
     * If the cop's inventory is full, drops the item at the cop's location.
     * Should be called when a suspect is jailed, dies, or disconnects while cuffed.
     *
     * @param suspectUUID the UUID of the cuffed suspect
     */
    public void returnHandcuffToCop(UUID suspectUUID) {
        UUID copUUID = cuffedPlayers.get(suspectUUID);
        if (copUUID == null) return;

        Player cop = plugin.getServer().getPlayer(copUUID);
        if (cop != null && cop.isOnline()) {
            ItemStack handcuffItem = createHandcuffItem();
            java.util.HashMap<Integer, ItemStack> leftover = cop.getInventory().addItem(handcuffItem);
            if (!leftover.isEmpty()) {
                cop.getWorld().dropItemNaturally(cop.getLocation(), handcuffItem);
                cop.sendMessage(plugin.getLanguageManager().getPrefix() +
                        plugin.getLanguageManager().getMessage("handcuff.returned_dropped"));
            } else {
                cop.sendMessage(plugin.getLanguageManager().getPrefix() +
                        plugin.getLanguageManager().getMessage("handcuff.returned_inventory"));
            }
        }
    }

    public boolean uncuffPlayer(Player target) {
        if (!isCuffed(target))
            return false;

        // Get cuffer UUID before removing from map
        UUID cufferUUID = cuffedPlayers.get(target.getUniqueId());

        cuffedPlayers.remove(target.getUniqueId());
        cuffTimes.remove(target.getUniqueId());
        suppressDamage.remove(target.getUniqueId());

        // Remove slowness and other immobilizing effects
        target.removePotionEffect(PotionEffectType.SLOW);
        target.removePotionEffect(PotionEffectType.SLOW_DIGGING);
        target.removePotionEffect(PotionEffectType.BLINDNESS);

        // Restore collision
        setNoCollision(target, false);

        // Remove cop BossBar
        if (cufferUUID != null) {
            Player cuffer = plugin.getServer().getPlayer(cufferUUID);
            if (cuffer != null) {
                removeCopBossBar(cuffer);
            }
        }
        try {
            if (plugin.getWantedManager().isArrested(target)) {
                plugin.getWantedManager().releaseArrested(target);
            }
        } catch (Throwable ignored) {
        }
        return true;
    }

    public boolean isHandcuffItem(ItemStack item) {
        if (item == null || item.getType() != handcuffItem)
            return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(cuffKey, PersistentDataType.BYTE))
            return true;
        if (acceptPlainItem && meta.hasDisplayName()) {
            String expected = colorize(
                    (handcuffNameColor != null ? handcuffNameColor : "") + (handcuffNameBold ? "&l" : "") + handcuffName);
            return expected.equals(meta.getDisplayName());
        }
        return false;
    }

    public ItemStack createHandcuffItem() {
        ItemStack item = new ItemStack(handcuffItem);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = colorize((handcuffNameColor != null ? handcuffNameColor : "") + (handcuffNameBold ? "&l" : "")
                    + handcuffName);
            meta.setDisplayName(name);
            if (handcuffLore != null && !handcuffLore.isEmpty()) {
                meta.setLore(Collections.singletonList(colorize(
                        (handcuffLoreColor != null ? handcuffLoreColor : "&7") + handcuffLore)));
            }
            meta.getPersistentDataContainer().set(cuffKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Shows a BossBar to the cop tracking their handcuffed suspect.
     */
    public void showCopBossBar(Player cop, Player suspect) {
        removeCopBossBar(cop);
        String title = "🚔 " + suspect.getName();
        BossBar bossBar = Bukkit.createBossBar(title, BarColor.RED, BarStyle.SOLID);
        bossBar.addPlayer(cop);
        bossBar.setVisible(true);
        copBossBars.put(cop.getUniqueId(), bossBar);
        updateCopBossBar(cop, suspect);
    }

    public void updateCopBossBar(Player cop, Player suspect) {
        BossBar bossBar = copBossBars.get(cop.getUniqueId());
        if (bossBar == null) return;
        String title = "🚔 Suspect: " + suspect.getName();
        bossBar.setTitle(title);
    }

    public void removeCopBossBar(Player cop) {
        BossBar bossBar = copBossBars.remove(cop.getUniqueId());
        if (bossBar != null) {
            bossBar.setVisible(false);
            bossBar.removeAll();
        }
    }

    public UUID getSuspectForCop(Player cop) {
        for (Map.Entry<UUID, UUID> entry : cuffedPlayers.entrySet()) {
            if (entry.getValue().equals(cop.getUniqueId())) {
                return entry.getKey();
            }
        }
        return null;
    }

    public BossBar getCopBossBar(UUID copUUID) {
        return copBossBars.get(copUUID);
    }

    private String colorize(String str) {
        return str == null ? null : str.replace('&', '§');
    }

    private boolean isPassableForPlayer(Location location) {
        return location.getBlock().isPassable() && location.clone().add(0, 1, 0).getBlock().isPassable();
    }

    public void reloadConfig() {
        loadConfig();
    }

    /**
     * Marks a suspect as a combat-logger (disconnected while cuffed).
     */
    public void markCombatLogger(UUID suspectUUID, UUID copUUID) {
        combatLoggers.put(suspectUUID, copUUID);
        combatLoggerTimestamps.put(suspectUUID, System.currentTimeMillis());
    }

    public boolean isCombatLogger(UUID suspectUUID) {
        return combatLoggers.containsKey(suspectUUID);
    }

    public UUID getCombatLoggerCop(UUID suspectUUID) {
        return combatLoggers.get(suspectUUID);
    }

    /**
     * Handles a combat-logger rejoining the server.
     * Returns true if the player was handled as a combat-logger (jailed).
     */
    public boolean handleCombatLoggerRejoin(Player player) {
        UUID playerUUID = player.getUniqueId();
        if (!combatLoggers.containsKey(playerUUID)) {
            return false;
        }

        UUID copUUID = combatLoggers.remove(playerUUID);
        combatLoggerTimestamps.remove(playerUUID);
        Player cop = copUUID != null ? plugin.getServer().getPlayer(copUUID) : null;

        String jailName = plugin.getJailManager().getLeastPopulatedJailName();
        if (jailName != null) {
            plugin.getJailManager().jailPlayerByWantedLevel(player, jailName);

            String copName = cop != null ? cop.getName() : "an officer";
            player.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("combat_logged_jailed", "player", copName));

            if (cop != null && cop.isOnline()) {
                cop.sendMessage(plugin.getLanguageManager().getPrefix() +
                        plugin.getLanguageManager().getMessage("combat_logged_cop_notify", "player", player.getName()));
            }
        }

        return true;
    }

    private void setNoCollision(Player player, boolean noCollision) {
        try {
            Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
            Team team = sb.getTeam(CUFF_TEAM_NAME);
            if (noCollision) {
                if (team == null) {
                    team = sb.registerNewTeam(CUFF_TEAM_NAME);
                    team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
                }
                if (!team.hasEntry(player.getName())) {
                    team.addEntry(player.getName());
                }
            } else {
                if (team != null && team.hasEntry(player.getName())) {
                    team.removeEntry(player.getName());
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public void cleanupExpiredCombatLoggers() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = combatLoggerTimestamps.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            if (now - entry.getValue() > COMBAT_LOGGER_MAX_AGE_MS) {
                combatLoggers.remove(entry.getKey());
                it.remove();
            }
        }
    }

    public void cleanupCombatLoggers() {
        combatLoggers.clear();
        combatLoggerTimestamps.clear();
    }
}