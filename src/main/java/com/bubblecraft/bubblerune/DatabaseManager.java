package com.bubblecraft.bubblerune;

import org.bukkit.Bukkit;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class DatabaseManager {
    private final BubbleRunePlugin plugin;
    private Connection connection;
    private final File databaseFile;
    private final Object dbLock = new Object();

    public DatabaseManager(BubbleRunePlugin plugin) {
        this.plugin = plugin;
        this.databaseFile = new File(plugin.getDataFolder(), "data.db");
    }

    /**
     * Initialize database connection and create tables
     */
    public void initialize() {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
            
            createTables();
            plugin.getLogger().info("SQLite database initialized successfully!");
            
        } catch (ClassNotFoundException e) {
            plugin.getLogger().severe("SQLite JDBC driver not found!");
            e.printStackTrace();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialize database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Create all necessary database tables
     */
    private void createTables() throws SQLException {
        // Player statistics table
        execute(
            "CREATE TABLE IF NOT EXISTS player_stats (" +
            "uuid TEXT PRIMARY KEY," +
            "player_name TEXT NOT NULL," +
            "total_rolls INTEGER DEFAULT 0," +
            "total_xp_spent INTEGER DEFAULT 0," +
            "total_coins_spent INTEGER DEFAULT 0," +
            "common_rolls INTEGER DEFAULT 0," +
            "uncommon_rolls INTEGER DEFAULT 0," +
            "rare_rolls INTEGER DEFAULT 0," +
            "epic_rolls INTEGER DEFAULT 0," +
            "legendary_rolls INTEGER DEFAULT 0," +
            "special_rolls INTEGER DEFAULT 0," +
            "veryspecial_rolls INTEGER DEFAULT 0," +
            "first_roll_date BIGINT," +
            "last_roll_date BIGINT," +
            "updated_at BIGINT" +
            ")"
        );

        // Individual roll history table
        execute(
            "CREATE TABLE IF NOT EXISTS roll_history (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "uuid TEXT NOT NULL," +
            "player_name TEXT NOT NULL," +
            "tier TEXT NOT NULL," +
            "enchant_id TEXT NOT NULL," +
            "enchant_name TEXT NOT NULL," +
            "enchant_level INTEGER NOT NULL," +
            "xp_cost INTEGER NOT NULL," +
            "coin_cost INTEGER NOT NULL," +
            "location_world TEXT," +
            "location_x REAL," +
            "location_y REAL," +
            "location_z REAL," +
            "timestamp BIGINT NOT NULL," +
            "FOREIGN KEY (uuid) REFERENCES player_stats(uuid)" +
            ")"
        );

        // Daily/Weekly aggregated stats
        execute(
            "CREATE TABLE IF NOT EXISTS daily_stats (" +
            "date TEXT PRIMARY KEY," +
            "total_rolls INTEGER DEFAULT 0," +
            "total_xp_spent INTEGER DEFAULT 0," +
            "total_coins_spent INTEGER DEFAULT 0," +
            "unique_players INTEGER DEFAULT 0," +
            "common_rolls INTEGER DEFAULT 0," +
            "uncommon_rolls INTEGER DEFAULT 0," +
            "rare_rolls INTEGER DEFAULT 0," +
            "epic_rolls INTEGER DEFAULT 0," +
            "legendary_rolls INTEGER DEFAULT 0," +
            "special_rolls INTEGER DEFAULT 0," +
            "veryspecial_rolls INTEGER DEFAULT 0" +
            ")"
        );

        // Create indices for faster queries
        execute("CREATE INDEX IF NOT EXISTS idx_roll_history_uuid ON roll_history(uuid)");
        execute("CREATE INDEX IF NOT EXISTS idx_roll_history_timestamp ON roll_history(timestamp)");
        execute("CREATE INDEX IF NOT EXISTS idx_roll_history_tier ON roll_history(tier)");
        execute("CREATE INDEX IF NOT EXISTS idx_player_stats_total_rolls ON player_stats(total_rolls DESC)");
    }

    /**
     * Execute an SQL statement without returning results
     */
    private void execute(String sql) throws SQLException {
        synchronized (dbLock) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(sql);
            }
        }
    }

    /**
     * Record a rune roll asynchronously
     */
    public CompletableFuture<Void> recordRollAsync(
            UUID playerId, 
            String playerName,
            RuneTier tier,
            String enchantId,
            String enchantName,
            int enchantLevel,
            int xpCost,
            int coinCost,
            org.bukkit.Location location) {
        
        return CompletableFuture.runAsync(() -> {
            try {
                recordRoll(playerId, playerName, tier, enchantId, enchantName, enchantLevel, xpCost, coinCost, location);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to record roll to database", e);
            }
        });
    }

    /**
     * Record a rune roll to the database
     */
    public void recordRoll(
            UUID playerId,
            String playerName,
            RuneTier tier,
            String enchantId,
            String enchantName,
            int enchantLevel,
            int xpCost,
            int coinCost,
            org.bukkit.Location location) throws SQLException {
        synchronized (dbLock) {
            long timestamp = System.currentTimeMillis();
            String today = java.time.LocalDate.now().toString();

            // Insert into roll history
            String insertRoll =
                "INSERT INTO roll_history (uuid, player_name, tier, enchant_id, enchant_name, enchant_level, " +
                "xp_cost, coin_cost, location_world, location_x, location_y, location_z, timestamp) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement stmt = connection.prepareStatement(insertRoll)) {
                stmt.setString(1, playerId.toString());
                stmt.setString(2, playerName);
                stmt.setString(3, tier.name());
                stmt.setString(4, enchantId);
                stmt.setString(5, enchantName);
                stmt.setInt(6, enchantLevel);
                stmt.setInt(7, xpCost);
                stmt.setInt(8, coinCost);
                stmt.setString(9, location != null ? location.getWorld().getName() : null);
                stmt.setDouble(10, location != null ? location.getX() : 0);
                stmt.setDouble(11, location != null ? location.getY() : 0);
                stmt.setDouble(12, location != null ? location.getZ() : 0);
                stmt.setLong(13, timestamp);
                stmt.executeUpdate();
            }

            // Update player stats
            String tierColumn = tier.name().toLowerCase() + "_rolls";
            String updatePlayer =
                "INSERT INTO player_stats (uuid, player_name, total_rolls, total_xp_spent, total_coins_spent, " +
                tierColumn + ", first_roll_date, last_roll_date, updated_at) " +
                "VALUES (?, ?, 1, ?, ?, 1, ?, ?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET " +
                "player_name = excluded.player_name, " +
                "total_rolls = total_rolls + 1, " +
                "total_xp_spent = total_xp_spent + excluded.total_xp_spent, " +
                "total_coins_spent = total_coins_spent + excluded.total_coins_spent, " +
                tierColumn + " = " + tierColumn + " + 1, " +
                "last_roll_date = excluded.last_roll_date, " +
                "updated_at = excluded.updated_at";

            try (PreparedStatement stmt = connection.prepareStatement(updatePlayer)) {
                stmt.setString(1, playerId.toString());
                stmt.setString(2, playerName);
                stmt.setInt(3, xpCost);
                stmt.setInt(4, coinCost);
                stmt.setLong(5, timestamp);
                stmt.setLong(6, timestamp);
                stmt.setLong(7, timestamp);
                stmt.executeUpdate();
            }

            // Update daily stats
            String updateDaily =
                "INSERT INTO daily_stats (date, total_rolls, total_xp_spent, total_coins_spent, unique_players, " +
                tierColumn + ") " +
                "VALUES (?, 1, ?, ?, 1, 1) " +
                "ON CONFLICT(date) DO UPDATE SET " +
                "total_rolls = total_rolls + 1, " +
                "total_xp_spent = total_xp_spent + excluded.total_xp_spent, " +
                "total_coins_spent = total_coins_spent + excluded.total_coins_spent, " +
                tierColumn + " = " + tierColumn + " + 1";

            try (PreparedStatement stmt = connection.prepareStatement(updateDaily)) {
                stmt.setString(1, today);
                stmt.setInt(2, xpCost);
                stmt.setInt(3, coinCost);
                stmt.executeUpdate();
            }
        }
    }

    /**
     * Get player statistics
     */
    public PlayerStats getPlayerStats(UUID playerId) throws SQLException {
        String query = "SELECT * FROM player_stats WHERE uuid = ?";

        synchronized (dbLock) {
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, playerId.toString());
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    return new PlayerStats(
                        UUID.fromString(rs.getString("uuid")),
                        rs.getString("player_name"),
                        rs.getInt("total_rolls"),
                        rs.getInt("total_xp_spent"),
                        rs.getInt("total_coins_spent"),
                        rs.getInt("common_rolls"),
                        rs.getInt("uncommon_rolls"),
                        rs.getInt("rare_rolls"),
                        rs.getInt("epic_rolls"),
                        rs.getInt("legendary_rolls"),
                        rs.getInt("special_rolls"),
                        rs.getInt("veryspecial_rolls"),
                        rs.getLong("first_roll_date"),
                        rs.getLong("last_roll_date")
                    );
                }
            }
        }
        
        return null;
    }

    /**
     * Get top players by total rolls
     */
    public List<PlayerStats> getTopPlayers(int limit) throws SQLException {
        List<PlayerStats> topPlayers = new ArrayList<>();
        String query = "SELECT * FROM player_stats ORDER BY total_rolls DESC LIMIT ?";

        synchronized (dbLock) {
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setInt(1, limit);
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    topPlayers.add(new PlayerStats(
                        UUID.fromString(rs.getString("uuid")),
                        rs.getString("player_name"),
                        rs.getInt("total_rolls"),
                        rs.getInt("total_xp_spent"),
                        rs.getInt("total_coins_spent"),
                        rs.getInt("common_rolls"),
                        rs.getInt("uncommon_rolls"),
                        rs.getInt("rare_rolls"),
                        rs.getInt("epic_rolls"),
                        rs.getInt("legendary_rolls"),
                        rs.getInt("special_rolls"),
                        rs.getInt("veryspecial_rolls"),
                        rs.getLong("first_roll_date"),
                        rs.getLong("last_roll_date")
                    ));
                }
            }
        }
        
        return topPlayers;
    }

    /**
     * Get recent rolls for a player
     */
    public List<RollRecord> getRecentRolls(UUID playerId, int limit) throws SQLException {
        List<RollRecord> rolls = new ArrayList<>();
        String query = "SELECT * FROM roll_history WHERE uuid = ? ORDER BY timestamp DESC LIMIT ?";

        synchronized (dbLock) {
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, playerId.toString());
                stmt.setInt(2, limit);
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    rolls.add(new RollRecord(
                        rs.getInt("id"),
                        UUID.fromString(rs.getString("uuid")),
                        rs.getString("player_name"),
                        RuneTier.valueOf(rs.getString("tier")),
                        rs.getString("enchant_id"),
                        rs.getString("enchant_name"),
                        rs.getInt("enchant_level"),
                        rs.getInt("xp_cost"),
                        rs.getInt("coin_cost"),
                        rs.getLong("timestamp")
                    ));
                }
            }
        }
        
        return rolls;
    }

    /**
     * Get global statistics
     */
    public GlobalStats getGlobalStats() throws SQLException {
        String query = "SELECT SUM(total_rolls) as total, SUM(total_xp_spent) as xp, " +
                      "SUM(total_coins_spent) as coins, COUNT(*) as players FROM player_stats";

        synchronized (dbLock) {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {

                if (rs.next()) {
                    return new GlobalStats(
                        rs.getInt("total"),
                        rs.getInt("xp"),
                        rs.getInt("coins"),
                        rs.getInt("players")
                    );
                }
            }
        }
        
        return new GlobalStats(0, 0, 0, 0);
    }

    /**
     * Get tier distribution statistics
     */
    public Map<RuneTier, Integer> getTierDistribution() throws SQLException {
        Map<RuneTier, Integer> distribution = new EnumMap<>(RuneTier.class);
        
        String query = "SELECT tier, COUNT(*) as count FROM roll_history GROUP BY tier";

        synchronized (dbLock) {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {

                while (rs.next()) {
                    RuneTier tier = RuneTier.valueOf(rs.getString("tier"));
                    distribution.put(tier, rs.getInt("count"));
                }
            }
        }
        
        return distribution;
    }

    /**
     * Get a player's rank based on total rolls.
     * Returns null if the player has no stats.
     */
    public Integer getPlayerRank(UUID playerId) throws SQLException {
        String query =
            "SELECT (SELECT COUNT(*) FROM player_stats ps2 WHERE ps2.total_rolls > ps.total_rolls) + 1 AS rank " +
            "FROM player_stats ps WHERE ps.uuid = ?";

        synchronized (dbLock) {
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, playerId.toString());
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getInt("rank");
                }
            }
        }

        return null;
    }

    /**
     * Close database connection
     */
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                synchronized (dbLock) {
                    connection.close();
                }
                plugin.getLogger().info("Database connection closed.");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error closing database connection", e);
        }
    }

    /**
     * Player statistics data class
     */
    public static class PlayerStats {
        public final UUID uuid;
        public final String playerName;
        public final int totalRolls;
        public final int totalXpSpent;
        public final int totalCoinsSpent;
        public final int commonRolls;
        public final int uncommonRolls;
        public final int rareRolls;
        public final int epicRolls;
        public final int legendaryRolls;
        public final int specialRolls;
        public final int verySpecialRolls;
        public final long firstRollDate;
        public final long lastRollDate;

        public PlayerStats(UUID uuid, String playerName, int totalRolls, int totalXpSpent, int totalCoinsSpent,
                          int commonRolls, int uncommonRolls, int rareRolls, int epicRolls, int legendaryRolls,
                          int specialRolls, int verySpecialRolls, long firstRollDate, long lastRollDate) {
            this.uuid = uuid;
            this.playerName = playerName;
            this.totalRolls = totalRolls;
            this.totalXpSpent = totalXpSpent;
            this.totalCoinsSpent = totalCoinsSpent;
            this.commonRolls = commonRolls;
            this.uncommonRolls = uncommonRolls;
            this.rareRolls = rareRolls;
            this.epicRolls = epicRolls;
            this.legendaryRolls = legendaryRolls;
            this.specialRolls = specialRolls;
            this.verySpecialRolls = verySpecialRolls;
            this.firstRollDate = firstRollDate;
            this.lastRollDate = lastRollDate;
        }
    }

    /**
     * Roll record data class
     */
    public static class RollRecord {
        public final int id;
        public final UUID uuid;
        public final String playerName;
        public final RuneTier tier;
        public final String enchantId;
        public final String enchantName;
        public final int enchantLevel;
        public final int xpCost;
        public final int coinCost;
        public final long timestamp;

        public RollRecord(int id, UUID uuid, String playerName, RuneTier tier, String enchantId,
                         String enchantName, int enchantLevel, int xpCost, int coinCost, long timestamp) {
            this.id = id;
            this.uuid = uuid;
            this.playerName = playerName;
            this.tier = tier;
            this.enchantId = enchantId;
            this.enchantName = enchantName;
            this.enchantLevel = enchantLevel;
            this.xpCost = xpCost;
            this.coinCost = coinCost;
            this.timestamp = timestamp;
        }
    }

    /**
     * Global statistics data class
     */
    public static class GlobalStats {
        public final int totalRolls;
        public final int totalXpSpent;
        public final int totalCoinsSpent;
        public final int uniquePlayers;

        public GlobalStats(int totalRolls, int totalXpSpent, int totalCoinsSpent, int uniquePlayers) {
            this.totalRolls = totalRolls;
            this.totalXpSpent = totalXpSpent;
            this.totalCoinsSpent = totalCoinsSpent;
            this.uniquePlayers = uniquePlayers;
        }
    }
}
