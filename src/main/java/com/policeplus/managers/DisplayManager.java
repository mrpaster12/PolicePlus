package com.policeplus.managers;

import com.policeplus.PolicePlus;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DisplayManager {

	private final PolicePlus plugin;
	private Objective belowNameObjective;
	private final Map<Integer, String> starCache = new ConcurrentHashMap<>();
	private String cachedStarSymbol;
	private String cachedFilledColor;
	private String cachedEmptyColor;
	private int cachedMaxLevel = -1;

	private int bossBarUpdateTaskId = -1;

	public DisplayManager(PolicePlus plugin) {
		this.plugin = plugin;
	}

	public void start() {
		setupBelowNameObjective();
		updateAllPlayersDisplay();
		startBossBarUpdater();
	}

	public void stop() {
		stopBossBarUpdater();
		// Clean up any active cop boss bars
		HandcuffManager hm = plugin.getHandcuffManager();
		if (hm != null) {
			for (Player online : Bukkit.getOnlinePlayers()) {
				hm.removeCopBossBar(online);
			}
		}
	}

	/**
	 * Cleans up scoreboard data for a disconnecting player to prevent memory leaks.
	 * Removes their scores from all other players' scoreboards.
	 */
	public void cleanupPlayer(Player player) {
		if (!plugin.getConfigManager().isDisplayEnabled() || !plugin.getConfigManager().isShowBelowName()) {
			return;
		}
		String objectiveName = plugin.getConfigManager().getBelowNameObjective();
		for (Player viewer : Bukkit.getOnlinePlayers()) {
			if (viewer.equals(player)) continue;
			Scoreboard sb = viewer.getScoreboard();
			if (sb == null) continue;
			Objective obj = sb.getObjective(objectiveName);
			if (obj != null) {
				obj.getScore(player.getName()).setScore(0);
			}
		}
	}

	/**
	 * Starts a repeating task that updates cop BossBars with suspect info every second.
	 */
	private void startBossBarUpdater() {
		if (bossBarUpdateTaskId != -1) return;
		bossBarUpdateTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
			HandcuffManager hm = plugin.getHandcuffManager();
			if (hm == null) return;
			for (Player cop : Bukkit.getOnlinePlayers()) {
				UUID suspectUUID = hm.getSuspectForCop(cop);
				if (suspectUUID != null) {
					Player suspect = Bukkit.getPlayer(suspectUUID);
					if (suspect != null && suspect.isOnline()) {
						hm.updateCopBossBar(cop, suspect);
					}
				}
			}
		}, 20L, 20L); // Every 1 second (20 ticks)
	}

	/**
	 * Stops the BossBar update task.
	 */
	private void stopBossBarUpdater() {
		if (bossBarUpdateTaskId != -1) {
			Bukkit.getScheduler().cancelTask(bossBarUpdateTaskId);
			bossBarUpdateTaskId = -1;
		}
	}

	private void setupBelowNameObjective() {
		if (!plugin.getConfigManager().isDisplayEnabled() || !plugin.getConfigManager().isShowBelowName()) {
			return;
		}
		String objectiveName = plugin.getConfigManager().getBelowNameObjective();
		String objectiveTitle = ChatColor.translateAlternateColorCodes('&',
				plugin.getConfigManager().getBelowNameTitle());

		for (Player online : Bukkit.getOnlinePlayers()) {
			Scoreboard sb = getOrCreateScoreboard(online);

			// Get or create the objective
			Objective obj = sb.getObjective(objectiveName);
			if (obj == null) {
				obj = sb.registerNewObjective(objectiveName, "dummy", objectiveTitle);
			}

			// Set the display slot
			obj.setDisplaySlot(DisplaySlot.BELOW_NAME);
		}

		// Store reference to main scoreboard objective for global access
		belowNameObjective = Bukkit.getScoreboardManager().getMainScoreboard().getObjective(objectiveName);
		if (belowNameObjective == null) {
			belowNameObjective = Bukkit.getScoreboardManager().getMainScoreboard().registerNewObjective(objectiveName,
					"dummy", objectiveTitle);
		}
	}

	private Scoreboard getOrCreateScoreboard(Player player) {
		Scoreboard sb = player.getScoreboard();
		if (sb == null || sb == Bukkit.getScoreboardManager().getMainScoreboard()) {
			sb = Bukkit.getScoreboardManager().getNewScoreboard();
			player.setScoreboard(sb);
		}
		return sb;
	}

	public void updateAllPlayersDisplay() {
		if (!plugin.getConfigManager().isDisplayEnabled())
			return;
		for (Player p : Bukkit.getOnlinePlayers()) {
			updatePlayerDisplay(p);
		}
	}

	public void forceRefreshAllDisplays() {
		if (!plugin.getConfigManager().isDisplayEnabled())
			return;

		// Clear star cache so display settings are re-read from config
		clearStarCache();

		// First setup the objectives for all players
		setupBelowNameObjective();

		// Then update all player displays
		for (Player p : Bukkit.getOnlinePlayers()) {
			updatePlayerDisplay(p);
		}
	}

	/**
	 * Clears the star rendering cache. Should be called when display config changes.
	 */
	public void clearStarCache() {
		starCache.clear();
		cachedMaxLevel = -1;
		cachedStarSymbol = null;
		cachedFilledColor = null;
		cachedEmptyColor = null;
	}

	/**
	 * Restarts the BossBar updater task. Called during reload to pick up new intervals.
	 */
	public void restartBossBarUpdater() {
		stopBossBarUpdater();
		startBossBarUpdater();
	}

	public void debugPlayerDisplay(Player player) {
		if (!plugin.getConfigManager().isDisplayEnabled()) {
			player.sendMessage("§cDisplay is disabled in config");
			return;
		}

		int wantedLevel = plugin.getWantedManager().getWantedLevel(player);
		player.sendMessage("§e=== Display Debug Info ===");
		player.sendMessage("§7Wanted Level: §c" + wantedLevel);
		player.sendMessage("§7Display Mode: §e" + plugin.getConfigManager().getDisplayMode());
		player.sendMessage("§7Show TabList: §e" + plugin.getConfigManager().isShowTabList());
		player.sendMessage("§7Show Below Name: §e" + plugin.getConfigManager().isShowBelowName());

		if (plugin.getConfigManager().isShowBelowName()) {
			Scoreboard sb = player.getScoreboard();
			String objectiveName = plugin.getConfigManager().getBelowNameObjective();
			Objective obj = sb.getObjective(objectiveName);

			if (obj != null) {
				player.sendMessage("§7Below Name Objective: §a" + obj.getName());
				player.sendMessage("§7Below Name Title: §e" + obj.getDisplayName());
				Score score = obj.getScore(player.getName());
				player.sendMessage("§7Current Score: §e" + score.getScore());
			} else {
				player.sendMessage("§7Below Name Objective: §cNOT FOUND");
			}
		}
	}

	public boolean verifyDisplayState(Player player) {
		if (!plugin.getConfigManager().isDisplayEnabled() || !plugin.getConfigManager().isShowBelowName()) {
			return false;
		}

		Scoreboard sb = player.getScoreboard();
		String objectiveName = plugin.getConfigManager().getBelowNameObjective();
		Objective obj = sb.getObjective(objectiveName);

		if (obj == null) {
			return false;
		}

		Score score = obj.getScore(player.getName());
		int expectedLevel = plugin.getWantedManager().getWantedLevel(player);

		return score.getScore() == expectedLevel;
	}

	public void updatePlayerDisplay(Player player) {
		if (!plugin.getConfigManager().isDisplayEnabled())
			return;
		int level = plugin.getWantedManager().getWantedLevel(player);

		// Update tablist name
		if (plugin.getConfigManager().isShowTabList()) {
			String suffix;
			if (plugin.getConfigManager().getDisplayMode().equalsIgnoreCase("stars")) {
				suffix = plugin.getConfigManager().getTablistFormatStars().replace("{stars}", buildStars(level));
			} else {
				suffix = plugin.getConfigManager().getTablistFormatNumber().replace("{level}", String.valueOf(level));
			}
			String baseName = player.getName();
			String formatted = ChatColor.translateAlternateColorCodes('&', baseName + suffix);
			player.setPlayerListName(formatted);
		}

		// Update below-name objective (numbers only)
		if (plugin.getConfigManager().isShowBelowName()) {
			Scoreboard sb = getOrCreateScoreboard(player);
			String objectiveName = plugin.getConfigManager().getBelowNameObjective();

			// Get or create the objective
			Objective obj = sb.getObjective(objectiveName);
			if (obj == null) {
				// Create new objective if it doesn't exist
				obj = sb.registerNewObjective(objectiveName, "dummy",
						ChatColor.translateAlternateColorCodes('&', plugin.getConfigManager().getBelowNameTitle()));
			}

			// Set the display slot
			obj.setDisplaySlot(DisplaySlot.BELOW_NAME);

			// Set the score for this player
			Score score = obj.getScore(player.getName());
			score.setScore(level);

		}
	}

	private String buildStars(int level) {
		// Return cached result if available and configuration hasn't changed
		if (starCache.containsKey(level) &&
				cachedMaxLevel == plugin.getConfigManager().getMaxWantedLevel() &&
				cachedStarSymbol != null) {
			return starCache.get(level);
		}

		// Update cache if configuration changed
		int max = Math.max(plugin.getConfigManager().getMaxWantedLevel(), level);
		cachedMaxLevel = plugin.getConfigManager().getMaxWantedLevel();
		cachedStarSymbol = plugin.getConfigManager().getStarSymbol();
		cachedFilledColor = plugin.getConfigManager().getStarFilledColor();
		cachedEmptyColor = plugin.getConfigManager().getStarEmptyColor();

		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < max; i++) {
			if (i < level) {
				sb.append(cachedFilledColor).append(cachedStarSymbol);
			} else {
				sb.append(cachedEmptyColor).append(cachedStarSymbol);
			}
		}
		String result = ChatColor.translateAlternateColorCodes('&', sb.toString());
		starCache.put(level, result);
		return result;
	}

	private Objective ensureObjective(Scoreboard scoreboard, String objectiveName, String title) {
		Objective obj = scoreboard.getObjective(objectiveName);
		if (obj == null) {
			obj = scoreboard.registerNewObjective(objectiveName, "dummy",
					ChatColor.translateAlternateColorCodes('&', title));
		}
		obj.setDisplaySlot(DisplaySlot.BELOW_NAME);
		return obj;
	}

	public void updateBelowNameForViewer(Player viewer, Player target, int level) {
		if (!plugin.getConfigManager().isDisplayEnabled() || !plugin.getConfigManager().isShowBelowName())
			return;
		Scoreboard viewerBoard = getOrCreateScoreboard(viewer);
		String objectiveName = plugin.getConfigManager().getBelowNameObjective();
		Objective obj = ensureObjective(viewerBoard, objectiveName, plugin.getConfigManager().getBelowNameTitle());
		Score scoreEntry = obj.getScore(target.getName());
		scoreEntry.setScore(level);
	}

	public void updateTargetForAllViewers(Player target) {
		if (!plugin.getConfigManager().isDisplayEnabled())
			return;
		int level = plugin.getWantedManager().getWantedLevel(target);
		for (Player viewer : Bukkit.getOnlinePlayers()) {
			updateBelowNameForViewer(viewer, target, level);
		}
	}

	public void initializeViewerScores(Player viewer) {
		if (!plugin.getConfigManager().isDisplayEnabled() || !plugin.getConfigManager().isShowBelowName())
			return;
		Scoreboard viewerBoard = getOrCreateScoreboard(viewer);
		String objectiveName = plugin.getConfigManager().getBelowNameObjective();
		Objective obj = ensureObjective(viewerBoard, objectiveName, plugin.getConfigManager().getBelowNameTitle());
		for (Player target : Bukkit.getOnlinePlayers()) {
			int level = plugin.getWantedManager().getWantedLevel(target);
			Score scoreEntry = obj.getScore(target.getName());
			scoreEntry.setScore(level);
		}
	}
}
