package com.bubblecraft.bubblerune;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class BubbleRunePlaceholderExpansion extends PlaceholderExpansion {
    private final BubbleRunePlugin plugin;

    public BubbleRunePlaceholderExpansion(BubbleRunePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "bubblerune";
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
        
        // Basic stats
        if (params.equalsIgnoreCase("total_rolls")) {
            return String.valueOf(stats.getTotalRolls());
        }
        
        if (params.equalsIgnoreCase("player_rolls")) {
            if (player == null) return "0";
            return String.valueOf(stats.getPlayerRolls(player.getUniqueId()));
        }
        
        if (params.startsWith("tier_")) {
            String tierName = params.substring(5).toUpperCase();
            try {
                RuneTier tier = RuneTier.valueOf(tierName);
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
                    return String.valueOf(stats.getTopPlayerRolls(position));
                } catch (NumberFormatException e) {
                    return "0";
                }
            }
        }
        
        // Player rank
        if (params.equalsIgnoreCase("rank")) {
            if (player == null) return "N/A";
            return String.valueOf(stats.getPlayerRank(player.getUniqueId()));
        }
        
        // Milestones
        if (params.equalsIgnoreCase("next_milestone")) {
            if (player == null) return "0";
            int rolls = stats.getPlayerRolls(player.getUniqueId());
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
            int rolls = stats.getPlayerRolls(player.getUniqueId());
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
            int rolls = stats.getPlayerRolls(player.getUniqueId());
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
                return String.valueOf(stats.getPlayerTierCount(player.getUniqueId(), tier));
            } catch (IllegalArgumentException e) {
                return "0";
            }
        }
        
        // Rarest rune obtained
        if (params.equalsIgnoreCase("rarest_rune")) {
            if (player == null) return "None";
            return stats.getRarestRuneObtained(player.getUniqueId());
        }
        
        // Total XP spent on runes
        if (params.equalsIgnoreCase("total_xp_spent")) {
            if (player == null) return "0";
            return String.valueOf(stats.getTotalXpSpent(player.getUniqueId()));
        }
        
        return null;
    }
}
