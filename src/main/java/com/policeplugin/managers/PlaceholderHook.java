package com.policeplugin.managers;

import com.policeplugin.PolicePlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
// Removed external annotations to avoid dependency

public class PlaceholderHook extends PlaceholderExpansion {

	private final PolicePlugin plugin;

	public PlaceholderHook(PolicePlugin plugin) {
		this.plugin = plugin;
	}

	@Override
	public String getIdentifier() {
		return "police";
	}

	@Override
	public String getAuthor() {
		return String.join(", ", plugin.getDescription().getAuthors());
	}

	@Override
	public String getVersion() {
		return plugin.getDescription().getVersion();
	}

	@Override
	public boolean persist() {
		return true;
	}

	@Override
	public String onPlaceholderRequest(Player player, String params) {
		if (player == null) return "";
		int level = plugin.getWantedManager().getWantedLevel(player);
		switch (params.toLowerCase()) {
			case "wanted_level":
				return String.valueOf(level);
			case "wanted_stars":
				String symbol = plugin.getConfigManager().getStarSymbol();
				String filled = plugin.getConfigManager().getStarFilledColor();
				String empty = plugin.getConfigManager().getStarEmptyColor();
				int max = Math.max(level, plugin.getConfigManager().getMaxWantedLevel());
				StringBuilder sb = new StringBuilder();
				for (int i = 0; i < max; i++) {
					if (i < level) sb.append(filled).append(symbol);
					else sb.append(empty).append(symbol);
				}
				return org.bukkit.ChatColor.translateAlternateColorCodes('&', sb.toString());
			default:
				return "";
		}
	}
}


