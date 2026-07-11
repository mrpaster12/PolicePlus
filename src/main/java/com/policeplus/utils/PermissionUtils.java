package com.policeplus.utils;

import org.bukkit.entity.Player;

/**
 * Dual-layer permission utility for PolicePlus.
 * A player passes a check if they have either:
 *   - The specific granular permission node, OR
 *   - The master cop permission (policeplus.police), OR
 *   - The admin permission (policeplus.admin), OR
 *   - Operator status.
 */
public class PermissionUtils {

    /**
     * Checks if a player has a specific granular permission OR any master/admin/op permission.
     *
     * @param player       the player to check
     * @param granularNode the specific permission node (e.g., "policeplus.handcuff.cuff")
     * @return true if the player has the granular node or any elevated permission
     */
    public static boolean hasPolicePermission(Player player, String granularNode) {
        return player.hasPermission(granularNode)
            || player.hasPermission("policeplus.police")
            || player.hasPermission("policeplus.admin")
            || player.isOp();
    }
}