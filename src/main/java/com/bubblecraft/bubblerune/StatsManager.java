package com.bubblecraft.bubblerune;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class StatsManager {
    private final Map<UUID, Integer> playerRolls = new ConcurrentHashMap<>();
    private final Map<RuneTier, Integer> tierCounts = new EnumMap<>(RuneTier.class);
    private final Map<UUID, Map<RuneTier, Integer>> playerTierCounts = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerXpSpent = new ConcurrentHashMap<>();
    private int totalRolls = 0;

    public StatsManager() {
        for (RuneTier tier : RuneTier.values()) {
            tierCounts.put(tier, 0);
        }
    }

    public void recordRoll(UUID playerId, RuneTier tier) {
        int previousRolls = playerRolls.getOrDefault(playerId, 0);
        playerRolls.merge(playerId, 1, Integer::sum);
        tierCounts.merge(tier, 1, Integer::sum);
        totalRolls++;
        
        // Track per-player tier counts
        playerTierCounts.computeIfAbsent(playerId, k -> new EnumMap<>(RuneTier.class))
                       .merge(tier, 1, Integer::sum);
    }
    
    public boolean shouldTriggerMilestone(UUID playerId, int milestone) {
        int currentRolls = playerRolls.getOrDefault(playerId, 0);
        int previousRolls = currentRolls - 1;
        return currentRolls == milestone && previousRolls < milestone;
    }
    
    public void recordXpSpent(UUID playerId, int xp) {
        playerXpSpent.merge(playerId, xp, Integer::sum);
    }

    public int getPlayerRolls(UUID playerId) {
        return playerRolls.getOrDefault(playerId, 0);
    }

    public int getTotalRolls() {
        return totalRolls;
    }

    public int getTierCount(RuneTier tier) {
        return tierCounts.getOrDefault(tier, 0);
    }
    
    public int getPlayerTierCount(UUID playerId, RuneTier tier) {
        return playerTierCounts.getOrDefault(playerId, Collections.emptyMap())
                              .getOrDefault(tier, 0);
    }
    
    public int getTotalXpSpent(UUID playerId) {
        return playerXpSpent.getOrDefault(playerId, 0);
    }
    
    // Leaderboard methods
    public List<Map.Entry<UUID, Integer>> getTopPlayers(int limit) {
        return playerRolls.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    public String getTopPlayerName(int position) {
        List<Map.Entry<UUID, Integer>> top = getTopPlayers(10);
        if (position < 1 || position > top.size()) return "N/A";
        
        UUID playerId = top.get(position - 1).getKey();
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        return player.getName() != null ? player.getName() : "Unknown";
    }
    
    public int getTopPlayerRolls(int position) {
        List<Map.Entry<UUID, Integer>> top = getTopPlayers(10);
        if (position < 1 || position > top.size()) return 0;
        return top.get(position - 1).getValue();
    }
    
    public int getPlayerRank(UUID playerId) {
        List<Map.Entry<UUID, Integer>> sorted = playerRolls.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .collect(Collectors.toList());
        
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).getKey().equals(playerId)) {
                return i + 1;
            }
        }
        return sorted.size() + 1;
    }
    
    public String getRarestRuneObtained(UUID playerId) {
        Map<RuneTier, Integer> playerTiers = playerTierCounts.getOrDefault(playerId, Collections.emptyMap());
        
        // Check from rarest to most common
        RuneTier[] tiers = {RuneTier.VERYSPECIAL, RuneTier.SPECIAL, RuneTier.LEGENDARY, 
                           RuneTier.EPIC, RuneTier.RARE, RuneTier.UNCOMMON, RuneTier.COMMON};
        
        for (RuneTier tier : tiers) {
            if (playerTiers.getOrDefault(tier, 0) > 0) {
                return tier.name();
            }
        }
        
        return "None";
    }

    public void reset() {
        playerRolls.clear();
        tierCounts.clear();
        playerTierCounts.clear();
        playerXpSpent.clear();
        totalRolls = 0;
        for (RuneTier tier : RuneTier.values()) {
            tierCounts.put(tier, 0);
        }
    }
}
