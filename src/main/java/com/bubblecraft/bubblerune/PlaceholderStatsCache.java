package com.bubblecraft.bubblerune;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cached placeholder values that refresh on a timer.
 *
 * Goal: avoid hitting the database for every PlaceholderAPI request while still
 * keeping values reasonably fresh (configurable interval).
 */
public class PlaceholderStatsCache {
    private final BubbleRunePlugin plugin;

    private volatile DatabaseManager.GlobalStats globalStats = new DatabaseManager.GlobalStats(0, 0, 0, 0);
    private volatile Map<RuneTier, Integer> tierDistribution = new EnumMap<>(RuneTier.class);
    private volatile List<DatabaseManager.PlayerStats> topPlayers = Collections.emptyList();

    private final Map<UUID, DatabaseManager.PlayerStats> playerStats = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerRanks = new ConcurrentHashMap<>();

    private volatile long lastRefreshMillis = 0L;

    public PlaceholderStatsCache(BubbleRunePlugin plugin) {
        this.plugin = plugin;
        for (RuneTier tier : RuneTier.values()) {
            tierDistribution.put(tier, 0);
        }
    }

    public long getLastRefreshMillis() {
        return lastRefreshMillis;
    }

    /**
     * Refresh snapshot for global + online-player data.
     * Runs asynchronously.
     */
    public void refresh() {
        DatabaseManager db = plugin.getDatabaseManager();
        if (db == null) {
            return;
        }

        // Collect online UUIDs safely on main thread.
        final List<UUID> online;
        if (Bukkit.isPrimaryThread()) {
            online = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                online.add(p.getUniqueId());
            }
        } else {
            try {
                online = Bukkit.getScheduler().callSyncMethod(plugin, () -> {
                    List<UUID> list = new ArrayList<>();
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        list.add(p.getUniqueId());
                    }
                    return list;
                }).get();
            } catch (Exception e) {
                plugin.getLogger().warning("Placeholder cache online-player snapshot failed: " + e.getMessage());
                return;
            }
        }

        try {
            DatabaseManager.GlobalStats newGlobal = db.getGlobalStats();
            Map<RuneTier, Integer> newDist = db.getTierDistribution();
            List<DatabaseManager.PlayerStats> newTop = db.getTopPlayers(10);

            // Fill missing tiers with 0
            EnumMap<RuneTier, Integer> dist = new EnumMap<>(RuneTier.class);
            for (RuneTier tier : RuneTier.values()) {
                dist.put(tier, newDist.getOrDefault(tier, 0));
            }

            // Compute ranks for online players (DB query per player, but only every refresh interval)
            Map<UUID, Integer> newRanks = new HashMap<>();
            for (UUID uuid : online) {
                Integer rank = db.getPlayerRank(uuid);
                if (rank != null) {
                    newRanks.put(uuid, rank);
                }
            }

            // Cache player stats for online players
            for (UUID uuid : online) {
                DatabaseManager.PlayerStats ps = db.getPlayerStats(uuid);
                if (ps != null) {
                    playerStats.put(uuid, ps);
                } else {
                    playerStats.remove(uuid);
                }
            }

            // Atomically swap volatile snapshots
            globalStats = newGlobal;
            tierDistribution = dist;
            topPlayers = newTop;

            playerRanks.clear();
            playerRanks.putAll(newRanks);

            lastRefreshMillis = System.currentTimeMillis();
        } catch (Exception e) {
            plugin.getLogger().warning("Placeholder cache refresh failed: " + e.getMessage());
        }
    }

    // ---- Global ----

    public int getGlobalTotalRolls() {
        return globalStats.totalRolls;
    }

    public int getGlobalTierCount(RuneTier tier) {
        return tierDistribution.getOrDefault(tier, 0);
    }

    // ---- Leaderboard ----

    public String getTopPlayerName(int position) {
        if (position < 1 || position > 10) return "N/A";
        List<DatabaseManager.PlayerStats> list = topPlayers;
        if (position > list.size()) return "N/A";
        String name = list.get(position - 1).playerName;
        return name != null ? name : "Unknown";
    }

    public int getTopPlayerRolls(int position) {
        if (position < 1 || position > 10) return 0;
        List<DatabaseManager.PlayerStats> list = topPlayers;
        if (position > list.size()) return 0;
        return list.get(position - 1).totalRolls;
    }

    // ---- Player ----

    public int getPlayerRolls(UUID uuid) {
        DatabaseManager.PlayerStats ps = playerStats.get(uuid);
        return ps != null ? ps.totalRolls : 0;
    }

    public int getPlayerTierRolls(UUID uuid, RuneTier tier) {
        DatabaseManager.PlayerStats ps = playerStats.get(uuid);
        if (ps == null) return 0;

        switch (tier) {
            case COMMON: return ps.commonRolls;
            case UNCOMMON: return ps.uncommonRolls;
            case RARE: return ps.rareRolls;
            case EPIC: return ps.epicRolls;
            case LEGENDARY: return ps.legendaryRolls;
            case SPECIAL: return ps.specialRolls;
            case VERYSPECIAL: return ps.verySpecialRolls;
            default: return 0;
        }
    }

    public String getRarestRuneObtained(UUID uuid) {
        DatabaseManager.PlayerStats ps = playerStats.get(uuid);
        if (ps == null) return "None";

        if (ps.verySpecialRolls > 0) return RuneTier.VERYSPECIAL.name();
        if (ps.specialRolls > 0) return RuneTier.SPECIAL.name();
        if (ps.legendaryRolls > 0) return RuneTier.LEGENDARY.name();
        if (ps.epicRolls > 0) return RuneTier.EPIC.name();
        if (ps.rareRolls > 0) return RuneTier.RARE.name();
        if (ps.uncommonRolls > 0) return RuneTier.UNCOMMON.name();
        if (ps.commonRolls > 0) return RuneTier.COMMON.name();
        return "None";
    }

    public int getPlayerTotalXpSpent(UUID uuid) {
        DatabaseManager.PlayerStats ps = playerStats.get(uuid);
        return ps != null ? ps.totalXpSpent : 0;
    }

    public Integer getPlayerRank(UUID uuid) {
        return playerRanks.get(uuid);
    }
}
