package com.policeplugin.managers;

import com.policeplugin.PolicePlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

public class DisplayManager {

	private final PolicePlugin plugin;
	private Objective belowNameObjective;

	public DisplayManager(PolicePlugin plugin) {
		this.plugin = plugin;
	}

	public void start() {
		setupBelowNameObjective();
		updateAllPlayersDisplay();
	}

	public void stop() {
		// nothing to cleanup explicitly
	}

	private void setupBelowNameObjective() {
		if (!plugin.getConfigManager().isDisplayEnabled() || !plugin.getConfigManager().isShowBelowName()) {
			return;
		}
		String objectiveName = plugin.getConfigManager().getBelowNameObjective();
		String objectiveTitle = ChatColor.translateAlternateColorCodes('&', plugin.getConfigManager().getBelowNameTitle());
		
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
			belowNameObjective = Bukkit.getScoreboardManager().getMainScoreboard().registerNewObjective(objectiveName, "dummy", objectiveTitle);
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
		if (!plugin.getConfigManager().isDisplayEnabled()) return;
		for (Player p : Bukkit.getOnlinePlayers()) {
			updatePlayerDisplay(p);
		}
	}

	public void forceRefreshAllDisplays() {
		if (!plugin.getConfigManager().isDisplayEnabled()) return;
		
		// First setup the objectives for all players
		setupBelowNameObjective();
		
		// Then update all player displays
		for (Player p : Bukkit.getOnlinePlayers()) {
			updatePlayerDisplay(p);
		}
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
		if (!plugin.getConfigManager().isDisplayEnabled()) return;
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
			
			// Debug logging
			plugin.getLogger().info("Updated display for " + player.getName() + " - Wanted Level: " + level + 
				", Objective: " + objectiveName + ", Score: " + score.getScore());
		}
	}

	private String buildStars(int level) {
		int max = Math.max(plugin.getConfigManager().getMaxWantedLevel(), level);
		String symbol = plugin.getConfigManager().getStarSymbol();
		String filledColor = plugin.getConfigManager().getStarFilledColor();
		String emptyColor = plugin.getConfigManager().getStarEmptyColor();
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < max; i++) {
			if (i < level) {
				sb.append(filledColor).append(symbol);
			} else {
				sb.append(emptyColor).append(symbol);
			}
		}
		return ChatColor.translateAlternateColorCodes('&', sb.toString());
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
		if (!plugin.getConfigManager().isDisplayEnabled() || !plugin.getConfigManager().isShowBelowName()) return;
		Scoreboard viewerBoard = getOrCreateScoreboard(viewer);
		String objectiveName = plugin.getConfigManager().getBelowNameObjective();
		Objective obj = ensureObjective(viewerBoard, objectiveName, plugin.getConfigManager().getBelowNameTitle());
		Score scoreEntry = obj.getScore(target.getName());
		scoreEntry.setScore(level);
	}

	public void updateTargetForAllViewers(Player target) {
		if (!plugin.getConfigManager().isDisplayEnabled()) return;
		int level = plugin.getWantedManager().getWantedLevel(target);
		for (Player viewer : Bukkit.getOnlinePlayers()) {
			updateBelowNameForViewer(viewer, target, level);
		}
	}

	public void initializeViewerScores(Player viewer) {
		if (!plugin.getConfigManager().isDisplayEnabled() || !plugin.getConfigManager().isShowBelowName()) return;
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


