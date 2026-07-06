package com.policeplus.gui;

import com.policeplus.PolicePlus;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PoliceGUI {

    // Unique inventory title prefixes for each step
    public static final String GUI_DURATION_TITLE = "§8§lSelect Jail Duration";
    public static final String GUI_TYPE_TITLE = "§8§lSelect Jail Type";
    public static final String GUI_JAIL_TITLE = "§8§lSelect Jail Location";

    // PDC keys for GUI items
    private static final String PDC_ACTION = "gui_action";
    private static final String PDC_JAIL_NAME = "gui_jail_name";

    // State tracking for multi-step flow: cop UUID -> flow state
    private static final Map<UUID, JailFlowState> flowStates = new ConcurrentHashMap<>();

    public static class JailFlowState {
        public UUID targetUUID;
        public int durationMinutes = 0; // for TIME type
        public int blockCount = 0; // for BLOCKS type
        public String jailType = "TIME"; // "TIME" or "BLOCKS"
        public boolean customInputPending = false;
    }

    // ========================= Original Police GUI =========================

    public static void openPoliceGUI(Player player, PolicePlus plugin) {
        Inventory gui = Bukkit.createInventory(null, 54,
            plugin.getLanguageManager().getMessage("gui_police_title"));

        Map<UUID, Integer> wantedPlayers = plugin.getWantedManager().getWantedPlayers();
        Map<UUID, UUID> arrestedPlayers = plugin.getWantedManager().getArrestedPlayers();
        int slot = 0;

        for (Map.Entry<UUID, Integer> entry : wantedPlayers.entrySet()) {
            if (slot >= 45) break;

            String playerName = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            if (playerName != null) {
                ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) skull.getItemMeta();

                boolean isArrested = arrestedPlayers.containsKey(entry.getKey());

                if (isArrested && plugin.getWantedManager().isArrestedBy(Bukkit.getPlayer(entry.getKey()), player)) {
                    meta.setDisplayName("§6" + playerName + " §7(" + plugin.getLanguageManager().getMessage("status_arrested") + ")");

                    List<String> lore = new ArrayList<>();
                    lore.add(plugin.getLanguageManager().getMessage("lore_wanted_level", "level", String.valueOf(entry.getValue())));
                    String reason = plugin.getWantedManager().getWantedReason(entry.getKey());
                    if (reason != null && !reason.isEmpty()) {
                        lore.add(plugin.getLanguageManager().getMessage("lore_wanted_reason", "reason", reason));
                    }
                    lore.add(plugin.getLanguageManager().getMessage("lore_status_arrested_by_you"));
                    lore.add(plugin.getLanguageManager().getMessage("lore_click_to_jail"));

                    meta.setLore(lore);
                    PersistentDataContainer pdc = meta.getPersistentDataContainer();
                    pdc.set(new NamespacedKey(plugin, "target_uuid"), PersistentDataType.STRING, entry.getKey().toString());
                    pdc.set(new NamespacedKey(plugin, "state"), PersistentDataType.STRING, "arrested_by_you");
                    skull.setItemMeta(meta);
                    gui.setItem(slot, skull);
                    slot++;
                } else if (!isArrested) {
                    meta.setDisplayName("§c" + playerName);

                    List<String> lore = new ArrayList<>();
                    lore.add(plugin.getLanguageManager().getMessage("lore_wanted_level", "level", String.valueOf(entry.getValue())));
                    String reason = plugin.getWantedManager().getWantedReason(entry.getKey());
                    if (reason != null && !reason.isEmpty()) {
                        lore.add(plugin.getLanguageManager().getMessage("lore_wanted_reason", "reason", reason));
                    }
                    lore.add(plugin.getLanguageManager().getMessage("gui_click_to_arrest_or_track"));

                    meta.setLore(lore);
                    PersistentDataContainer pdc = meta.getPersistentDataContainer();
                    pdc.set(new NamespacedKey(plugin, "target_uuid"), PersistentDataType.STRING, entry.getKey().toString());
                    pdc.set(new NamespacedKey(plugin, "state"), PersistentDataType.STRING, "wanted");
                    skull.setItemMeta(meta);
                    gui.setItem(slot, skull);
                    slot++;
                }
            }
        }

        ItemStack infoItem = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName(plugin.getLanguageManager().getMessage("info_title_police"));
        List<String> infoLore = new ArrayList<>();
        infoLore.add(plugin.getLanguageManager().getMessage("info_total_wanted_players", "count", String.valueOf(wantedPlayers.size())));
        infoLore.add(plugin.getLanguageManager().getMessage("info_use_compass"));
        infoLore.add(plugin.getLanguageManager().getMessage("info_click_player_heads"));
        infoLore.add(plugin.getLanguageManager().getMessage("info_max_arrest_distance", "distance", String.valueOf(plugin.getConfigManager().getArrestDistance())));
        infoLore.add(plugin.getLanguageManager().getMessage("info_compass_tracking", "status", (plugin.getCompassManager().isTracking(player) ? "§a" + plugin.getLanguageManager().getMessage("status_active") : "§c" + plugin.getLanguageManager().getMessage("status_inactive"))));
        infoMeta.setLore(infoLore);
        infoItem.setItemMeta(infoMeta);
        gui.setItem(49, infoItem);

        player.openInventory(gui);
    }

    // ========================= Main Police GUI Click Handler =========================

    public static void handleClick(Player player, int slot, PolicePlus plugin) {
        if (slot >= 45) return;

        Inventory inv = player.getOpenInventory().getTopInventory();
        ItemStack clicked = inv.getItem(slot);

        if (clicked == null || clicked.getType() != Material.PLAYER_HEAD) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String uuidStr = pdc.get(new NamespacedKey(plugin, "target_uuid"), PersistentDataType.STRING);
        if (uuidStr == null) return;
        Player target = Bukkit.getPlayer(UUID.fromString(uuidStr));

        if (target == null) {
            player.sendMessage(plugin.getLanguageManager().getPrefix() +
                plugin.getLanguageManager().getMessage("player_not_found"));
            return;
        }

        // If arrested by this police, open multi-step jailing flow
        if (plugin.getWantedManager().isArrested(target) && plugin.getWantedManager().isArrestedBy(target, player)) {
            // Pre-checks before opening flow
            if (!plugin.getJailManager().canArrestPlayer(player, target)) {
                int distance = plugin.getConfigManager().getArrestDistance();
                player.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("jail_too_far", "distance", String.valueOf(distance)));
                player.closeInventory();
                return;
            }
            if (!plugin.getJailManager().isGlobalSpawnSet()) {
                player.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("jail_spawn_not_set"));
                player.closeInventory();
                return;
            }
            // Initialize flow state and open Step 1
            JailFlowState state = new JailFlowState();
            state.targetUUID = target.getUniqueId();
            flowStates.put(player.getUniqueId(), state);
            openDurationGUI(player, plugin);
        } else {
            // Track via compass
            if (plugin.getCompassManager().isTracking(player)) {
                Player current = plugin.getCompassManager().getCompassTarget(player);
                if (current == null || !current.equals(target)) {
                    plugin.getCompassManager().removeCompass(player);
                    plugin.getCompassManager().giveCompass(player, target);
                }
            } else {
                plugin.getCompassManager().giveCompass(player, target);
            }
            player.closeInventory();
        }
    }

    public static void handleRightClick(Player player, int slot, PolicePlus plugin) {
        if (slot >= 45) return;

        Inventory inv = player.getOpenInventory().getTopInventory();
        ItemStack clicked = inv.getItem(slot);

        if (clicked == null || clicked.getType() != Material.PLAYER_HEAD) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;

        String displayName = meta.getDisplayName();
        String playerName;

        if (displayName.contains("§6") && displayName.contains("(Arrested)")) {
            return;
        } else {
            playerName = displayName.substring(2);
        }

        Player target = Bukkit.getPlayer(playerName);

        if (target == null) {
            player.sendMessage(plugin.getLanguageManager().getPrefix() +
                plugin.getLanguageManager().getMessage("player_not_found"));
            return;
        }

        if (target != null) {
            plugin.getCompassManager().giveCompass(player, target);
            player.closeInventory();
        }
    }

    // ========================= Step 1: Duration Selection =========================

    public static void openDurationGUI(Player player, PolicePlus plugin) {
        Inventory gui = Bukkit.createInventory(null, 9, GUI_DURATION_TITLE);

        gui.setItem(1, createDurationItem(Material.CLOCK, "§a5 Minutes", 5, plugin));
        gui.setItem(2, createDurationItem(Material.CLOCK, "§a10 Minutes", 10, plugin));
        gui.setItem(3, createDurationItem(Material.CLOCK, "§a15 Minutes", 15, plugin));
        gui.setItem(4, createDurationItem(Material.CLOCK, "§a20 Minutes", 20, plugin));
        gui.setItem(6, createCustomDurationItem(Material.PAPER, plugin));

        // Cancel button
        gui.setItem(8, createCancelItem(plugin));

        player.openInventory(gui);
    }

    private static ItemStack createDurationItem(Material material, String name, int minutes, PolicePlus plugin) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        List<String> lore = new ArrayList<>();
        lore.add("§7Click to select " + minutes + " minutes jail time");
        meta.setLore(lore);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(new NamespacedKey(plugin, PDC_ACTION), PersistentDataType.STRING, "duration_" + minutes);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createCustomDurationItem(Material material, PolicePlus plugin) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§eCustom Input");
        List<String> lore = new ArrayList<>();
        lore.add("§7Type a custom duration in chat");
        lore.add("§7(in minutes)");
        meta.setLore(lore);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(new NamespacedKey(plugin, PDC_ACTION), PersistentDataType.STRING, "duration_custom");
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createCancelItem(PolicePlus plugin) {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§cCancel");
        List<String> lore = new ArrayList<>();
        lore.add("§7Cancel the jailing process");
        meta.setLore(lore);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(new NamespacedKey(plugin, PDC_ACTION), PersistentDataType.STRING, "cancel");
        item.setItemMeta(meta);
        return item;
    }

    // ========================= Step 2: Jail Type Selection =========================

    public static void openTypeGUI(Player player, PolicePlus plugin) {
        Inventory gui = Bukkit.createInventory(null, 9, GUI_TYPE_TITLE);

        // Time-based option
        ItemStack timeItem = new ItemStack(Material.CLOCK);
        ItemMeta timeMeta = timeItem.getItemMeta();
        timeMeta.setDisplayName("§aNormal Jail (Time-based)");
        List<String> timeLore = new ArrayList<>();
        timeLore.add("§7Suspect will serve time in jail");
        timeLore.add("§7Duration: §e" + flowStates.get(player.getUniqueId()).durationMinutes + " minutes");
        timeMeta.setLore(timeLore);
        PersistentDataContainer timePdc = timeMeta.getPersistentDataContainer();
        timePdc.set(new NamespacedKey(plugin, PDC_ACTION), PersistentDataType.STRING, "type_TIME");
        timeItem.setItemMeta(timeMeta);
        gui.setItem(2, timeItem);

        // Mining-based option
        ItemStack miningItem = new ItemStack(Material.STONE_PICKAXE);
        ItemMeta miningMeta = miningItem.getItemMeta();
        miningMeta.setDisplayName("§eMining Jail (Labor-based)");
        List<String> miningLore = new ArrayList<>();
        miningLore.add("§7Suspect must mine blocks to earn freedom");
        miningLore.add("§7Blocks: §e" + flowStates.get(player.getUniqueId()).blockCount);
        miningMeta.setLore(miningLore);
        PersistentDataContainer miningPdc = miningMeta.getPersistentDataContainer();
        miningPdc.set(new NamespacedKey(plugin, PDC_ACTION), PersistentDataType.STRING, "type_BLOCKS");
        miningItem.setItemMeta(miningMeta);
        gui.setItem(6, miningItem);

        // Back button
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName("§7Back");
        PersistentDataContainer backPdc = backMeta.getPersistentDataContainer();
        backPdc.set(new NamespacedKey(plugin, PDC_ACTION), PersistentDataType.STRING, "back_duration");
        backItem.setItemMeta(backMeta);
        gui.setItem(0, backItem);

        // Cancel button
        gui.setItem(8, createCancelItem(plugin));

        player.openInventory(gui);
    }

    // ========================= Step 3: Jail Selection =========================

    public static void openJailSelectionGUI(Player player, PolicePlus plugin) {
        JailFlowState state = flowStates.get(player.getUniqueId());
        if (state == null) return;

        Map<String, com.policeplus.managers.JailManager.JailInfo> allJails = plugin.getJailManager().getAllJails();
        String selectedType = state.jailType; // "TIME" or "BLOCKS"

        // Debug logging
        plugin.getLogger().info("=== JAIL SELECTION GUI DEBUG ===");
        plugin.getLogger().info("Selected type: " + selectedType);
        plugin.getLogger().info("Total jails found: " + allJails.size());
        for (Map.Entry<String, com.policeplus.managers.JailManager.JailInfo> entry : allJails.entrySet()) {
            plugin.getLogger().info("  Jail: " + entry.getKey() + " | ID: " + entry.getValue().getId() + " | type: " + entry.getValue().getType());
        }

        // Filter jails by type using getType() for robust comparison
        boolean usingFallback = false;
        List<Map.Entry<String, com.policeplus.managers.JailManager.JailInfo>> filteredJails = new ArrayList<>();
        for (Map.Entry<String, com.policeplus.managers.JailManager.JailInfo> entry : allJails.entrySet()) {
            String jailType = entry.getValue().getType();
            if (selectedType.equals(jailType)) {
                filteredJails.add(entry);
            }
        }

        plugin.getLogger().info("Filtered jails count: " + filteredJails.size());
        for (Map.Entry<String, com.policeplus.managers.JailManager.JailInfo> entry : filteredJails) {
            plugin.getLogger().info("  Filtered Jail: " + entry.getKey() + " | type: " + entry.getValue().getType());
        }

        // Fallback: if no jails match the selected type, show ALL jails so the menu is never empty
        if (filteredJails.isEmpty() && !allJails.isEmpty()) {
            plugin.getLogger().info("[Police-Plus] No jails found for type '" + selectedType + "', falling back to ALL jails.");
            filteredJails = new ArrayList<>(allJails.entrySet());
            usingFallback = true;
        }

        plugin.getLogger().info("=== END JAIL SELECTION GUI DEBUG ===");

        // Calculate inventory size: enough rows for jails + 1 row for navigation
        int jailSlots = Math.max(9, filteredJails.size());
        int rows = (jailSlots + 8) / 9; // round up to full rows
        int size = Math.max(18, (rows + 1) * 9); // +1 row for buttons
        if (size > 54) size = 54;

        Inventory gui = Bukkit.createInventory(null, size, GUI_JAIL_TITLE);

        boolean wantBlocks = selectedType.equals("BLOCKS");
        
        if (filteredJails.isEmpty()) {
            // Show placeholder when no jails exist at all
            ItemStack noJails = new ItemStack(Material.RED_STAINED_GLASS_PANE);
            ItemMeta noJailsMeta = noJails.getItemMeta();
            noJailsMeta.setDisplayName("§cNo Jails Found!");
            List<String> noJailsLore = new ArrayList<>();
            noJailsLore.add("§7Use §e/jail create <name> <id> §7to create a time jail.");
            noJailsLore.add("§7Use §e/jail createmine <name> <id> §7to create a mining jail.");
            noJailsMeta.setLore(noJailsLore);
            noJails.setItemMeta(noJailsMeta);
            gui.setItem(13, noJails);
        } else {
            // Populate jail items starting from slot 0, reserving last row for buttons
            int slot = 0;
            for (Map.Entry<String, com.policeplus.managers.JailManager.JailInfo> entry : filteredJails) {
                if (slot >= size - 9) break; // Don't overflow into button row
                String jailName = entry.getKey();
                com.policeplus.managers.JailManager.JailInfo jail = entry.getValue();

                // Determine display material based on jail's actual type (handles fallback showing mixed types)
                String jailActualType = jail.getType();
                boolean jailIsBlocks = "BLOCKS".equals(jailActualType);
                Material mat = jailIsBlocks ? Material.STONE_PICKAXE : Material.IRON_BARS;
                ItemStack item = new ItemStack(mat);
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName("§b" + jailName);
                List<String> lore = new ArrayList<>();
                lore.add("§7ID: §f" + jail.getId());
                if (usingFallback) {
                    // Show actual jail type when using fallback so cop can distinguish
                    if (jailIsBlocks) {
                        lore.add("§7Type: §eMining (BLOCKS)");
                    } else {
                        lore.add("§7Type: §eTime");
                    }
                }
                if (wantBlocks && !usingFallback) {
                    lore.add("§7Type: §eMining");
                    lore.add("§7Blocks: §e" + state.blockCount);
                } else if (!usingFallback) {
                    lore.add("§7Type: §eTime");
                    lore.add("§7Duration: §e" + state.durationMinutes + " min");
                }
                lore.add("");
                lore.add("§aClick to send suspect here!");
                meta.setLore(lore);

                PersistentDataContainer pdc = meta.getPersistentDataContainer();
                pdc.set(new NamespacedKey(plugin, PDC_ACTION), PersistentDataType.STRING, "jail_select");
                pdc.set(new NamespacedKey(plugin, PDC_JAIL_NAME), PersistentDataType.STRING, jailName);
                item.setItemMeta(meta);
                gui.setItem(slot, item);
                slot++;
            }
        }

        // Back button
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName("§7Back");
        PersistentDataContainer backPdc = backMeta.getPersistentDataContainer();
        backPdc.set(new NamespacedKey(plugin, PDC_ACTION), PersistentDataType.STRING, "back_type");
        backItem.setItemMeta(backMeta);
        gui.setItem(size - 9, backItem);

        // Cancel button
        gui.setItem(size - 1, createCancelItem(plugin));

        player.openInventory(gui);
    }

    // ========================= GUI Click Routing =========================

    /**
     * Handles clicks in the multi-step jailing GUIs (Duration, Type, Jail Selection).
     * Returns true if the click was handled, false otherwise.
     */
    public static boolean handleSubGUIClick(Player player, String title, int slot, PolicePlus plugin) {
        if (!title.equals(GUI_DURATION_TITLE) && !title.equals(GUI_TYPE_TITLE) && !title.equals(GUI_JAIL_TITLE)) {
            return false;
        }

        Inventory inv = player.getOpenInventory().getTopInventory();
        ItemStack clicked = inv.getItem(slot);
        if (clicked == null || clicked.getType() == Material.AIR) return true;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return true;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String action = pdc.get(new NamespacedKey(plugin, PDC_ACTION), PersistentDataType.STRING);
        if (action == null) return true;

        JailFlowState state = flowStates.get(player.getUniqueId());
        if (state == null) {
            player.closeInventory();
            return true;
        }

        // Cancel action
        if (action.equals("cancel")) {
            flowStates.remove(player.getUniqueId());
            player.closeInventory();
            player.sendMessage(plugin.getLanguageManager().getPrefix() + plugin.getLanguageManager().getMessage("jail_cancelled"));
            return true;
        }

        // Duration selection
        if (action.startsWith("duration_")) {
            if (action.equals("duration_custom")) {
                // Mark as custom input pending, close GUI, wait for chat
                state.customInputPending = true;
                player.closeInventory();
                player.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("jail_custom_duration_prompt"));
                return true;
            }
            int minutes = Integer.parseInt(action.replace("duration_", ""));
            state.durationMinutes = minutes;
            // Calculate block count based on wanted level and config
            int wantedLevel = plugin.getWantedManager().getWantedLevel(Bukkit.getPlayer(state.targetUUID));
            if (wantedLevel <= 0) wantedLevel = 1;
            state.blockCount = wantedLevel * plugin.getConfigManager().getBlocksPerWanted();
            // Move to Step 2
            openTypeGUI(player, plugin);
            return true;
        }

        // Type selection
        if (action.startsWith("type_")) {
            state.jailType = action.replace("type_", "");
            // Move to Step 3
            openJailSelectionGUI(player, plugin);
            return true;
        }

        // Jail selection
        if (action.equals("jail_select")) {
            String jailName = pdc.get(new NamespacedKey(plugin, PDC_JAIL_NAME), PersistentDataType.STRING);
            if (jailName == null) return true;
            executeJail(player, jailName, state, plugin);
            flowStates.remove(player.getUniqueId());
            player.closeInventory();
            return true;
        }

        // Navigation
        if (action.equals("back_duration")) {
            openDurationGUI(player, plugin);
            return true;
        }
        if (action.equals("back_type")) {
            openTypeGUI(player, plugin);
            return true;
        }

        return true;
    }

    // ========================= Custom Input Handling =========================

    /**
     * Handles custom duration input from chat.
     * Returns true if the message was consumed by the flow.
     */
    public static boolean handleChatInput(Player player, String message, PolicePlus plugin) {
        JailFlowState state = flowStates.get(player.getUniqueId());
        if (state == null || !state.customInputPending) return false;

        if (message.equalsIgnoreCase("cancel")) {
            flowStates.remove(player.getUniqueId());
            player.sendMessage(plugin.getLanguageManager().getPrefix() + plugin.getLanguageManager().getMessage("jail_cancelled"));
            return true;
        }

        try {
            int minutes = Integer.parseInt(message.trim());
            if (minutes <= 0) {
                player.sendMessage(plugin.getLanguageManager().getPrefix() + plugin.getLanguageManager().getMessage("jail_custom_duration_positive"));
                return true;
            }
            state.durationMinutes = minutes;
            state.customInputPending = false;
            // Calculate block count
            int wantedLevel = plugin.getWantedManager().getWantedLevel(Bukkit.getPlayer(state.targetUUID));
            if (wantedLevel <= 0) wantedLevel = 1;
            state.blockCount = wantedLevel * plugin.getConfigManager().getBlocksPerWanted();
            // Move to Step 2
            openTypeGUI(player, plugin);
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.getLanguageManager().getPrefix() + plugin.getLanguageManager().getMessage("jail_custom_duration_invalid"));
        }
        return true;
    }

    // ========================= Jail Execution =========================

    private static void executeJail(Player police, String jailName, JailFlowState state, PolicePlus plugin) {
        Player target = Bukkit.getPlayer(state.targetUUID);
        if (target == null) {
            police.sendMessage(plugin.getLanguageManager().getPrefix() +
                plugin.getLanguageManager().getMessage("player_not_found"));
            return;
        }

        // Get wanted level BEFORE clearing it (for economy reward calculation)
        int wantedLevel = plugin.getWantedManager().getWantedLevel(target);

        // Double-check distance and jail proximity
        if (!plugin.getJailManager().canArrestPlayer(police, target)) {
            int distance = plugin.getConfigManager().getArrestDistance();
            police.sendMessage(plugin.getLanguageManager().getPrefix() +
                plugin.getLanguageManager().getMessage("jail_too_far", "distance", String.valueOf(distance)));
            return;
        }

        if (plugin.getJailManager().getJail(jailName) == null) {
            police.sendMessage(plugin.getLanguageManager().getPrefix() +
                plugin.getLanguageManager().getMessage("jail_not_found", "jail_name", jailName));
            return;
        }

        // Check proximity to the selected jail cell
        if (!plugin.getJailManager().isNearJailLocation(police, jailName)) {
            police.sendMessage(plugin.getLanguageManager().getPrefix() +
                plugin.getLanguageManager().getMessage("jail_not_near_jail_cell", "jail", jailName));
            return;
        }

        // Apply jail based on the actual jail's type (handles fallback where type might differ)
        com.policeplus.managers.JailManager.JailInfo jailInfo = plugin.getJailManager().getJail(jailName);
        String actualType = jailInfo.getType();
        if ("BLOCKS".equals(actualType)) {
            plugin.getJailManager().jailPlayerWithBlocks(target, jailName, state.blockCount);
        } else {
            plugin.getJailManager().jailPlayer(target, jailName, state.durationMinutes);
        }

        // Clear wanted level and release from arrest
        plugin.getWantedManager().removeWanted(target);
        plugin.getWantedManager().releaseArrested(target);

        // Stop compass tracking
        plugin.getCompassManager().stopTracking(police);
        plugin.getCompassManager().stopTracking(target);

        // Economy reward
        try {
            if (plugin.getConfigManager().isEconomyEnabled() && PolicePlus.getEconomy() != null) {
                if (wantedLevel <= 0) wantedLevel = 1;
                double reward = wantedLevel * plugin.getConfigManager().getEconomyRewardPerWantedLevel();
                PolicePlus.getEconomy().depositPlayer(police, reward);
                police.sendMessage(plugin.getLanguageManager().getPrefix() +
                    plugin.getLanguageManager().getMessage("economy_reward",
                        "amount", String.format("%.0f", reward),
                        "player", target.getName(),
                        "level", String.valueOf(wantedLevel)));
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not pay economy reward: " + t.getMessage());
        }

        police.sendMessage(plugin.getLanguageManager().getPrefix() +
            plugin.getLanguageManager().getMessage("player_jailed_success"));
    }

    // ========================= State Management =========================

    public static boolean isAwaitingCustomInput(Player player) {
        JailFlowState state = flowStates.get(player.getUniqueId());
        return state != null && state.customInputPending;
    }

    public static void cancelFlow(Player player) {
        flowStates.remove(player.getUniqueId());
    }
}