package com.bubblecraft.bubblerune;

import org.bukkit.entity.Player;

/**
 * Utilities for working with Minecraft experience points.
 *
 * Bukkit's Player#getTotalExperience and setTotalExperience can become desynced
 * from the level/exp bar in some cases. These helpers compute/set total XP
 * based on the player's level and exp progress.
 */
public final class ExperienceUtil {
    private ExperienceUtil() {}

    public static int getTotalExperience(Player player) {
        if (player == null) return 0;
        int level = Math.max(0, player.getLevel());
        float progress = player.getExp();
        if (progress < 0f) progress = 0f;
        if (progress > 1f) progress = 1f;

        int base = getXpAtLevel(level);
        int toNext = getXpToNextLevel(level);
        int within = Math.round(progress * toNext);
        long total = (long) base + within;
        if (total < 0L) return 0;
        if (total > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) total;
    }

    public static void setTotalExperience(Player player, int totalExperience) {
        if (player == null) return;
        int total = Math.max(0, totalExperience);

        // Reset first to avoid additive weirdness.
        player.setExp(0f);
        player.setLevel(0);
        player.setTotalExperience(0);

        if (total == 0) return;

        int level = findLevelForTotalXp(total);
        int base = getXpAtLevel(level);
        int remainder = Math.max(0, total - base);
        int toNext = getXpToNextLevel(level);
        float progress = toNext > 0 ? (remainder / (float) toNext) : 0f;
        if (progress < 0f) progress = 0f;
        if (progress > 1f) progress = 1f;

        player.setLevel(level);
        player.setExp(progress);
        player.setTotalExperience(total);
    }

    private static int findLevelForTotalXp(int total) {
        // Find upper bound.
        int low = 0;
        int high = 1;
        while (high < 5000 && getXpAtLevel(high) <= total) {
            low = high;
            high *= 2;
        }
        if (high > 5000) high = 5000;

        // Binary search for largest level with xpAtLevel(level) <= total.
        int left = low;
        int right = high;
        while (left < right) {
            int mid = left + ((right - left + 1) / 2);
            int xpAtMid = getXpAtLevel(mid);
            if (xpAtMid <= total) {
                left = mid;
            } else {
                right = mid - 1;
            }
        }
        return left;
    }

    private static int getXpToNextLevel(int level) {
        if (level <= 15) return 2 * level + 7;
        if (level <= 30) return 5 * level - 38;
        return 9 * level - 158;
    }

    private static int getXpAtLevel(int level) {
        if (level <= 16) {
            return level * level + 6 * level;
        }
        if (level <= 31) {
            long l = level;
            long xp = (5L * l * l - 81L * l + 720L) / 2L;
            if (xp < 0L) return 0;
            if (xp > Integer.MAX_VALUE) return Integer.MAX_VALUE;
            return (int) xp;
        }
        long l = level;
        long xp = (9L * l * l - 325L * l + 4440L) / 2L;
        if (xp < 0L) return 0;
        if (xp > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) xp;
    }
}
