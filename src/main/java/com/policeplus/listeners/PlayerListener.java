package com.policeplus.listeners;

import com.policeplus.PolicePlus;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import java.util.UUID;

public class PlayerListener implements Listener {
    
    private final PolicePlus plugin;
    
    public PlayerListener(PolicePlus plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        
        if (killer != null && killer != victim) {
            plugin.getWantedManager().onPlayerKill(killer, victim);
        }

        // Bounty Hunter Payout: anyone who kills a player with an active bounty gets paid
        if (killer != null && killer != victim && plugin.getBountyManager().hasBounty(victim)) {
            double bountyAmount = plugin.getBountyManager().getBounty(victim);
            if (bountyAmount > 0) {
                try {
                    net.milkbowl.vault.economy.Economy economy = com.policeplus.PolicePlus.getEconomy();
                    if (economy != null) {
                        economy.depositPlayer(killer, bountyAmount);

                        // Private message to the killer only
                        killer.sendMessage(plugin.getLanguageManager().getPrefix() +
                                plugin.getLanguageManager().getMessage("bounty_claimed_killer",
                                        "player", victim.getName(),
                                        "amount", plugin.getBountyManager().formatCurrency(bountyAmount)));

                        // Private message to the victim only
                        victim.sendMessage(plugin.getLanguageManager().getPrefix() +
                                plugin.getLanguageManager().getMessage("bounty_claimed_victim",
                                        "player", killer.getName(),
                                        "amount", plugin.getBountyManager().formatCurrency(bountyAmount)));
                    }
                } catch (Throwable t) {
                    plugin.getLogger().warning("Could not process bounty hunter payout: " + t.getMessage());
                }
                // Silently remove bounty — no broadcast
                plugin.getBountyManager().removeBountySilently(victim);
            }
        }

        // Reset victim's wanted on death based on config
        if (plugin.getConfigManager().isResetWantedOnDeathEnabled() && plugin.getWantedManager().isWanted(victim)) {
            int threshold = plugin.getConfigManager().getMaxWantedLevelToResetOnDeath();
            int victimLevel = plugin.getWantedManager().getWantedLevel(victim);
            boolean shouldReset = threshold < 0 || victimLevel <= threshold;
            if (shouldReset) {
                plugin.getWantedManager().removeWanted(victim);
            }
        }

        // Edge-case: if cop dies while having a suspect handcuffed, return handcuff and uncuff the suspect
        UUID suspectUUID = plugin.getHandcuffManager().getSuspectForCop(victim);
        if (suspectUUID != null) {
            plugin.getHandcuffManager().returnHandcuffToCop(suspectUUID);
            Player suspect = plugin.getServer().getPlayer(suspectUUID);
            if (suspect != null && suspect.isOnline()) {
                plugin.getHandcuffManager().uncuffPlayer(suspect);
                suspect.sendMessage(plugin.getLanguageManager().getPrefix() +
                        plugin.getLanguageManager().getMessage("handcuff.released_cop_died", "player", suspect.getName()));
            }
        }

        // Edge-case: if suspect dies while cuffed, return handcuff to cop and uncuff
        if (plugin.getHandcuffManager().isCuffed(victim)) {
            plugin.getHandcuffManager().returnHandcuffToCop(victim.getUniqueId());
            plugin.getHandcuffManager().uncuffPlayer(victim);
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Check if player is a combat-logger (disconnected while cuffed)
        // Must be checked before jail check since combat-loggers get jailed
        if (plugin.getHandcuffManager().handleCombatLoggerRejoin(player)) {
            // Player was jailed for combat-logging, no need to continue
            plugin.getDisplayManager().initializeViewerScores(player);
            plugin.getDisplayManager().updatePlayerDisplay(player);
            return;
        }
        
        // Check if player is jailed
        if (plugin.getJailManager().isJailed(player)) {
            if (plugin.getJailManager().isBlocksJail(player)) {
                int remaining = plugin.getJailManager().getRemainingBlocks(player);
                String message = plugin.getLanguageManager().getMessage("jail_actionbar_blocks",
                        "remaining", String.valueOf(remaining),
                        "required", String.valueOf(plugin.getJailManager().getRequiredBlocks(player)));
                player.sendMessage(plugin.getLanguageManager().getPrefix() + message);
            } else {
                long remainingTime = plugin.getJailManager().getRemainingTime(player);
                int minutes = (int) (remainingTime / (1000 * 60));
                String message = plugin.getLanguageManager().getMessage("jail_time_remaining",
                        "time", String.valueOf(minutes));
                player.sendMessage(plugin.getLanguageManager().getPrefix() + message);
            }
        }

        // Initialize all below-name scores on viewer's board and update player's own display
        plugin.getDisplayManager().initializeViewerScores(player);
        plugin.getDisplayManager().updatePlayerDisplay(player);
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Cleanup pending jailing session (chat capture state) from GUI flow
        com.policeplus.gui.PoliceGUI.cancelFlow(player);

        // Cleanup compass and boss bar
        plugin.getCompassManager().cleanupPlayer(player);

        // Edge-case: if cop disconnects while having a suspect handcuffed, return handcuff and uncuff
        UUID suspectUUID = plugin.getHandcuffManager().getSuspectForCop(player);
        if (suspectUUID != null) {
            plugin.getHandcuffManager().returnHandcuffToCop(suspectUUID);
            Player suspect = plugin.getServer().getPlayer(suspectUUID);
            if (suspect != null && suspect.isOnline()) {
                plugin.getHandcuffManager().uncuffPlayer(suspect);
            }
        }

        // Edge-case: if cuffed suspect disconnects, return handcuff to cop, mark as combat-logger
        // so they get instantly jailed when they rejoin
        if (plugin.getHandcuffManager().isCuffed(player)) {
            Player cuffer = plugin.getHandcuffManager().getCuffer(player);
            if (cuffer != null) {
                plugin.getHandcuffManager().removeCopBossBar(cuffer);
                // Mark as combat-logger before uncuffing (preserves cop reference)
                plugin.getHandcuffManager().markCombatLogger(player.getUniqueId(), cuffer.getUniqueId());
            }
            // Return handcuff item to the cop before uncuffing
            plugin.getHandcuffManager().returnHandcuffToCop(player.getUniqueId());
            plugin.getHandcuffManager().uncuffPlayer(player);
        }

        // Cleanup any cop BossBars for this player
        plugin.getHandcuffManager().removeCopBossBar(player);

        // Cleanup display scoreboards for this player to prevent memory leaks
        plugin.getDisplayManager().cleanupPlayer(player);
    }

    /**
     * Prevent handcuffed suspects from changing worlds (Nether Portal etc).
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (plugin.getHandcuffManager().isCuffed(player)) {
            // Teleport back to cuffer if they exist in the old world
            Player cuffer = plugin.getHandcuffManager().getCuffer(player);
            if (cuffer != null && cuffer.isOnline()) {
                if (!player.getWorld().equals(cuffer.getWorld())) {
                    player.teleport(cuffer);
                }
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Strict HAND check — only process main-hand interactions
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();

        if (!plugin.getJailManager().isJailed(player)) return;
        if (player.hasPermission("policeplus.bypass")) return;

        // BLOCKS-type jail: allow pickaxe usage for mining
        if (plugin.getJailManager().isBlocksJail(player)) {
            ItemStack item = event.getItem();
            if (item != null && item.getType().name().contains("PICKAXE")) {
                return; // Allow pickaxe use for mining labor
            }
        }

        ItemStack item = event.getItem();

        if (item != null && (item.getType().name().contains("PICKAXE") ||
                            item.getType().name().contains("AXE") ||
                            item.getType().name().contains("SHOVEL") ||
                            item.getType().name().contains("HOE"))) {
            event.setCancelled(true);
            return;
        }

        // Cancel block breaking/placing
        if (event.getAction().name().contains("LEFT_CLICK") ||
            event.getAction().name().contains("RIGHT_CLICK")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player)) return;
        // Prevent double trigger (main hand only)
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player police = event.getPlayer();

        // --- Debounce Shield: block duplicate events within 500ms ---
        if (HandcuffListener.isOnCooldown(police.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        Player target = (Player) event.getRightClicked();

        // Use HandcuffManager to check if item is a handcuff item (consistent check via PDC)
        org.bukkit.inventory.ItemStack item = police.getInventory().getItemInMainHand();
        if (item == null || item.getType() == org.bukkit.Material.AIR) return;
        if (!plugin.getHandcuffManager().isHandcuffItem(item)) return;

        // Permission check: unified permission system
        if (!com.policeplus.utils.PermissionUtils.hasPolicePermission(police, "policeplus.handcuff.cuff")) {
            police.sendMessage(plugin.getLanguageManager().getPrefix() + plugin.getLanguageManager().getMessage("no_permission"));
            return;
        }

        // Range check same as arrest distance
        if (!plugin.getJailManager().canArrestPlayer(police, target)) {
            int distance = plugin.getConfigManager().getArrestDistance();
            police.sendMessage(plugin.getLanguageManager().getPrefix() + 
                plugin.getLanguageManager().getMessage("jail_too_far", "distance", String.valueOf(distance)));
            return;
        }

        // Require target to have at least 1 wanted level
        if (!plugin.getWantedManager().isWanted(target) || plugin.getWantedManager().getWantedLevel(target) <= 0) {
            police.sendMessage(plugin.getLanguageManager().getPrefix() +
                plugin.getLanguageManager().getMessage("handcuff.target_not_wanted"));
            return;
        }

        // Use HandcuffManager.cuffPlayer() for consistent state management
        if (plugin.getHandcuffManager().cuffPlayer(police, target)) {
            // Consume the handcuff item
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                police.getInventory().setItemInMainHand(null);
            }

            police.sendMessage(plugin.getLanguageManager().getPrefix() + plugin.getLanguageManager().getMessage("cuff_cuffed_notify_police", "player", target.getName()));
            target.sendMessage(plugin.getLanguageManager().getPrefix() + plugin.getLanguageManager().getMessage("cuff_cuffed_notify_target", "police", police.getName()));
        }
    }
}
