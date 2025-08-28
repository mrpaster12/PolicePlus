package com.policeplugin.managers;

import com.policeplugin.PolicePlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class HandcuffManager {

    private final PolicePlugin plugin;
    private final Map<UUID, UUID> cuffedPlayers; // cuffed -> cuffer
    private final Map<UUID, Long> cuffTimes; // cuffed -> startedAt

    private Material handcuffItem;
    private String handcuffName;
    private String handcuffLore;
    private String handcuffNameColor;
    private boolean handcuffNameBold;
    private int maxCuffSeconds;
    private final NamespacedKey cuffKey;
    private boolean acceptPlainItem;

    public HandcuffManager(PolicePlugin plugin) {
        this.plugin = plugin;
        this.cuffedPlayers = new HashMap<>();
        this.cuffTimes = new HashMap<>();
        this.cuffKey = new NamespacedKey(plugin, "handcuff_item");
        loadConfig();
    }

    public void loadConfig() {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("handcuff");
        if (sec == null) {
            plugin.getConfig().set("handcuff.name", "cuff");
            plugin.getConfig().set("handcuff.lore", "cuff");
            plugin.getConfig().set("handcuff.name_color", "&a");
            plugin.getConfig().set("handcuff.name_bold", true);
            plugin.getConfig().set("handcuff.max_time", 300);
            plugin.saveConfig();
        }
        // Lock material to BLAZE_ROD regardless of config
        this.handcuffItem = Material.BLAZE_ROD;
        this.handcuffName = plugin.getConfig().getString("handcuff.name", "cuff");
        this.handcuffLore = plugin.getConfig().getString("handcuff.lore", "cuff");
        this.handcuffNameColor = plugin.getConfig().getString("handcuff.name_color", "&a");
        this.handcuffNameBold = plugin.getConfig().getBoolean("handcuff.name_bold", true);
        this.maxCuffSeconds = plugin.getConfig().getInt("handcuff.max_time", 300);
        this.acceptPlainItem = plugin.getConfig().getBoolean("handcuff.accept_plain_item", true);
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
        if (isCuffed(target)) return false;
        cuffedPlayers.put(target.getUniqueId(), cuffer.getUniqueId());
        cuffTimes.put(target.getUniqueId(), System.currentTimeMillis());
        // hard-freeze speeds
        try { target.setWalkSpeed(0.0f); } catch (Throwable ignored) {}
        try { target.setFlySpeed(0.0f); } catch (Throwable ignored) {}
        // strong immobilizing potion effects (hidden, no particles)
        int longTicks = Integer.MAX_VALUE / 4; // very long; cleared on uncuff/jail
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, longTicks, 255, false, false, false));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, longTicks, 255, false, false, false));
        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, longTicks, 1, false, false, false));
        try {
            if (!plugin.getWantedManager().isArrested(target)) {
                plugin.getWantedManager().arrestPlayer(target, cuffer);
            }
        } catch (Throwable ignored) {}
        return true;
    }

    public boolean uncuffPlayer(Player target) {
        if (!isCuffed(target)) return false;
        cuffedPlayers.remove(target.getUniqueId());
        cuffTimes.remove(target.getUniqueId());
        // restore default speeds
        try { target.setWalkSpeed(0.2f); } catch (Throwable ignored) {}
        try { target.setFlySpeed(0.1f); } catch (Throwable ignored) {}
        // clear immobilizing potion effects
        target.removePotionEffect(PotionEffectType.SLOW);
        target.removePotionEffect(PotionEffectType.SLOW_DIGGING);
        target.removePotionEffect(PotionEffectType.BLINDNESS);
        try {
            if (plugin.getWantedManager().isArrested(target)) {
                plugin.getWantedManager().releaseArrested(target);
            }
        } catch (Throwable ignored) {}
        return true;
    }

    public boolean isHandcuffItem(ItemStack item) {
        if (item == null || item.getType() != handcuffItem) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return acceptPlainItem; // allow plain material if configured
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(cuffKey, PersistentDataType.BYTE)) return true;
        if (acceptPlainItem) return true;
        // Fallback to display name match if PDC missing (older items)
        if (!meta.hasDisplayName()) return false;
        String expected = colorize((handcuffNameColor != null ? handcuffNameColor : "") + (handcuffNameBold ? "&l" : "") + handcuffName);
        return expected.equals(meta.getDisplayName());
    }

    public ItemStack createHandcuffItem() {
        ItemStack item = new ItemStack(handcuffItem);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = colorize((handcuffNameColor != null ? handcuffNameColor : "") + (handcuffNameBold ? "&l" : "") + handcuffName);
            meta.setDisplayName(name);
            if (handcuffLore != null && !handcuffLore.isEmpty()) {
                meta.setLore(Collections.singletonList(colorize(handcuffLore)));
            }
            // Mark with PDC to reliably detect regardless of name/locale
            meta.getPersistentDataContainer().set(cuffKey, PersistentDataType.BYTE, (byte)1);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String colorize(String str) { return str == null ? null : str.replace('&', '§'); }

    public void reloadConfig() { loadConfig(); }
}


