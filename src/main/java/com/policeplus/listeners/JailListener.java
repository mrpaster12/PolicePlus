package com.policeplus.listeners;

import com.policeplus.PolicePlus;
import com.policeplus.managers.JailManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

public class JailListener implements Listener {
    
    private final PolicePlus plugin;
    
    // Cached allowed block materials for BLOCKS-type jail mining (avoids re-parsing on every break event)
    private Set<Material> cachedAllowedMineMaterials;
    
    public JailListener(PolicePlus plugin) {
        this.plugin = plugin;
        refreshAllowedMineMaterials();
    }
    
    /**
     * Refreshes the cached allowed mine materials. Call on config reload.
     */
    public void refreshAllowedMineMaterials() {
        cachedAllowedMineMaterials = plugin.getConfigManager().getAllowedBlocks().stream()
                .map(name -> {
                    try { return Material.valueOf(name.toUpperCase()); }
                    catch (IllegalArgumentException e) { return null; }
                })
                .filter(m -> m != null)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Material.class)));
    }
    
    // Cache allowed block materials for BLOCKS-type jail mining
    private Set<Material> getAllowedMineMaterials() {
        return cachedAllowedMineMaterials != null ? cachedAllowedMineMaterials : EnumSet.noneOf(Material.class);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        org.bukkit.block.Block block = event.getBlock();

        // === Mine-Jail Cuboid Protection ===
        // Check if the broken block is inside any mine-jail cuboid region.
        // If so, protect it from non-jailed players (only jailed players may mine here).
        // OPs in Creative mode are allowed to break blocks for editing purposes.
        JailManager.JailInfo mineJail = plugin.getJailManager().getMineJailForLocation(block.getLocation());
        if (mineJail != null) {
            boolean isCreativeBypass = player.isOp() && player.getGameMode() == org.bukkit.GameMode.CREATIVE;
            if (!isCreativeBypass) {
                // Check if this player is the jailed player assigned to THIS jail
                String playerJailName = plugin.getJailManager().getJailName(player);
                boolean isAssignedJailedPlayer = plugin.getJailManager().isJailed(player)
                        && playerJailName != null && playerJailName.equals(mineJail.getName());

                if (!isAssignedJailedPlayer) {
                    // Normal player or jailed player from a DIFFERENT jail — deny
                    event.setCancelled(true);
                    player.sendMessage(plugin.getLanguageManager().getPrefix() +
                            plugin.getLanguageManager().getMessage("jail_mine_protected"));
                    return;
                }

                // Player IS the assigned jailed player in this mine-jail
                if (!plugin.getJailManager().isBlocksJail(player)) {
                    event.setCancelled(true);
                    return;
                }

                Set<Material> allowed = getAllowedMineMaterials();
                if (allowed.contains(block.getType())) {
                    // Force-uncancel to override WorldGuard or any other protection plugin
                    event.setCancelled(false);
                    event.setDropItems(false);
                    event.setExpToDrop(0);

                    // Capture block location for auto-regen (use cloned Location to be safe)
                    final org.bukkit.Location blockLoc = block.getLocation().clone();

                    // Check remaining BEFORE mining to catch the last block
                    int remainingBefore = plugin.getJailManager().getRemainingBlocks(player);

                    if (remainingBefore <= 1) {
                        // This is the last (or final) block — count it and release INSTANTLY
                        plugin.getJailManager().mineBlock(player);
                        // Schedule auto-regen for the last block too
                        plugin.getJailManager().scheduleBlockRegen(blockLoc);
                        plugin.getJailManager().releasePlayer(player);
                        return;
                    }

                    // Not the last block — count and show progress
                    plugin.getJailManager().mineBlock(player);

                    int remaining = plugin.getJailManager().getRemainingBlocks(player);
                    int required = plugin.getJailManager().getRequiredBlocks(player);
                    String subtitle = plugin.getLanguageManager().getMessage("jail_actionbar_blocks",
                            "remaining", String.valueOf(remaining),
                            "required", String.valueOf(required));
                    player.sendTitle("§e⛏", subtitle, 5, 30, 5);

                    // Auto-regen: queue block for regeneration after 3 seconds
                    plugin.getJailManager().scheduleBlockRegen(blockLoc);
                    return;
                }

                // Non-allowed block inside mine-jail — deny
                event.setCancelled(true);
                player.sendMessage(plugin.getLanguageManager().getPrefix() +
                        plugin.getLanguageManager().getMessage("jail_block_break_denied"));
                return;
            }
            // Creative mode OP — allow breaking (for editing the mine)
            return;
        }

        // === Jail Restriction (non-mine-jail) ===
        if (!plugin.getJailManager().isJailed(player)) return;
        if (player.hasPermission("policeplus.bypass")) return;

        // BLOCKS-type jail (without cuboid) — deny (shouldn't normally happen)
        if (plugin.getJailManager().isBlocksJail(player)) {
            event.setCancelled(true);
            player.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("jail_block_break_denied"));
            return;
        }

        // TIME-type jail: deny all block breaking
        event.setCancelled(true);
        player.sendMessage(plugin.getLanguageManager().getPrefix() +
                plugin.getLanguageManager().getMessage("jail_block_break_denied"));
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        org.bukkit.block.Block block = event.getBlock();

        // 1. If player is actually jailed, block them from placing blocks anywhere
        if (plugin.getJailManager().isJailed(player)) {
            if (!player.hasPermission("policeplus.bypass")) {
                event.setCancelled(true);
                player.sendMessage(plugin.getLanguageManager().getPrefix() +
                        plugin.getLanguageManager().getMessage("jail_block_place_denied"));
            }
            return;
        }

        // 2. If player is NOT jailed, only block them if they try to place inside a Mine-Jail region
        if (plugin.getJailManager().isInsideAnyMineRegion(block.getLocation())) {
            // Allow OPs in Creative mode to build/edit the mine
            if (player.isOp() && player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
                return;
            }
            event.setCancelled(true);
            player.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("jail_mine_protected"));
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // If the player is NOT jailed, immediately return — do nothing!
        if (!plugin.getJailManager().isJailed(player)) {
            return;
        }
        if (player.hasPermission("policeplus.bypass")) return;

        // Allow movement within jail boundaries — check if destination exceeds radius
        Location to = event.getTo();
        if (to == null) return;

        String jailName = plugin.getJailManager().getJailName(player);
        if (jailName == null) return;

        JailManager.JailInfo jail = plugin.getJailManager().getJail(jailName);
        if (jail == null) return;

        Location jailCenter = jail.getLocation();
        if (jailCenter == null || jailCenter.getWorld() == null) return;

        // Must be in the same world
        if (!to.getWorld().equals(jailCenter.getWorld())) {
            event.setCancelled(true);
            return;
        }

        // Calculate allowed radius: use jail's radius or config default
        int radius = plugin.getConfigManager().getDefaultJailRadius();
        if (radius <= 0) radius = jail.getRadius();

        double distance = to.distance(jailCenter);
        if (distance > radius) {
            // Teleport back to jail center
            player.teleport(jailCenter);
            player.sendTitle(
                    plugin.getLanguageManager().getMessage("jail_cannot_escape_title").replace('&', '§'),
                    plugin.getLanguageManager().getMessage("jail_cannot_escape_subtitle").replace('&', '§'),
                    5, 40, 10);
            player.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("jail_area_leave_denied"));
            event.setCancelled(true);
        }
    }

    /**
     * Prevent jailed players from dropping items (anti-exploit).
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (plugin.getJailManager().isJailed(player) && !player.hasPermission("policeplus.bypass")) {
            event.setCancelled(true);
            player.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("jail_cannot_drop"));
        }
    }

    /**
     * Remove jail pickaxe from death drops if a jailed player dies.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (plugin.getJailManager().isJailed(player)) {
            // Remove jail pickaxe from drops to prevent smuggling
            event.getDrops().removeIf(item ->
                    item != null && item.getType() == org.bukkit.Material.STONE_PICKAXE
                            && item.getItemMeta() != null && item.getItemMeta().isUnbreakable());
        }
    }

    /**
     * Re-give jail pickaxe on respawn if the player is still jailed in a BLOCKS jail.
     * Uses a 1-tick delayed task to ensure inventory is ready after respawn.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (plugin.getJailManager().isJailed(player) && plugin.getJailManager().isBlocksJail(player)) {
                plugin.getJailManager().giveJailPickaxeToPlayer(player);
                player.sendMessage(plugin.getLanguageManager().getPrefix() +
                        plugin.getLanguageManager().getMessage("jail_pickaxe_returned"));
            }
        }, 1L);
    }

    /**
     * Make jailed players completely invulnerable to ALL damage types.
     * This includes PvP, mobs, fall, suffocation, fire, lava, drowning,
     * hunger, void, and any other damage source. Ensures players can
     * safely serve their sentence without dying.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onJailedPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (plugin.getJailManager().isJailed(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        // cuffs removed
    }
}
