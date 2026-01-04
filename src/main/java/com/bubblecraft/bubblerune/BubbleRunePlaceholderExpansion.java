package com.bubblecraft.bubblerune;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class BubbleRunePlaceholderExpansion extends PlaceholderExpansion {
    private final BubbleRunePlugin plugin;
    private final String identifier;

    public BubbleRunePlaceholderExpansion(BubbleRunePlugin plugin, String identifier) {
        this.plugin = plugin;
        this.identifier = (identifier == null || identifier.isBlank()) ? "bubblerune" : identifier;
    }

    private PlaceholderStatsCache cache() {
        return plugin.getPlaceholderStatsCache();
    }

    @Override
    public @NotNull String getIdentifier() {
        return identifier;
    }

    @Override
    public @NotNull String getAuthor() {
        return plugin.getDescription().getAuthors().toString();
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        StatsManager stats = plugin.getStatsManager();
        PlaceholderStatsCache cache = cache();
        
        // Basic stats
        if (params.equalsIgnoreCase("total_rolls")) {
            if (cache != null) return String.valueOf(cache.getGlobalTotalRolls());
            return String.valueOf(stats.getTotalRolls());
        }
        
        if (params.equalsIgnoreCase("player_rolls")) {
            if (player == null) return "0";
            if (cache != null) return String.valueOf(cache.getPlayerRolls(player.getUniqueId()));
            return String.valueOf(stats.getPlayerRolls(player.getUniqueId()));
        }
        
        if (params.startsWith("tier_")) {
            String tierName = params.substring(5).toUpperCase();
            try {
                RuneTier tier = RuneTier.valueOf(tierName);
                if (cache != null) return String.valueOf(cache.getGlobalTierCount(tier));
                return String.valueOf(stats.getTierCount(tier));
            } catch (IllegalArgumentException e) {
                return "0";
            }
        }
        
        if (params.equalsIgnoreCase("cooldown")) {
            if (player == null) return "0";
            CooldownManager cooldown = plugin.getCooldownManager();
            return String.valueOf(cooldown.getRemainingCooldown(player.getUniqueId()));
        }
        
        // Leaderboard positions (1-10)
        if (params.startsWith("leaderboard_")) {
            String[] parts = params.split("_");
            if (parts.length == 2) {
                try {
                    int position = Integer.parseInt(parts[1]);
                    if (position < 1 || position > 10) return "N/A";
                    if (cache != null) return cache.getTopPlayerName(position);
                    return stats.getTopPlayerName(position);
                } catch (NumberFormatException e) {
                    return "N/A";
                }
            }
        }
        
        if (params.startsWith("leaderboard_rolls_")) {
            String[] parts = params.split("_");
            if (parts.length == 3) {
                try {
                    int position = Integer.parseInt(parts[2]);
                    if (position < 1 || position > 10) return "0";
                    if (cache != null) return String.valueOf(cache.getTopPlayerRolls(position));
                    return String.valueOf(stats.getTopPlayerRolls(position));
                } catch (NumberFormatException e) {
                    return "0";
                }
            }
        }
        
        // Player rank
        if (params.equalsIgnoreCase("rank")) {
            if (player == null) return "N/A";
            if (cache != null) {
                Integer rank = cache.getPlayerRank(player.getUniqueId());
                return rank != null ? String.valueOf(rank) : "N/A";
            }
            return String.valueOf(stats.getPlayerRank(player.getUniqueId()));
        }
        
        // Milestones
        if (params.equalsIgnoreCase("next_milestone")) {
            if (player == null) return "0";
            int rolls = (cache != null) ? cache.getPlayerRolls(player.getUniqueId()) : stats.getPlayerRolls(player.getUniqueId());
            int[] milestones = {10, 25, 50, 100, 250, 500, 1000, 2500, 5000, 10000};
            for (int milestone : milestones) {
                if (rolls < milestone) {
                    return String.valueOf(milestone);
                }
            }
            return "MAX";
        }
        
        if (params.equalsIgnoreCase("milestone_progress")) {
            if (player == null) return "0";
            int rolls = (cache != null) ? cache.getPlayerRolls(player.getUniqueId()) : stats.getPlayerRolls(player.getUniqueId());
            int[] milestones = {10, 25, 50, 100, 250, 500, 1000, 2500, 5000, 10000};
            for (int milestone : milestones) {
                if (rolls < milestone) {
                    return rolls + "/" + milestone;
                }
            }
            return "MAX";
        }
        
        if (params.equalsIgnoreCase("milestone_percent")) {
            if (player == null) return "0";
            int rolls = (cache != null) ? cache.getPlayerRolls(player.getUniqueId()) : stats.getPlayerRolls(player.getUniqueId());
            int[] milestones = {10, 25, 50, 100, 250, 500, 1000, 2500, 5000, 10000};
            for (int milestone : milestones) {
                if (rolls < milestone) {
                    return String.valueOf((int) ((double) rolls / milestone * 100));
                }
            }
            return "100";
        }
        
        // Weekly quests
        if (params.equalsIgnoreCase("quest_count")) {
            if (player == null) return "0";
            WeeklyQuestManager questManager = plugin.getQuestManager();
            if (questManager == null) return "0";
            int completed = 0;
            for (String questId : questManager.getAllQuestIds()) {
                if (questManager.isCompleted(player.getUniqueId(), questId)) {
                    completed++;
                }
            }
            return completed + "/" + questManager.getAllQuestIds().size();
        }
        
        if (params.equalsIgnoreCase("quest_reset")) {
            WeeklyQuestManager questManager = plugin.getQuestManager();
            if (questManager == null) return "N/A";
            return questManager.getFormattedTimeUntilReset();
        }
        
        if (params.startsWith("quest_progress_")) {
            if (player == null) return "0";
            WeeklyQuestManager questManager = plugin.getQuestManager();
            if (questManager == null) return "0";
            String questId = params.substring(15);
            return String.valueOf(questManager.getProgress(player.getUniqueId(), questId));
        }
        
        if (params.startsWith("quest_complete_")) {
            if (player == null) return "false";
            WeeklyQuestManager questManager = plugin.getQuestManager();
            if (questManager == null) return "false";
            String questId = params.substring(15);
            return String.valueOf(questManager.isCompleted(player.getUniqueId(), questId));
        }
        
        // Tier-specific player stats
        if (params.startsWith("player_tier_")) {
            if (player == null) return "0";
            String tierName = params.substring(12).toUpperCase();
            try {
                RuneTier tier = RuneTier.valueOf(tierName);
                if (cache != null) return String.valueOf(cache.getPlayerTierRolls(player.getUniqueId(), tier));
                return String.valueOf(stats.getPlayerTierCount(player.getUniqueId(), tier));
            } catch (IllegalArgumentException e) {
                return "0";
            }
        }
        
        // Rarest rune obtained
        if (params.equalsIgnoreCase("rarest_rune")) {
            if (player == null) return "None";
            if (cache != null) return cache.getRarestRuneObtained(player.getUniqueId());
            return stats.getRarestRuneObtained(player.getUniqueId());
        }
        
        // Total XP spent on runes
        if (params.equalsIgnoreCase("total_xp_spent")) {
            if (player == null) return "0";
            if (cache != null) return String.valueOf(cache.getPlayerTotalXpSpent(player.getUniqueId()));
            return String.valueOf(stats.getTotalXpSpent(player.getUniqueId()));
        }
        
        return null;
    }
}
