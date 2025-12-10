# BubbleRune

A Minecraft 1.21+ Purpur plugin for the BubbleCraft server that adds a rune system with EcoEnchants integration.

## ✨ Features

### Core Mechanics
- **7 Rune Tiers**: Common, Uncommon, Rare, Epic, Legendary, Special, VerySpecial
- **Dual-Currency System**: Costs both XP and BubbleCoins (CoinsEngine integration)
- **Player Choice**: Select which tier to roll from an interactive GUI
- **EcoEnchants Integration**: Runes reveal custom EcoEnchants enchanted books
- **Configurable Everything**: Weights, costs, enchantment pools per tier

### Data Persistence
- **📊 SQLite Database**: All rolls and statistics permanently stored
- **📈 Statistics Tracking**: Detailed player stats, roll history, and leaderboards
- **🏆 Leaderboards**: Server-wide rankings by total rolls
- **📜 Roll History**: View your recent rolls with full details

### Quality of Life
- **⏱️ Cooldown System**: Prevents rune table spam with configurable per-player cooldowns
- **🔊 Sound Effects**: Audio feedback for rolling runes, revealing books, and errors
- **✨ Particle Effects**: Visual enchantment particles at rune tables
- **💎 Glow Effect**: Runes shimmer with an enchantment glow in your inventory

### Advanced Features
- **🔨 Rune Combining**: Combine 3 same-tier runes in an anvil to upgrade to the next tier
- **📍 Multiple Tables**: Support for multiple rune table locations across worlds
- **🔌 PlaceholderAPI**: 25+ placeholders for leaderboards, milestones, quests, and stats
- **🎯 Milestone Rewards**: Automatic rewards at 10 different roll milestones (10, 25, 50, 100, 250, 500, 1000, 2500, 5000, 10000)
- **📅 Weekly Quests**: 6 challenging survival quests that reset every Monday with rare rune rewards
- **🎨 GUI Interface**: Beautiful inventory GUI for tier selection with affordability indicators
- **🎆 Visual Effects**: Fireworks for legendary+ rolls, tier-colored particles, celebration effects
- **📢 Broadcasts**: Server-wide announcements for rare rune rolls
- **👁️ Rune Preview**: See tier before revealing enchantment for added suspense

## 📋 Commands

### Player Commands
- `/bubblerune gui` - Open the tier selection GUI
- `/bubblerune quests` - View weekly quest progress and rewards
- `/bubblerune stats` - View your rune rolling statistics
- `/bubblerune history [limit]` - View your recent roll history (default: 10, max: 50)

### Admin Commands
- `/bubblerune reload` - Reload configuration files
- `/bubblerune settable [name]` - Set a rune table at your location
- `/bubblerune giverune <player> <tier>` - Give a rune to a player
- `/bubblerune testroll` - Test the tier weighting system
- `/bubblerune leaderboard [limit]` - View top rune rollers (default: 10, max: 25)

### Permissions
- `bubblerune.admin` - Access to admin commands
- `bubblerune.gui` - Access to GUI command
- `bubblerune.leaderboard` - View leaderboards

## 💾 Database

BubbleRune uses SQLite to track all rune rolls and player statistics. The database includes:

- **Player Statistics**: Total rolls, XP/coins spent, tier breakdown
- **Roll History**: Individual roll records with timestamps and locations
- **Daily Stats**: Aggregated server-wide statistics per day
- **Leaderboards**: Automatic ranking system

See [DATABASE.md](DATABASE.md) for complete documentation.

## 📊 Placeholders

Requires PlaceholderAPI. All placeholders support leaderboards, milestones, and quest tracking!

### Basic Stats
- `%bubblerune_total_rolls%` - Total runes rolled server-wide
- `%bubblerune_player_rolls%` - Player's total rolls
- `%bubblerune_tier_<tier>%` - Count for specific tier (e.g., `%bubblerune_tier_legendary%`)
- `%bubblerune_cooldown%` - Player's remaining cooldown in seconds

### Leaderboards
- `%bubblerune_leaderboard_1%` through `%bubblerune_leaderboard_10%` - Top 10 player names
- `%bubblerune_leaderboard_rolls_1%` through `%bubblerune_leaderboard_rolls_10%` - Top 10 roll counts
- `%bubblerune_rank%` - Player's current leaderboard rank

### Milestones
- `%bubblerune_next_milestone%` - Next milestone to reach
- `%bubblerune_milestone_progress%` - Progress toward next milestone (e.g., "45/50")
- `%bubblerune_milestone_percent%` - Percentage progress toward next milestone

### Weekly Quests
- `%bubblerune_quest_count%` - Completed quests out of total (e.g., "3/6")
- `%bubblerune_quest_reset%` - Time until quest reset
- `%bubblerune_quest_progress_<quest_id>%` - Progress for specific quest
- `%bubblerune_quest_complete_<quest_id>%` - Whether quest is complete (true/false)

### Advanced Stats
- `%bubblerune_player_tier_<tier>%` - Player's count for specific tier
- `%bubblerune_rarest_rune%` - Rarest rune the player has obtained
- `%bubblerune_total_xp_spent%` - Total XP player has spent on runes

## ⚙️ Configuration

### Configuration Files

BubbleRune uses **two main configuration files**:

1. **`config.yml`** - Main plugin configuration (mechanics, rewards, sounds, particles, quests, milestones)
2. **`runes.yml`** - Rune item appearance (names, lore, custom model data)

### 🎨 Text Formatting Support

**Both Legacy & MiniMessage formats are supported everywhere!**

**Legacy Color Codes (`&`):**
```yaml
message: "&aGreen &l&6Bold Gold &r&7Gray"
```

**MiniMessage Tags (`<>`):**
```yaml
message: "<green>Green <bold><gold>Bold Gold</gold></bold> <gray>Gray</gray>"
```

**Advanced MiniMessage Features:**
- `<gradient:red:blue>Gradient text</gradient>`
- `<rainbow>Rainbow effect!</rainbow>`
- `<hover:show_text:'Tooltip'>Hover me</hover>`
- `<click:run_command:'/cmd'>Clickable</click>`

**Hex RGB Colors (All Formats):**
```yaml
# Simple hex format
message: "&#FF5733Hello &#00FF00World"

# Spaced hex ampersand format
message: "&x&F&F&5&7&3&3Red Text"

# Spaced hex section symbol format  
message: "§x§F§F§5§7§3§3Red Text"

# MiniMessage hex format
message: "<#FF5733>Red Text</color>"
```

### Rune Customization (`runes.yml`)

Customize every aspect of rune item appearance with both formatting styles:

**Legacy Example:**
```yaml
tiers:
  rare:
    name: "&9Rare Rune"
    lore:
      - "&7Right-click to reveal a"
      - "&9Rare &7enchantment!"
```

**MiniMessage Example:**
```yaml
tiers:
  legendary:
    name: "<gradient:gold:yellow><bold>Legendary Rune</bold></gradient>"
    customModelData: 1005
    lore:
      - "<gray>Right-click to reveal a"
      - "<gold><bold>Legendary</bold></gold> <gray>enchantment!"
  
  veryspecial:
    name: "<rainbow><bold>Very Special Rune</bold></rainbow>"
    lore:
      - "<gradient:light_purple:pink>Ultra rare!</gradient>"
```

**Placeholders available in names/lore:**
- `%tier%` - Tier name
- `%enchant%` - Enchantment ID
- `%level%` - Enchantment level
- `%player%` - Player name

### Multiple Rune Tables (`config.yml`)
```yaml
runeTables:
  spawn:
    world: world
    x: 0
    y: 64
    z: 0
  nether:
    world: world_nether
    x: 100
    y: 65
    z: -200
```

### Cooldown System
```yaml
cooldown:
  enabled: true
  seconds: 60
```

### Economy Integration (CoinsEngine)
```yaml
economy:
  bubbleCoinEnabled: true
  bubbleCoinCurrency: "bubblecoin"
  requireCoinsEnginePlugin: true
  
  # Per-tier BubbleCoin costs (fully configurable!)
  bubbleCoinCosts:
    common: 1
    uncommon: 2
    rare: 5
    epic: 10
    legendary: 25
    special: 50
    veryspecial: 100
```

**Features:**
- ✅ Per-tier coin costs (set any tier to 0 for free)
- ✅ Reflection-based integration (no hard dependency)
- ✅ Graceful degradation if CoinsEngine not found
- ✅ Dual-currency system (XP + BubbleCoins)
- ✅ Automatic refunds on errors
- ✅ Backwards compatible with single `bubbleCoinCost` setting

### Effects
```yaml
sounds:
  enabled: true

particles:
  enabled: true
  count: 50
  speed: 0.1

runeItem:
  glow: true  # Makes runes glow with enchantment effect
```

### Rune Combining
```yaml
runeCombining:
  enabled: true
  requiredRunes: 3  # Number of same-tier runes needed to upgrade
```

## 🔧 Build

From the project root:

```powershell
.\gradlew.bat build
```

The compiled plugin jar will be in `build/libs/BubbleRune-1.0.0-SNAPSHOT.jar`.

## 📦 Dependencies

- **Required**: Purpur 1.21+
- **Optional**: EcoEnchants (for custom enchantments)
- **Optional**: PlaceholderAPI (for placeholders)

## 🚀 Installation

1. Download `BubbleRune.jar` from `build/libs/`
2. Place in your server's `plugins/` folder
3. Install EcoEnchants for custom enchantments
4. (Optional) Install PlaceholderAPI for placeholders
5. Restart server
6. Configure enchantment IDs in `config.yml`
7. Set rune table locations with `/bubblerune settable`

## 🎮 How to Use

1. **Set up tables**: Place enchanting tables and register them with `/bubblerune settable`
2. **Roll runes**: Players right-click registered tables and spend XP to get runes
3. **Reveal enchants**: Right-click runes to get enchanted books
4. **Upgrade runes**: Combine 3 same-tier runes in an anvil to upgrade to the next tier

## 🔐 Permissions

- `bubblerune.admin` - Access to all admin commands (default: op)
