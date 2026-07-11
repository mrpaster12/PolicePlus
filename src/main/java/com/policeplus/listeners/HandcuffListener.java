package com.policeplus.listeners;

import com.policeplus.PolicePlus;
import com.policeplus.managers.HandcuffManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class HandcuffListener implements Listener {

    private final PolicePlus plugin;
    private final HandcuffManager manager;

    /**
     * Debounce / cooldown map: tracks the last warning-message timestamp per cop.
     * Prevents duplicate warning messages from being sent within 500ms.
     * STATIC so it is shared across all listener instances (HandcuffListener + PlayerListener).
     */
    private static final Map<UUID, Long> warningCooldown = new HashMap<>();

    /**
     * Checks whether a warning message for this player is on cooldown (within 500ms).
     * If not on cooldown, records the current timestamp and returns false (proceed).
     * If on cooldown, returns true (caller should skip sending the message).
     *
     * @param playerUUID the UUID of the player sending the event
     * @return true if the message should be suppressed (duplicate), false if it should proceed
     */
    public static boolean isOnCooldown(UUID playerUUID) {
        long now = System.currentTimeMillis();
        long lastMessage = warningCooldown.getOrDefault(playerUUID, 0L);
        if (now - lastMessage < 500) {
            return true; // suppress duplicate
        }
        warningCooldown.put(playerUUID, now);
        return false; // proceed
    }

    public HandcuffListener(PolicePlus plugin) {
        this.plugin = plugin;
        this.manager = plugin.getHandcuffManager();
    }

    // Do not ignore cancelled to ensure cuffs still work if other plugins cancel basic interacts
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onUseCuff(PlayerInteractEvent event) {
        // Strict HAND check — only process main-hand interactions
        if (event.getHand() != EquipmentSlot.HAND) return;

        Action a = event.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        Player p = event.getPlayer();

        ItemStack it = p.getInventory().getItemInMainHand();
        // FIX: check the item / permission FIRST. Previously the 500ms debounce
        // check below ran on every single right-click (placing blocks, opening
        // chests, eating, etc.), and would randomly cancel the interact event
        // — and therefore the resulting block placement — for ANY player who
        // right-clicked twice within 500ms, regardless of whether they were
        // even holding a handcuff item. Now we only reach the debounce check
        // once we know this is actually a handcuff-use attempt.
        if (it == null || !manager.isHandcuffItem(it)) return;
        if (!com.policeplus.utils.PermissionUtils.hasPolicePermission(p, "policeplus.handcuff.cuff")) return;

        // --- Debounce Shield: block duplicate handcuff-use events within 500ms ---
        if (isOnCooldown(p.getUniqueId())) {
            event.setCancelled(true);
            return; // Ignore this duplicate click/event completely
        }

        Player target = findNearest(p, 3.0);
        if (target == null || target.equals(p)) {
            p.sendMessage(plugin.getLanguageManager().getMessage("handcuff.no_player_nearby"));
            event.setCancelled(true);
            return;
        }
        if (manager.isCuffed(target)) {
            p.sendMessage(plugin.getLanguageManager().getMessage("handcuff.already_cuffed"));
            event.setCancelled(true);
            return;
        }
        // Only allow cuffing players who have a wanted level > 0
        if (!plugin.getWantedManager().isWanted(target) || plugin.getWantedManager().getWantedLevel(target) <= 0) {
            p.sendMessage(plugin.getLanguageManager().getPrefix() +
                plugin.getLanguageManager().getMessage("handcuff.target_not_wanted"));
            event.setCancelled(true);
            return;
        }
        // Distance guard (<= 3 blocks)
        if (p.getLocation().distance(target.getLocation()) > 3.0D) {
            p.sendMessage(plugin.getLanguageManager().getMessage("handcuff.too_far"));
            event.setCancelled(true);
            return;
        }

        if (manager.cuffPlayer(p, target)) {
            p.sendMessage(plugin.getLanguageManager().getMessage("handcuff.cuffed", "player", target.getName()));
            target.sendMessage(plugin.getLanguageManager().getMessage("handcuff.cuffed_by", "player", p.getName()));
            if (it.getAmount() > 1) {
                it.setAmount(it.getAmount() - 1);
            } else {
                p.getInventory().setItemInMainHand(null);
            }
        }
        event.setCancelled(true);
    }

    /**
     * Elastic leash: prevent handcuffed suspects from walking beyond 5 blocks from the cop.
     * Suspects can walk slowly within the radius but cannot escape.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!manager.isCuffed(player)) return;

        org.bukkit.Location from = event.getFrom();
        org.bukkit.Location to = event.getTo();
        if (to == null) return;

        // Only check on horizontal movement (not head rotation)
        if (from.getX() == to.getX() && from.getZ() == to.getZ()) return;

        // Get the cop who cuffed this suspect
        Player cop = manager.getCuffer(player);
        if (cop == null || !cop.isOnline()) return;

        // Must be in the same world
        if (!player.getWorld().equals(cop.getWorld())) {
            event.setTo(from);
            return;
        }

        // Check if the *destination* exceeds the 5-block leash radius
        double distanceToCop = to.distance(cop.getLocation());
        if (distanceToCop > manager.getLeashRadius()) {
            // Block movement — reset to 'from' but preserve head rotation
            org.bukkit.Location back = from.clone();
            back.setYaw(to.getYaw());
            back.setPitch(to.getPitch());
            event.setTo(back);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) { if (manager.isCuffed(e.getPlayer())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) { if (manager.isCuffed(e.getPlayer())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) { if (manager.isCuffed(e.getPlayer())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player && manager.isCuffed((Player) e.getEntity())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) { if (manager.isCuffed(e.getPlayer())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInvClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player && manager.isCuffed((Player) e.getWhoClicked())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        // Prevent cuffed players from dealing damage
        if (e.getDamager() instanceof Player && manager.isCuffed((Player) e.getDamager())) {
            e.setCancelled(true);
        }
        // Prevent damage TO cuffed players (from other players only — environmental handled separately)
        if (e.getEntity() instanceof Player && manager.isCuffed((Player) e.getEntity())) {
            e.setCancelled(true);
        }
    }

    /**
     * Make handcuffed suspects completely invulnerable to ALL damage types.
     * This includes fall, suffocation, PvP, mobs, fire, lava, drowning,
     * void, starvation, and any other damage source. Guarantees suspects
     * don't die during drag teleportation or while serving handcuff time.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHandcuffedPlayerDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player player = (Player) e.getEntity();
        if (manager.isCuffed(player)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnderPearl(PlayerInteractEvent e) {
        // Strict HAND check — only process main-hand interactions
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (!manager.isCuffed(e.getPlayer())) return;
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = e.getItem();
        if (item != null && item.getType() == Material.ENDER_PEARL) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(plugin.getLanguageManager().getMessage("handcuff_ender_pearl_blocked"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent e) {
        if (e.getEntered() instanceof Player && manager.isCuffed((Player) e.getEntered())) {
            e.setCancelled(true);
        }
    }

    // Allowed commands for handcuffed players (basic communication)
    private static final List<String> ALLOWED_CUFFED_COMMANDS = Arrays.asList(
            "msg", "r", "reply", "tell", "w", "whisper", "help", "rules"
    );

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (!manager.isCuffed(e.getPlayer())) return;
        String msg = e.getMessage().toLowerCase();
        // Extract command name (e.g., "/msg" -> "msg")
        String cmd = msg.startsWith("/") ? msg.substring(1).split("\\s+")[0] : msg.split("\\s+")[0];
        if (!ALLOWED_CUFFED_COMMANDS.contains(cmd)) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(plugin.getLanguageManager().getMessage("handcuff_commands_blocked"));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (manager.isCuffed(e.getPlayer())) {
            // Get cuffer reference BEFORE clearing the cuffed state
            Player cuffer = manager.getCuffer(e.getPlayer());
            if (cuffer != null) {
                // Remove cop's BossBar tracking this suspect
                manager.removeCopBossBar(cuffer);
                // Mark as combat-logger so they get jailed on rejoin
                manager.markCombatLogger(e.getPlayer().getUniqueId(), cuffer.getUniqueId());
            }
            // Return handcuff item to the original cop who cuffed them
            manager.returnHandcuffToCop(e.getPlayer().getUniqueId());
            // Now clear the cuffed state
            manager.uncuffPlayer(e.getPlayer());
        }
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


