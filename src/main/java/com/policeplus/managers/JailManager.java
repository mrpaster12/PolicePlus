package com.policeplus.managers;

import com.policeplus.PolicePlus;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class JailManager {

    private final PolicePlus plugin;
    private final Map<UUID, JailData> jailedPlayers; // UUID -> JailData
    private final Map<String, JailInfo> jails; // jail_name -> JailInfo
    private final File dataFile;
    private final FileConfiguration dataConfig;
    private int pendingSaveTaskId = -1;
    private volatile boolean dirty = false;
    private int actionbarTaskId = -1;
    private Location globalSpawn; // Global release/spawn location (set by /jail spawn)

    // Unified block regen queue — single repeating task handles all regens
    private static class QueuedBlockRegen {
        final org.bukkit.Location location;
        final long regenTimeMs;
        QueuedBlockRegen(org.bukkit.Location location, long regenTimeMs) {
            this.location = location;
            this.regenTimeMs = regenTimeMs;
        }
    }
    private final ConcurrentLinkedQueue<QueuedBlockRegen> regenQueue = new ConcurrentLinkedQueue<>();
    private int regenTaskId = -1;

    public static class JailInfo {
        private final String name;
        private final String id;
        private Location location;
        private Location spawnLocation;
        private int radius;
        private String type; // "TIME" or "BLOCKS"
        private Location pos1; // cuboid corner 1
        private Location pos2; // cuboid corner 2

        public JailInfo(String name, String id, Location location) {
            this.name = name;
            this.id = id;
            this.location = location;
            this.radius = 10; // Default radius
            this.type = "TIME"; // Default to TIME-based jail
        }

        public String getName() { return name; }
        public String getId() { return id; }
        public Location getLocation() { return location; }
        public void setLocation(Location location) { this.location = location; }
        public Location getSpawnLocation() { return spawnLocation; }
        public void setSpawnLocation(Location spawnLocation) { this.spawnLocation = spawnLocation; }
        public int getRadius() { return radius; }
        public void setRadius(int radius) { this.radius = radius; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public boolean isMine() { return "BLOCKS".equals(type); }
        public void setMine(boolean mine) { this.type = mine ? "BLOCKS" : "TIME"; }
        public Location getPos1() { return pos1; }
        public void setPos1(Location pos1) { this.pos1 = pos1; }
        public Location getPos2() { return pos2; }
        public void setPos2(Location pos2) { this.pos2 = pos2; }
        public boolean hasCuboid() { return pos1 != null && pos2 != null; }
    }

    public static class JailData {
        private final String jailName;
        private final long releaseTime; // Used when type=TIME
        private final int requiredBlocks; // Used when type=BLOCKS
        private int brokenBlocks; // Used when type=BLOCKS

        // Constructor for TIME-based jail
        public JailData(String jailName, long releaseTime) {
            this.jailName = jailName;
            this.releaseTime = releaseTime;
            this.requiredBlocks = 0;
            this.brokenBlocks = 0;
        }

        // Constructor for BLOCKS-based jail
        public JailData(String jailName, int requiredBlocks) {
            this.jailName = jailName;
            this.releaseTime = 0;
            this.requiredBlocks = requiredBlocks;
            this.brokenBlocks = 0;
        }

        public String getJailName() {
            return jailName;
        }

        public long getReleaseTime() {
            return releaseTime;
        }

        public int getRequiredBlocks() {
            return requiredBlocks;
        }

        public int getBrokenBlocks() {
            return brokenBlocks;
        }

        public void setBrokenBlocks(int brokenBlocks) {
            this.brokenBlocks = brokenBlocks;
        }

        public boolean isBlocksType() {
            return requiredBlocks > 0;
        }
    }

    public JailManager(PolicePlus plugin) {
        this.plugin = plugin;
        this.jailedPlayers = new ConcurrentHashMap<>();
        this.jails = new ConcurrentHashMap<>();
        this.dataFile = new File(plugin.getDataFolder(), "jail.yml");
        this.dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        loadData();
        startActionBarTask();
    }

    public boolean createJail(String name, String id, Location location) {
        if (jails.containsKey(name)) {
            return false; // Jail already exists
        }

        JailInfo jailInfo = new JailInfo(name, id, location);
        jailInfo.setMine(false);
        jails.put(name, jailInfo);
        plugin.getLogger().info("Created TIME jail: " + name + " (id: " + id + ") at " + location);
        markDirtyAndScheduleSave();
        return true;
    }

    public boolean createMineJail(String name, String id, Location location) {
        if (jails.containsKey(name)) {
            return false;
        }

        JailInfo jailInfo = new JailInfo(name, id, location);
        jailInfo.setMine(true);
        jails.put(name, jailInfo);
        plugin.getLogger().info("Created BLOCKS (mine) jail: " + name + " (id: " + id + ") at " + location);
        markDirtyAndScheduleSave();
        return true;
    }

    public boolean setJailPos1(String jailName, Location pos1) {
        JailInfo jail = jails.get(jailName);
        if (jail == null) return false;
        jail.setPos1(pos1);
        markDirtyAndScheduleSave();
        return true;
    }

    public boolean setJailPos2(String jailName, Location pos2) {
        JailInfo jail = jails.get(jailName);
        if (jail == null) return false;
        jail.setPos2(pos2);
        markDirtyAndScheduleSave();
        return true;
    }

    /**
     * Fills the cuboid region of a mine-jail with COBBLESTONE blocks.
     * Only patches blocks that are currently AIR (i.e. missing/mined) —
     * it never overwrites a block that's already there, so re-jailing a new
     * prisoner into the same mine doesn't wipe out leftover blocks or
     * anything an admin placed for testing/decoration.
     * (FIX: previously this force-overwrote EVERY block in the cuboid on
     * every single jailing, which is why manually placed blocks kept
     * disappearing shortly after being placed.)
     */
    public void fillCuboid(String jailName) {
        JailInfo jail = jails.get(jailName);
        if (jail == null || !jail.hasCuboid()) return;
        Location p1 = jail.getPos1();
        Location p2 = jail.getPos2();
        if (!p1.getWorld().equals(p2.getWorld())) return;

        int minX = Math.min(p1.getBlockX(), p2.getBlockX());
        int maxX = Math.max(p1.getBlockX(), p2.getBlockX());
        int minY = Math.min(p1.getBlockY(), p2.getBlockY());
        int maxY = Math.max(p1.getBlockY(), p2.getBlockY());
        int minZ = Math.min(p1.getBlockZ(), p2.getBlockZ());
        int maxZ = Math.max(p1.getBlockZ(), p2.getBlockZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    org.bukkit.block.Block block = p1.getWorld().getBlockAt(x, y, z);
                    if (block.getType() == org.bukkit.Material.AIR) {
                        block.setType(org.bukkit.Material.COBBLESTONE);
                    }
                }
            }
        }
    }

    /**
     * Checks if a location is inside any mine-jail's cuboid region.
     */
    public JailInfo getMineJailForLocation(Location loc) {
        for (JailInfo jail : jails.values()) {
            if (!jail.isMine() || !jail.hasCuboid()) continue;
            Location p1 = jail.getPos1();
            Location p2 = jail.getPos2();
            if (!loc.getWorld().equals(p1.getWorld())) continue;
            int minX = Math.min(p1.getBlockX(), p2.getBlockX());
            int maxX = Math.max(p1.getBlockX(), p2.getBlockX());
            int minY = Math.min(p1.getBlockY(), p2.getBlockY());
            int maxY = Math.max(p1.getBlockY(), p2.getBlockY());
            int minZ = Math.min(p1.getBlockZ(), p2.getBlockZ());
            int maxZ = Math.max(p1.getBlockZ(), p2.getBlockZ());
            int bx = loc.getBlockX(), by = loc.getBlockY(), bz = loc.getBlockZ();
            if (bx >= minX && bx <= maxX && by >= minY && by <= maxY && bz >= minZ && bz <= maxZ) {
                return jail;
            }
        }
        return null;
    }

    /**
     * Checks if a location is inside any active BLOCKS-type mine-jail cuboid region.
     * Returns false by default if no mine positions are set or location is not inside any cuboid.
     */
    public boolean isInsideAnyMineRegion(Location loc) {
        return getMineJailForLocation(loc) != null;
    }

    public boolean deleteJail(String name) {
        if (!jails.containsKey(name)) {
            return false; // Jail doesn't exist
        }

        jails.remove(name);
        markDirtyAndScheduleSave();
        return true;
    }

    public JailInfo getJail(String name) {
        return jails.get(name);
    }

    public Map<String, JailInfo> getAllJails() {
        return new HashMap<>(jails);
    }

    public boolean setJailSpawn(String jailName, Location spawnLocation) {
        JailInfo jail = jails.get(jailName);
        if (jail == null) {
            return false;
        }

        jail.setSpawnLocation(spawnLocation);
        markDirtyAndScheduleSave();
        return true;
    }

    public void jailPlayer(Player player, String jailName, int minutes) {
        JailInfo jail = jails.get(jailName);
        if (jail == null) {
            return; // Jail doesn't exist
        }

        // Return handcuff to police if player was cuffed
        Player cuffer = plugin.getHandcuffManager().getCuffer(player);
        if (cuffer != null && plugin.getHandcuffManager().isCuffed(player)) {
            // Give handcuff item back to the police officer
            org.bukkit.inventory.ItemStack handcuffItem = plugin.getHandcuffManager().createHandcuffItem();
            java.util.HashMap<Integer, org.bukkit.inventory.ItemStack> leftover = cuffer.getInventory()
                    .addItem(handcuffItem);

            // If inventory is full, drop the item at police location
            if (!leftover.isEmpty()) {
                cuffer.getWorld().dropItemNaturally(cuffer.getLocation(), handcuffItem);
                cuffer.sendMessage(plugin.getLanguageManager().getPrefix() +
                        plugin.getLanguageManager().getMessage("handcuff.returned_dropped"));
            } else {
                cuffer.sendMessage(plugin.getLanguageManager().getPrefix() +
                        plugin.getLanguageManager().getMessage("handcuff.returned_inventory"));
            }

            // Remove cuff status
            plugin.getHandcuffManager().uncuffPlayer(player);
        }

        // Clear any cuff immobilization effects upon jailing
        try {
            player.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOW);
            player.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOW_DIGGING);
            player.removePotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS);
            player.setWalkSpeed(0.2f);
            player.setFlySpeed(0.1f);
        } catch (Throwable ignored) {
        }

        long releaseTime = System.currentTimeMillis() + (minutes * 60 * 1000L);
        JailData jailData = new JailData(jailName, releaseTime);
        jailedPlayers.put(player.getUniqueId(), jailData);
        saveData();

        // Record jail in stats
        if (plugin.getStatsManager() != null) {
            plugin.getStatsManager().recordJail(player);
        }

        // Teleport to jail
        player.teleport(jail.getLocation());

        String message = plugin.getLanguageManager().getMessage("jail_time_set",
                "player", player.getName(), "time", String.valueOf(minutes));
        plugin.getServer().broadcastMessage(plugin.getLanguageManager().getPrefix() + message);

        // Update display for the jailed player
        plugin.getDisplayManager().updatePlayerDisplay(player);
    }

    public void jailPlayerByWantedLevel(Player player, String jailName) {
        int wantedLevel = plugin.getWantedManager().getWantedLevel(player);

        if (plugin.getConfigManager().isJailTypeBlocks()) {
            // BLOCKS-based jailing
            int requiredBlocks = wantedLevel * plugin.getConfigManager().getBlocksPerWanted();
            jailPlayerWithBlocks(player, jailName, requiredBlocks);
        } else {
            // TIME-based jailing
            int jailTime = wantedLevel * plugin.getConfigManager().getJailTimePerWanted();
            jailTime = Math.min(jailTime, plugin.getConfigManager().getMaxJailTime());
            jailPlayer(player, jailName, jailTime);
        }
    }

    /**
     * Jails a player with a block-mining labor requirement instead of time.
     */
    public void jailPlayerWithBlocks(Player player, String jailName, int requiredBlocks) {
        JailInfo jail = jails.get(jailName);
        if (jail == null) return;

        // Return handcuff to police if player was cuffed
        Player cuffer = plugin.getHandcuffManager().getCuffer(player);
        if (cuffer != null && plugin.getHandcuffManager().isCuffed(player)) {
            org.bukkit.inventory.ItemStack handcuffItem = plugin.getHandcuffManager().createHandcuffItem();
            java.util.HashMap<Integer, org.bukkit.inventory.ItemStack> leftover = cuffer.getInventory().addItem(handcuffItem);
            if (!leftover.isEmpty()) {
                cuffer.getWorld().dropItemNaturally(cuffer.getLocation(), handcuffItem);
                cuffer.sendMessage(plugin.getLanguageManager().getPrefix() +
                        plugin.getLanguageManager().getMessage("handcuff.returned_dropped"));
            } else {
                cuffer.sendMessage(plugin.getLanguageManager().getPrefix() +
                        plugin.getLanguageManager().getMessage("handcuff.returned_inventory"));
            }
            plugin.getHandcuffManager().uncuffPlayer(player);
        }

        // Clear cuff immobilization effects
        try {
            player.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOW);
            player.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOW_DIGGING);
            player.removePotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS);
            player.setWalkSpeed(0.2f);
            player.setFlySpeed(0.1f);
        } catch (Throwable ignored) {
        }

        JailData jailData = new JailData(jailName, requiredBlocks);
        jailedPlayers.put(player.getUniqueId(), jailData);
        saveData();

        // Record jail in stats
        if (plugin.getStatsManager() != null) {
            plugin.getStatsManager().recordJail(player);
        }

        // Give restricted pickaxe
        giveJailPickaxe(player);

        // If mine-jail with cuboid, fill the region with cobblestone before teleporting
        if (jail.isMine() && jail.hasCuboid()) {
            fillCuboid(jailName);
        }

        // Teleport to jail
        player.teleport(jail.getLocation());

        String message = plugin.getLanguageManager().getMessage("jail_blocks_set",
                "player", player.getName(), "blocks", String.valueOf(requiredBlocks));
        plugin.getServer().broadcastMessage(plugin.getLanguageManager().getPrefix() + message);

        plugin.getDisplayManager().updatePlayerDisplay(player);
    }

    /**
     * Public accessor for giving a jail pickaxe to a player.
     * Used by JailListener to re-give pickaxe on respawn.
     */
    public void giveJailPickaxeToPlayer(Player player) {
        giveJailPickaxe(player);
    }

    /**
     * Gives a restricted stone pickaxe to a jailed player for mining labor.
     */
    private void giveJailPickaxe(Player player) {
        org.bukkit.inventory.ItemStack pickaxe = new org.bukkit.inventory.ItemStack(org.bukkit.Material.STONE_PICKAXE);
        org.bukkit.inventory.meta.ItemMeta meta = pickaxe.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e§lJail Pickaxe");
            meta.setUnbreakable(true);
            // Prevent enchanting/combining
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_UNBREAKABLE);
            pickaxe.setItemMeta(meta);
        }
        player.getInventory().addItem(pickaxe);
    }

    /**
     * Records a block being mined by a jailed player.
     * Returns true if the player has finished their required blocks and should be released.
     */
    public boolean mineBlock(Player player) {
        JailData data = jailedPlayers.get(player.getUniqueId());
        if (data == null || !data.isBlocksType()) return false;

        int newCount = data.getBrokenBlocks() + 1;
        data.setBrokenBlocks(newCount);
        markDirtyAndScheduleSave();

        return newCount >= data.getRequiredBlocks();
    }

    /**
     * Gets remaining blocks a jailed player needs to mine.
     */
    public int getRemainingBlocks(Player player) {
        JailData data = jailedPlayers.get(player.getUniqueId());
        if (data == null || !data.isBlocksType()) return 0;
        return Math.max(0, data.getRequiredBlocks() - data.getBrokenBlocks());
    }

    /**
     * Gets required blocks for a jailed player.
     */
    public int getRequiredBlocks(Player player) {
        JailData data = jailedPlayers.get(player.getUniqueId());
        if (data == null) return 0;
        return data.getRequiredBlocks();
    }

    /**
     * Returns true if the player is in a BLOCKS-type jail.
     */
    public boolean isBlocksJail(Player player) {
        JailData data = jailedPlayers.get(player.getUniqueId());
        return data != null && data.isBlocksType();
    }

    /**
     * Removes any jail pickaxe items from a player's entire inventory.
     * Should be called on release to prevent smuggling restricted items out.
     */
    public void removeJailPickaxe(Player player) {
        if (player.getInventory() == null) return;
        player.getInventory().forEach(item -> {
            if (item != null && item.getType() == org.bukkit.Material.STONE_PICKAXE
                    && item.getItemMeta() != null && item.getItemMeta().isUnbreakable()) {
                item.setAmount(0);
            }
        });
        player.updateInventory();
    }

    public void releasePlayer(Player player) {
        JailData jailData = jailedPlayers.remove(player.getUniqueId());
        if (jailData != null) {
            markDirtyAndScheduleSave();

            // Remove jail pickaxe before release
            removeJailPickaxe(player);

            // Clear wanted level if configured
            if (plugin.getConfigManager().isClearWantedOnRelease()) {
                plugin.getWantedManager().removeWanted(player);
                player.sendMessage(plugin.getLanguageManager().getPrefix() +
                        plugin.getLanguageManager().getMessage("jail_wanted_cleared"));
            }

            // Safety: ensure movement speeds are reset on release
            try {
                player.setWalkSpeed(0.2f);
                player.setFlySpeed(0.1f);
            } catch (Throwable ignored) {
            }

            // Teleport to global spawn if set, otherwise fall back to jail spawn
            if (globalSpawn != null) {
                player.teleport(globalSpawn);
            } else {
                JailInfo jail = jails.get(jailData.getJailName());
                if (jail != null && jail.getSpawnLocation() != null) {
                    player.teleport(jail.getSpawnLocation());
                }
            }

            // Send release notification to cops only (not broadcast to entire server)
            String copMessage = plugin.getLanguageManager().getMessage("jail_release_cop_notify",
                    "player", player.getName());
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (PolicePlus.isCop(onlinePlayer)) {
                    onlinePlayer.sendMessage(plugin.getLanguageManager().getPrefix() + copMessage);
                }
            }
            // Send direct message to the released player
            String playerMessage = plugin.getLanguageManager().getMessage("jail_release_player_notify");
            player.sendMessage(plugin.getLanguageManager().getPrefix() + playerMessage);

            // Update display for the released player
            plugin.getDisplayManager().updatePlayerDisplay(player);
        }
    }

    public boolean isJailed(Player player) {
        if (!jailedPlayers.containsKey(player.getUniqueId())) {
            return false;
        }

        JailData jailData = jailedPlayers.get(player.getUniqueId());

        // For BLOCKS type, check if all blocks are mined
        if (jailData.isBlocksType()) {
            if (jailData.getBrokenBlocks() >= jailData.getRequiredBlocks()) {
                releasePlayer(player);
                return false;
            }
            return true;
        }

        // For TIME type, check if time has elapsed
        if (jailData.getReleaseTime() > 0 && System.currentTimeMillis() >= jailData.getReleaseTime()) {
            releasePlayer(player);
            return false;
        }

        return true;
    }

    public long getRemainingTime(Player player) {
        if (!isJailed(player)) {
            return 0;
        }

        JailData jailData = jailedPlayers.get(player.getUniqueId());
        if (jailData.isBlocksType()) return 0; // N/A for blocks type
        return Math.max(0, jailData.getReleaseTime() - System.currentTimeMillis());
    }

    public String getJailName(Player player) {
        if (!isJailed(player)) {
            return null;
        }

        JailData jailData = jailedPlayers.get(player.getUniqueId());
        return jailData.getJailName();
    }

    /**
     * Checks if the global jail spawn (set by /jail spawn) is configured.
     * This is the release point for unjailed players.
     */
    public boolean isGlobalSpawnSet() {
        return globalSpawn != null;
    }

    /**
     * Checks if the cop is within 10 blocks of a specific jail cell's location.
     * The jail cell location is set when /jail create or /jail createmine is used.
     * Returns true if within 10 blocks, false otherwise.
     */
    public boolean isNearJailLocation(Player cop, String jailName) {
        JailInfo jail = jails.get(jailName);
        if (jail == null) return false;
        Location jailLoc = jail.getLocation();
        if (jailLoc == null) return false;
        if (!cop.getWorld().equals(jailLoc.getWorld())) return false;
        return cop.getLocation().distance(jailLoc) <= 10;
    }

    public boolean canArrestPlayer(Player police, Player target) {
        int maxDistance = plugin.getConfigManager().getArrestDistance();
        double distance = police.getLocation().distance(target.getLocation());
        return distance <= maxDistance;
    }

    public Map<UUID, JailData> getJailedPlayers() {
        return new HashMap<>(jailedPlayers);
    }

    public String getLeastPopulatedJailName() {
        if (jails.isEmpty())
            return null;
        Map<String, Integer> counts = new HashMap<>();
        for (String name : jails.keySet()) {
            counts.put(name, 0);
        }
        for (JailData data : jailedPlayers.values()) {
            if (data.getJailName() != null) {
                counts.computeIfPresent(data.getJailName(), (k, v) -> v + 1);
            }
        }
        String best = null;
        int bestCount = Integer.MAX_VALUE;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (e.getValue() < bestCount) {
                best = e.getKey();
                bestCount = e.getValue();
            }
        }
        return best;
    }

    private void loadData() {
        if (dataFile.exists()) {
            // Load jails
            if (dataConfig.contains("jails")) {
                plugin.getLogger().info("=== JAIL MANAGER LOAD DEBUG ===");
                for (String jailName : dataConfig.getConfigurationSection("jails").getKeys(false)) {
                    String id = dataConfig.getString("jails." + jailName + ".id");
                    Location location = loadLocation("jails." + jailName + ".location");
                    Location spawnLocation = loadLocation("jails." + jailName + ".spawn");
                    int radius = dataConfig.getInt("jails." + jailName + ".radius", 10);

                    // Load type field (TIME or BLOCKS), fallback to mine boolean for backward compatibility
                    String type = dataConfig.getString("jails." + jailName + ".type");
                    if (type == null) {
                        // Backward compatibility: use mine boolean
                        boolean mine = dataConfig.getBoolean("jails." + jailName + ".mine", false);
                        type = mine ? "BLOCKS" : "TIME";
                    }
                    Location pos1 = loadLocation("jails." + jailName + ".pos1");
                    Location pos2 = loadLocation("jails." + jailName + ".pos2");

                    plugin.getLogger().info("Loading jail: " + jailName + " | id: " + id + " | type: " + type + " | location: " + location);

                    if (location != null) {
                        JailInfo jailInfo = new JailInfo(jailName, id, location);
                        jailInfo.setSpawnLocation(spawnLocation);
                        jailInfo.setRadius(radius);
                        jailInfo.setType(type);
                        jailInfo.setPos1(pos1);
                        jailInfo.setPos2(pos2);
                        jails.put(jailName, jailInfo);
                    }
                }
                plugin.getLogger().info("Total jails loaded: " + jails.size());
                plugin.getLogger().info("=== END JAIL MANAGER LOAD DEBUG ===");
            }

            // Load global spawn (release hub)
            if (dataConfig.contains("global_spawn")) {
                globalSpawn = loadLocation("global_spawn");
            } else if (dataConfig.contains("jail_hub")) {
                // Legacy migration: jail_hub -> global_spawn
                globalSpawn = loadLocation("jail_hub");
            }

            // Load jailed players
            if (dataConfig.contains("jailed_players")) {
                for (String uuidString : dataConfig.getConfigurationSection("jailed_players").getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(uuidString);
                        String jailName = dataConfig.getString("jailed_players." + uuidString + ".jail_name");
                        long releaseTime = dataConfig.getLong("jailed_players." + uuidString + ".release_time");
                        int requiredBlocks = dataConfig.getInt("jailed_players." + uuidString + ".required_blocks", 0);
                        int brokenBlocks = dataConfig.getInt("jailed_players." + uuidString + ".broken_blocks", 0);

                        JailData jailData;
                        if (requiredBlocks > 0) {
                            // BLOCKS-type jail
                            jailData = new JailData(jailName, requiredBlocks);
                            jailData.setBrokenBlocks(brokenBlocks);
                        } else {
                            // TIME-type jail
                            jailData = new JailData(jailName, releaseTime);
                        }
                        jailedPlayers.put(uuid, jailData);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid UUID in jail.yml: " + uuidString);
                    }
                }
            }
        }
    }

    private Location loadLocation(String path) {
        if (!dataConfig.contains(path)) {
            return null;
        }

        String worldName = dataConfig.getString(path + ".world");
        double x = dataConfig.getDouble(path + ".x");
        double y = dataConfig.getDouble(path + ".y");
        double z = dataConfig.getDouble(path + ".z");
        float yaw = (float) dataConfig.getDouble(path + ".yaw", 0);
        float pitch = (float) dataConfig.getDouble(path + ".pitch", 0);

        if (plugin.getServer().getWorld(worldName) != null) {
            return new Location(plugin.getServer().getWorld(worldName), x, y, z, yaw, pitch);
        }

        return null;
    }

    private void saveLocation(String path, Location location) {
        if (location != null) {
            dataConfig.set(path + ".world", location.getWorld().getName());
            dataConfig.set(path + ".x", location.getX());
            dataConfig.set(path + ".y", location.getY());
            dataConfig.set(path + ".z", location.getZ());
            dataConfig.set(path + ".yaw", location.getYaw());
            dataConfig.set(path + ".pitch", location.getPitch());
        }
    }

    public void saveData() {
        // Take snapshots to avoid concurrent modification while saving asynchronously
        Map<String, JailInfo> jailsSnapshot = new HashMap<>(jails);
        Map<UUID, JailData> jailedSnapshot = new HashMap<>(jailedPlayers);

        plugin.getLogger().info("=== JAIL MANAGER SAVE DEBUG ===");
        plugin.getLogger().info("Saving " + jailsSnapshot.size() + " jails");

        // Save jails
        for (JailInfo jail : jailsSnapshot.values()) {
            String path = "jails." + jail.getName();
            dataConfig.set(path + ".id", jail.getId());
            saveLocation(path + ".location", jail.getLocation());
            saveLocation(path + ".spawn", jail.getSpawnLocation());
            dataConfig.set(path + ".radius", jail.getRadius());
            dataConfig.set(path + ".type", jail.getType()); // Save explicit type field
            dataConfig.set(path + ".mine", jail.isMine()); // Keep for backward compatibility
            if (jail.getPos1() != null) saveLocation(path + ".pos1", jail.getPos1());
            if (jail.getPos2() != null) saveLocation(path + ".pos2", jail.getPos2());
            
            plugin.getLogger().info("  Saving jail: " + jail.getName() + " | id: " + jail.getId() + " | type: " + jail.getType() + " | location: " + jail.getLocation());
        }
        plugin.getLogger().info("=== END JAIL MANAGER SAVE DEBUG ===");

        // Save global spawn
        if (globalSpawn != null) {
            saveLocation("global_spawn", globalSpawn);
        }

        // Save jailed players
        for (Map.Entry<UUID, JailData> entry : jailedSnapshot.entrySet()) {
            String path = "jailed_players." + entry.getKey().toString();
            JailData data = entry.getValue();
            dataConfig.set(path + ".jail_name", data.getJailName());
            dataConfig.set(path + ".release_time", data.getReleaseTime());
            // Save blocks data for BLOCKS-type jails
            if (data.isBlocksType()) {
                dataConfig.set(path + ".required_blocks", data.getRequiredBlocks());
                dataConfig.set(path + ".broken_blocks", data.getBrokenBlocks());
            }
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save jail data: " + e.getMessage());
        }
    }

    private void markDirtyAndScheduleSave() {
        dirty = true;
        if (pendingSaveTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(pendingSaveTaskId);
        }
        pendingSaveTaskId = plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                if (dirty) {
                    dirty = false;
                    saveData();
                }
            }
        }, 40L).getTaskId();
    }

    private void startActionBarTask() {
        if (actionbarTaskId != -1)
            return;
        actionbarTaskId = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!player.isOnline())
                        continue;
                    if (!isJailed(player))
                        continue;

                    // === Boundary Enforcement (runs every second instead of PlayerMoveEvent) ===
                    if (!player.hasPermission("policeplus.bypass")) {
                        String jName = getJailName(player);
                        if (jName != null) {
                            JailInfo jail = getJail(jName);
                            if (jail != null) {
                                // Use the jail cell location as the boundary center (not the release spawn)
                                Location jailCenter = jail.getLocation();
                                if (jailCenter != null && jailCenter.getWorld() != null) {
                                    boolean escaped = false;
                                    if (!player.getWorld().equals(jailCenter.getWorld())) {
                                        escaped = true;
                                    } else {
                                        int r = plugin.getConfigManager().getDefaultJailRadius();
                                        if (r <= 0) r = jail.getRadius();
                                        if (player.getLocation().distance(jailCenter) > r) {
                                            escaped = true;
                                        }
                                    }
                                    if (escaped) {
                                        player.teleport(jailCenter);
                                        player.sendTitle(
                                                plugin.getLanguageManager().getMessage("jail_cannot_escape_title").replace('&', '§'),
                                                plugin.getLanguageManager().getMessage("jail_cannot_escape_subtitle").replace('&', '§'),
                                                5, 40, 10);
                                        player.sendMessage(plugin.getLanguageManager().getPrefix() +
                                                plugin.getLanguageManager().getMessage("jail_area_leave_denied"));
                                    }
                                }
                            }
                        }
                    }

                    // === Actionbar Display ===
                    String msg;
                    if (isBlocksJail(player)) {
                        int remaining = getRemainingBlocks(player);
                        int required = getRequiredBlocks(player);
                        msg = plugin.getLanguageManager().getMessage("jail_actionbar_blocks",
                                "remaining", String.valueOf(remaining),
                                "required", String.valueOf(required));
                    } else {
                        long remainingMs = getRemainingTime(player);
                        long totalSeconds = Math.max(0, remainingMs / 1000);
                        long minutes = totalSeconds / 60;
                        long seconds = totalSeconds % 60;
                        msg = plugin.getLanguageManager().getMessage("jail_actionbar_remaining",
                                "minutes", String.valueOf(minutes),
                                "seconds", String.valueOf(seconds));
                    }
                    player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                            net.md_5.bungee.api.chat.TextComponent.fromLegacyText(
                                    plugin.getLanguageManager().getPrefix() + msg));
                }
            }
        }, 0L, 20L).getTaskId(); // every 1 second
    }

    /**
     * Schedules a block for regeneration after 3 seconds (3000ms).
     * Called instead of individual runTaskLater per block to reduce scheduler overhead.
     */
    public void scheduleBlockRegen(org.bukkit.Location location) {
        regenQueue.add(new QueuedBlockRegen(location, System.currentTimeMillis() + 3000));
        // Start the worker if not already running
        if (regenTaskId == -1) {
            startRegenWorker();
        }
    }

    /**
     * Starts a single repeating task that processes all queued block regenerations.
     * Runs every 10 ticks (0.5 seconds) to check for blocks ready to regenerate.
     * This replaces hundreds of individual runTaskLater calls with ONE task.
     */
    private void startRegenWorker() {
        regenTaskId = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            QueuedBlockRegen entry;
            int processed = 0;
            // Process up to 100 blocks per tick to avoid lag spikes
            while (processed < 100 && (entry = regenQueue.peek()) != null) {
                if (now >= entry.regenTimeMs) {
                    regenQueue.poll(); // remove from queue
                    try {
                        org.bukkit.block.Block block = entry.location.getBlock();
                        if (block.getType() == org.bukkit.Material.AIR) {
                            block.setType(org.bukkit.Material.COBBLESTONE);
                        }
                    } catch (Throwable ignored) {
                    }
                    processed++;
                } else {
                    break; // remaining entries are in the future
                }
            }
        }, 10L, 10L).getTaskId();
    }

    public void stop() {
        if (actionbarTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(actionbarTaskId);
            actionbarTaskId = -1;
        }
        if (regenTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(regenTaskId);
            regenTaskId = -1;
        }
        if (pendingSaveTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(pendingSaveTaskId);
            pendingSaveTaskId = -1;
        }
    }

    /**
     * Reloads jail data from disk. Called during /police reload.
     */
    public void reloadJails() {
        // Save current state first
        saveData();
        // Clear in-memory data
        jails.clear();
        jailedPlayers.clear();
        globalSpawn = null;
        // Reload from file
        if (dataFile.exists()) {
            // Force re-read the YAML file
            org.bukkit.configuration.file.YamlConfiguration freshConfig = YamlConfiguration.loadConfiguration(dataFile);
            // Temporarily replace dataConfig content
            loadDataFromConfig(freshConfig);
        }
    }

    /**
     * Loads data from a specific FileConfiguration (used by reloadJails).
     */
    private void loadDataFromConfig(FileConfiguration config) {
        if (config.contains("jails")) {
            for (String jailName : config.getConfigurationSection("jails").getKeys(false)) {
                String id = config.getString("jails." + jailName + ".id");
                Location location = loadLocationFromConfig(config, "jails." + jailName + ".location");
                Location spawnLocation = loadLocationFromConfig(config, "jails." + jailName + ".spawn");
                int radius = config.getInt("jails." + jailName + ".radius", 10);

                String type = config.getString("jails." + jailName + ".type");
                if (type == null) {
                    boolean mine = config.getBoolean("jails." + jailName + ".mine", false);
                    type = mine ? "BLOCKS" : "TIME";
                }
                Location pos1 = loadLocationFromConfig(config, "jails." + jailName + ".pos1");
                Location pos2 = loadLocationFromConfig(config, "jails." + jailName + ".pos2");

                if (location != null) {
                    JailInfo jailInfo = new JailInfo(jailName, id, location);
                    jailInfo.setSpawnLocation(spawnLocation);
                    jailInfo.setRadius(radius);
                    jailInfo.setType(type);
                    jailInfo.setPos1(pos1);
                    jailInfo.setPos2(pos2);
                    jails.put(jailName, jailInfo);
                }
            }
        }

        if (config.contains("global_spawn")) {
            globalSpawn = loadLocationFromConfig(config, "global_spawn");
        } else if (config.contains("jail_hub")) {
            globalSpawn = loadLocationFromConfig(config, "jail_hub");
        }

        if (config.contains("jailed_players")) {
            for (String uuidString : config.getConfigurationSection("jailed_players").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidString);
                    String jailName = config.getString("jailed_players." + uuidString + ".jail_name");
                    long releaseTime = config.getLong("jailed_players." + uuidString + ".release_time");
                    int requiredBlocks = config.getInt("jailed_players." + uuidString + ".required_blocks", 0);
                    int brokenBlocks = config.getInt("jailed_players." + uuidString + ".broken_blocks", 0);

                    JailData jailData;
                    if (requiredBlocks > 0) {
                        jailData = new JailData(jailName, requiredBlocks);
                        jailData.setBrokenBlocks(brokenBlocks);
                    } else {
                        jailData = new JailData(jailName, releaseTime);
                    }
                    jailedPlayers.put(uuid, jailData);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in jail.yml: " + uuidString);
                }
            }
        }
    }

    private Location loadLocationFromConfig(FileConfiguration config, String path) {
        if (!config.contains(path)) return null;

        String worldName = config.getString(path + ".world");
        double x = config.getDouble(path + ".x");
        double y = config.getDouble(path + ".y");
        double z = config.getDouble(path + ".z");
        float yaw = (float) config.getDouble(path + ".yaw", 0);
        float pitch = (float) config.getDouble(path + ".pitch", 0);

        if (plugin.getServer().getWorld(worldName) != null) {
            return new Location(plugin.getServer().getWorld(worldName), x, y, z, yaw, pitch);
        }
        return null;
    }

    // ========================= Global Spawn (Release Hub) =========================

    /**
     * Sets the global spawn location (where released players teleport).
     * Set by /jail spawn command.
     */
    public void setGlobalSpawn(Location location) {
        this.globalSpawn = location;
        markDirtyAndScheduleSave();
    }

    /**
     * Gets the global spawn location, or null if not set.
     */
    public Location getGlobalSpawn() {
        return globalSpawn;
    }
}
