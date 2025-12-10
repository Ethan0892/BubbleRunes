# BubbleRune Database System

## Overview
BubbleRune uses SQLite to persistently track all rune rolls, player statistics, and historical data. The database is automatically created in the plugin's data folder as `data.db`.

## Database Schema

### `player_stats` Table
Stores aggregate statistics for each player.

| Column | Type | Description |
|--------|------|-------------|
| uuid | TEXT | Player UUID (Primary Key) |
| player_name | TEXT | Player's current name |
| total_rolls | INTEGER | Total number of runes rolled |
| total_xp_spent | INTEGER | Total XP spent on rolls |
| total_coins_spent | INTEGER | Total BubbleCoins spent |
| common_rolls | INTEGER | Number of Common tier rolls |
| uncommon_rolls | INTEGER | Number of Uncommon tier rolls |
| rare_rolls | INTEGER | Number of Rare tier rolls |
| epic_rolls | INTEGER | Number of Epic tier rolls |
| legendary_rolls | INTEGER | Number of Legendary tier rolls |
| special_rolls | INTEGER | Number of Special tier rolls |
| veryspecial_rolls | INTEGER | Number of Very Special tier rolls |
| first_roll_date | BIGINT | Timestamp of first roll |
| last_roll_date | BIGINT | Timestamp of most recent roll |
| updated_at | BIGINT | Last update timestamp |

### `roll_history` Table
Stores individual roll records with full details.

| Column | Type | Description |
|--------|------|-------------|
| id | INTEGER | Auto-incrementing ID (Primary Key) |
| uuid | TEXT | Player UUID |
| player_name | TEXT | Player name at time of roll |
| tier | TEXT | Tier rolled (COMMON, UNCOMMON, etc.) |
| enchant_id | TEXT | Enchantment identifier |
| enchant_name | TEXT | Human-readable enchant name |
| enchant_level | INTEGER | Enchantment level |
| xp_cost | INTEGER | XP cost for this roll |
| coin_cost | INTEGER | BubbleCoin cost for this roll |
| location_world | TEXT | World where roll occurred |
| location_x | REAL | X coordinate |
| location_y | REAL | Y coordinate |
| location_z | REAL | Z coordinate |
| timestamp | BIGINT | When the roll occurred |

### `daily_stats` Table
Aggregated daily statistics for server-wide tracking.

| Column | Type | Description |
|--------|------|-------------|
| date | TEXT | Date in YYYY-MM-DD format (Primary Key) |
| total_rolls | INTEGER | Total rolls on this date |
| total_xp_spent | INTEGER | Total XP spent |
| total_coins_spent | INTEGER | Total coins spent |
| unique_players | INTEGER | Number of unique players |
| common_rolls | INTEGER | Common tier rolls |
| uncommon_rolls | INTEGER | Uncommon tier rolls |
| rare_rolls | INTEGER | Rare tier rolls |
| epic_rolls | INTEGER | Epic tier rolls |
| legendary_rolls | INTEGER | Legendary tier rolls |
| special_rolls | INTEGER | Special tier rolls |
| veryspecial_rolls | INTEGER | Very Special tier rolls |

## Commands

### Player Commands

#### `/bubblerune stats`
View your personal rune rolling statistics.
- **Permission:** None required
- **Shows:** Total rolls, XP/coins spent, tier breakdown

#### `/bubblerune history [limit]`
View your recent rune rolls.
- **Permission:** None required
- **Arguments:** 
  - `limit` (optional): Number of records to show (default: 10, max: 50)
- **Shows:** Recent rolls with tier, enchant, costs, and time

### Admin Commands

#### `/bubblerune leaderboard [limit]`
View top rune rollers on the server.
- **Permission:** `bubblerune.leaderboard`
- **Arguments:**
  - `limit` (optional): Number of players to show (default: 10, max: 25)
- **Aliases:** `/bubblerune top`
- **Shows:** Top players by total rolls with XP spent

## API Usage

### Getting Player Statistics

```java
DatabaseManager db = plugin.getDatabaseManager();
DatabaseManager.PlayerStats stats = db.getPlayerStats(playerUuid);

if (stats != null) {
    int totalRolls = stats.totalRolls;
    int legendaryRolls = stats.legendaryRolls;
    // ... use stats
}
```

### Recording a Roll

Rolls are automatically recorded when a player uses the rune table. The recording happens asynchronously to avoid blocking the main thread.

```java
plugin.getDatabaseManager().recordRollAsync(
    playerUuid,
    playerName,
    tier,
    enchantId,
    enchantName,
    enchantLevel,
    xpCost,
    coinCost,
    location
);
```

### Getting Recent Rolls

```java
List<DatabaseManager.RollRecord> rolls = db.getRecentRolls(playerUuid, 10);

for (DatabaseManager.RollRecord roll : rolls) {
    System.out.println(roll.tier + " - " + roll.enchantName);
}
```

### Getting Top Players

```java
List<DatabaseManager.PlayerStats> topPlayers = db.getTopPlayers(10);

for (DatabaseManager.PlayerStats player : topPlayers) {
    System.out.println(player.playerName + ": " + player.totalRolls);
}
```

### Getting Global Statistics

```java
DatabaseManager.GlobalStats global = db.getGlobalStats();
System.out.println("Total rolls: " + global.totalRolls);
System.out.println("Unique players: " + global.uniquePlayers);
```

### Getting Tier Distribution

```java
Map<RuneTier, Integer> distribution = db.getTierDistribution();
for (Map.Entry<RuneTier, Integer> entry : distribution.entrySet()) {
    System.out.println(entry.getKey() + ": " + entry.getValue());
}
```

## Performance Considerations

1. **Async Operations**: All database writes happen asynchronously to prevent lag
2. **Connection Pooling**: Single connection is maintained and reused
3. **Indices**: Optimized indices on frequently queried columns
4. **Batch Updates**: Roll recording uses upsert operations to minimize queries

## Data Migration

The database is automatically created on first run. No migration is needed from the old in-memory `StatsManager` system - both systems run in parallel.

## Backup Recommendations

The database file is located at:
```
plugins/BubbleRune/data.db
```

To backup:
1. Stop the server (or ensure no active transactions)
2. Copy `data.db` to a safe location
3. Optionally copy `data.db-journal` if it exists

To restore:
1. Stop the server
2. Replace `data.db` with your backup
3. Start the server

## Troubleshooting

### Database Locked Errors
- Ensure only one server instance is running
- Check file permissions on `data.db`
- Restart the server to clear stale locks

### Missing Statistics
- Verify database file exists and has write permissions
- Check console for SQL errors during roll recording
- Use `/bubblerune stats` to verify data is being recorded

### Performance Issues
- Database operations are async and should not cause lag
- If experiencing issues, check disk I/O performance
- Consider moving plugin folder to faster storage (SSD)

## Schema Versioning

Current schema version: **1.0.0**

Future updates may include automatic schema migrations for backwards compatibility.
