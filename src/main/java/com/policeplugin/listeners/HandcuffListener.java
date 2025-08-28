package com.policeplugin.listeners;

import com.policeplugin.PolicePlugin;
import com.policeplugin.managers.HandcuffManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class HandcuffListener implements Listener {

    private final PolicePlugin plugin;
    private final HandcuffManager manager;

    public HandcuffListener(PolicePlugin plugin) {
        this.plugin = plugin;
        this.manager = plugin.getHandcuffManager();
    }

    // Do not ignore cancelled to ensure cuffs still work if other plugins cancel basic interacts
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onUseCuff(PlayerInteractEvent event) {
        Action a = event.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        Player p = event.getPlayer();
        // Determine which hand triggered and read that item's stack
        EquipmentSlot hand = event.getHand();
        ItemStack it = (hand == EquipmentSlot.OFF_HAND)
                ? p.getInventory().getItemInOffHand()
                : p.getInventory().getItemInMainHand();
        if (!p.hasPermission("policeplugin.handcuff.cuff")) return;
        if (it == null || !manager.isHandcuffItem(it)) return;

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
                if (hand == EquipmentSlot.OFF_HAND) {
                    p.getInventory().setItemInOffHand(null);
                } else {
                    p.getInventory().setItemInMainHand(null);
                }
            }
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        if (!manager.isCuffed(p)) return;
        if (event.getTo() != null && event.getFrom() != null && !event.getTo().toVector().equals(event.getFrom().toVector())) {
            event.setTo(event.getFrom());
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
        if (e.getDamager() instanceof Player && manager.isCuffed((Player) e.getDamager())) {
            e.setCancelled(true);
        }
        if (e.getEntity() instanceof Player && manager.isCuffed((Player) e.getEntity())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { if (manager.isCuffed(e.getPlayer())) manager.uncuffPlayer(e.getPlayer()); }

    private Player findNearest(Player p, double max) {
        Player best = null; double dmin = Double.MAX_VALUE;
        for (Player t : p.getWorld().getPlayers()) {
            if (t.equals(p)) continue; double d = p.getLocation().distance(t.getLocation());
            if (d <= max && d < dmin) { dmin = d; best = t; }
        }
        return best;
    }
}


