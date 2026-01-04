# BubbleRune - Advanced Rune System with Economy & Statistics

**A feature-rich enchantment rune plugin with tier selection, dual-currency economy, SQLite statistics tracking, and extensive customization.**

---

## 🎯 Overview

BubbleRune transforms the traditional enchantment experience into an engaging progression system where players use **enchantment tables** as "Rune Tables" to roll for tiered rune items. Each rune can be revealed to obtain powerful EcoEnchants custom enchantments, creating a rewarding gameplay loop that encourages exploration, grinding, and strategic resource management.

Unlike simple enchantment plugins, BubbleRune features:
- **Player choice** - Select which tier to roll from an interactive GUI
- **Dual-currency economy** - Costs both XP and BubbleCoins (CoinsEngine integration)
- **Persistent statistics** - SQLite database tracks every roll, player stats, and leaderboards
- **Progression systems** - Weekly quests, milestones, and rune combining mechanics
- **Visual polish** - Tier-specific particles, sounds, fireworks, and celebration effects

Perfect for **survival servers**, **RPG servers**, and **economy-focused communities** looking to add depth to their enchantment system.

---

## ✨ Key Features

### 🎲 Tier-Based Rune System
**7 Distinct Tiers:** Common → Uncommon → Rare → Epic → Legendary → Special → Very Special

Players interact with designated enchantment tables to open a GUI showing all 7 tiers. Each tier:
- Has configurable XP costs (min/max ranges for variation)
- Has configurable BubbleCoin costs (separate per tier)
- Shows affordability indicators (colored wool when affordable, gray glass when not)
- Displays cost ranges, enchant counts, and player balances
- Glows when the player can afford it

**Example Progression:**
```
Common: 1,000-2,000 XP + 1 coin → Basic enchants
Uncommon: 3,000-5,000 XP + 2 coins → Better enchants
Rare: 7,000-10,000 XP + 5 coins → Powerful enchants
Epic: 15,000-25,000 XP + 10 coins → Very powerful
Legendary: 50,000-75,000 XP + 25 coins → Rare enchants
Special: 100,000-150,000 XP + 50 coins → Ultra rare
Very Special: 250,000-500,000 XP + 100 coins → Exclusive enchants
```

### 💰 Dual-Currency Economy System
**Seamless CoinsEngine Integration** (optional but recommended)

Each tier can have its own BubbleCoin cost, creating meaningful economic decisions:
- **Low tiers** (1-5 coins) - Accessible for new players
- **High tiers** (25-100 coins) - Require dedicated farming/trading
- **Configurable per tier** - Set any tier to 0 for XP-only rolling
- **Automatic refunds** - If rune creation fails, both XP and coins are refunded
- **Graceful degradation** - Plugin works without CoinsEngine (disables coin costs)

The dual-currency system prevents XP-only farming and integrates with your existing economy, making runes a valuable server commodity.

### 📊 SQLite Database & Statistics
**Comprehensive Data Tracking**

Every rune roll is permanently stored with:
- Player UUID and name
- Tier selected
- Enchantment details (ID, name, level)
- Costs paid (XP + coins)
- Location (world + coordinates)
- Timestamp

**Three Database Tables:**
1. **player_stats** - Aggregate statistics per player (total rolls, XP/coins spent, tier breakdown)
2. **roll_history** - Individual roll records for detailed history
3. **daily_stats** - Server-wide daily aggregations for analytics

**Players Can View:**
- `/bubblerune stats` - Personal statistics (total rolls, tier distribution, resources spent)
- `/bubblerune history [limit]` - Recent roll history with timestamps
- `/bubblerune leaderboard` - Top rollers on the server

**Admins Get:**
- Persistent data across server restarts
- Player engagement metrics
- Economy health monitoring (XP/coin sinks)
- Tier distribution analytics

### 🎨 Interactive GUI System
**Beautiful Tier Selection Interface**

When players interact with a rune table, they see a 27-slot inventory GUI:
- **7 tier buttons** (slots 10-16) arranged in a row
- **Color-coded materials** per tier (white, lime, blue, red, orange, cyan, magenta wool)
- **Info button** (slot 22) showing current XP and coin balance
- **Visual feedback:**
  - ✨ Glow effect on affordable tiers
  - ⚫ Gray glass for unaffordable tiers
  - 🔴 Red glass when on cooldown
- **Detailed lore** showing:
  - XP cost range
  - Coin cost
  - Current balance
  - Number of possible enchants

### 🔨 Rune Combining System
**Upgrade Lower Tiers to Higher Ones**

Players can combine **3 runes of the same tier** in an anvil to create **1 rune of the next tier up**:
```
3x Common → 1x Uncommon
3x Uncommon → 1x Rare
3x Rare → 1x Epic
... and so on
```

**Features:**
- Configurable rune requirements (default: 3)
- Works in vanilla anvils (no custom GUIs needed)
- Preserves enchantments if runes have already been revealed
- Success messages with tier information
- Can be disabled if you prefer pure rolling

This creates an alternate progression path and adds value to low-tier runes.

### 📅 Weekly Quest System
**6 Challenging Survival Quests**

Quests reset every Monday at midnight, encouraging consistent engagement:

**Quest Types:**
1. **Roll Master** - Roll X runes of any tier
2. **Rare Collector** - Roll X rare+ tier runes
3. **XP Spender** - Spend X total XP on runes
4. **Legendary Hunter** - Roll X legendary+ tier runes
5. **Rune Hoarder** - Accumulate X unrevealed runes in inventory
6. **High Roller** - Roll 1 very special tier rune

**Rewards:**
- Configurable tier runes (default: rare/epic/legendary)
- Automatic reward delivery on completion
- PlaceholderAPI integration for external tracking
- View with `/bubblerune quests`

Perfect for weekly events, scoreboard competitions, or seasonal challenges.

### 🎯 Milestone Rewards
**Automatic Rewards for Total Rolls**

Players receive rewards at **10 milestone thresholds**:
- 10, 25, 50, 100, 250, 500, 1,000, 2,500, 5,000, 10,000 rolls

**Configurable Rewards:**
- Tier runes (guaranteed rewards)
- Custom commands (economy rewards, titles, permissions)
- Celebratory messages and effects
- Milestone tracking via PlaceholderAPI

Encourages long-term engagement and creates memorable achievement moments.

### 🎆 Visual & Audio Effects
**Polished Player Experience**

**Tier-Specific Particles:**
- Spiral animations for rare+ tiers
- Color-coded particles matching tier
- Configurable count and speed
- Async spawning (no lag)

**Layered Sound Design:**
- Tier-specific pitch progression (1.0f → 1.6f)
- Success sounds (ENTITY_PLAYER_LEVELUP)
- Bonus sounds for legendary+ (UI_TOAST_CHALLENGE_COMPLETE)
- Failure sounds (ENTITY_VILLAGER_NO)

**Fireworks for Legendary+ Tiers:**
- Automatic firework spawn on rare rolls
- Tier-specific colors and patterns
- Configurable enable/disable

**Server Broadcasts:**
- Announce legendary+ rolls to all players
- Customizable MiniMessage/legacy format messages
- Creates community excitement around rare drops

### 🔌 PlaceholderAPI Support
**25+ Placeholders for External Integration**

**Basic Stats:**
- `%bubblerune_total_rolls%` - Server total
- `%bubblerune_player_rolls%` - Player total
- `%bubblerune_tier_<tier>%` - Tier-specific counts
- `%bubblerune_cooldown%` - Remaining cooldown

**Leaderboards:**
- `%bubblerune_leaderboard_1%` through `%bubblerune_leaderboard_10%`
- `%bubblerune_leaderboard_rolls_1%` through `%bubblerune_leaderboard_rolls_10%`
- `%bubblerune_rank%` - Player's rank

**Milestones:**
- `%bubblerune_next_milestone%`
- `%bubblerune_milestone_progress%`
- `%bubblerune_milestone_percent%`

**Weekly Quests:**
- `%bubblerune_quest_count%`
- `%bubblerune_quest_reset%`
- `%bubblerune_quest_progress_<id>%`
- `%bubblerune_quest_complete_<id>%`

**Advanced:**
- `%bubblerune_player_tier_<tier>%`
- `%bubblerune_rarest_rune%`
- `%bubblerune_total_xp_spent%`

Perfect for scoreboards, holograms, tab lists, and chat formatting.

### 🎨 Text Formatting Support
**Modern & Legacy Formats**

BubbleRune supports **all text formats** in config files:

**Legacy Color Codes:** `&a&lGreen Bold`
**MiniMessage Tags:** `<green><bold>Green Bold</bold></green>`
**Gradients:** `<gradient:red:blue>Smooth transition</gradient>`
**Rainbow:** `<rainbow>Rainbow text!</rainbow>`
**Hex Colors:** `&#FF5733Custom Red` or `<#FF5733>Custom Red</color>`

All messages, rune names, and lore support these formats for complete visual customization.

### 📍 Multiple Rune Tables
**Server-Wide Deployment**

Set up unlimited rune tables across all worlds:
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
  end:
    world: world_the_end
    x: 0
    y: 50
    z: 0
```

Each table shares the same functionality - perfect for hub setups, multi-world servers, or zone-based progression.

### ⏱️ Cooldown System
**Prevent Table Spam**

Configurable per-player cooldowns:
- Default: 60 seconds
- Applies across all tables
- Shows remaining time in GUI and messages
- Automatic cleanup of expired cooldowns
- Can be disabled for unrestricted rolling

Prevents abuse while maintaining balanced progression.

---

## 🛠️ Technical Details

### Performance Optimizations
- **Async database operations** - No main thread blocking
- **Async particle spawning** - Reduces lag on large effects
- **Concurrent data structures** - Thread-safe statistics
- **Indexed database queries** - Fast leaderboard lookups
- **Connection pooling** - Efficient SQLite usage
- **Configurable particle limits** - Prevent overload

### Error Handling
- **Atomic transactions** - Full refunds on any error
- **Graceful plugin degradation** - Works without optional dependencies
- **Comprehensive logging** - Debug information for admins
- **Inventory full detection** - No item loss
- **Reflection-based CoinsEngine** - No hard dependency crashes

### Data Persistence
- **SQLite database** in `plugins/BubbleRune/data.db`
- **Automatic schema creation** on first run
- **Backwards-compatible configs** - Legacy settings still work
- **Daily stats tracking** - Historical data retention
- **Efficient indices** - Fast queries even with millions of rolls

### Customization
- **Two config files:** `config.yml` (mechanics) and `runes.yml` (appearance)
- **Per-tier enchant pools** - Control which enchants appear in each tier
- **Per-tier costs** - Separate XP and coin costs
- **Custom model data support** - Resource pack integration
- **Flexible messages** - All text customizable
- **Rune preview toggle** - Show tier before revealing

---

## 📋 Commands & Permissions

### Player Commands
| Command | Permission | Description |
|---------|-----------|-------------|
| `/bubblerune gui` | `bubblerune.gui` | Open tier selection GUI |
| `/bubblerune stats` | - | View personal statistics |
| `/bubblerune history [limit]` | - | View roll history (max 50) |
| `/bubblerune quests` | - | View weekly quest progress |

### Admin Commands
| Command | Permission | Description |
|---------|-----------|-------------|
| `/bubblerune reload` | `bubblerune.admin` | Reload configurations |
| `/bubblerune settable [name]` | `bubblerune.admin` | Set rune table location |
| `/bubblerune giverune <player> <tier>` | `bubblerune.admin` | Give rune to player |
| `/bubblerune testroll` | `bubblerune.admin` | Test tier weighting |
| `/bubblerune leaderboard [limit]` | `bubblerune.leaderboard` | View top rollers |

---

## 🔗 Dependencies

### Required
- **Minecraft 1.21+** (Purpur recommended, Paper/Spigot compatible)
- **Java 21+**

### Soft Dependencies (Optional)
- **EcoEnchants** - Custom enchantments (highly recommended)
- **PlaceholderAPI** - Placeholder support for other plugins
- **CoinsEngine** - BubbleCoin economy integration

**All optional dependencies gracefully degrade** - the plugin works standalone but gains features when integrated.

---

## 🚀 Setup & Installation

1. **Download** BubbleRune.jar from releases
2. **Place** in `plugins/` folder
3. **Install optional plugins** (EcoEnchants, PlaceholderAPI, CoinsEngine)
4. **Start server** - configs auto-generate
5. **Configure enchant pools** in `config.yml` under `tiers.<tier>.enchants`
6. **Customize rune appearance** in `runes.yml`
7. **Set table locations** with `/bubblerune settable spawn` (while standing at enchantment table)
8. **Adjust economy costs** in `economy.bubbleCoinCosts` section
9. **Reload** with `/bubblerune reload`

**First-Time Configuration Checklist:**
- ✅ Set enchantment IDs to match your EcoEnchants setup
- ✅ Configure XP costs per tier
- ✅ Configure BubbleCoin costs per tier
- ✅ Customize rune names/lore in runes.yml
- ✅ Set cooldown duration
- ✅ Place and register rune tables
- ✅ Test with `/bubblerune testroll`

---

## 📖 Use Cases

### Survival Servers
- XP sink preventing over-leveling
- Coin sink supporting economy health
- Progression goals for endgame players
- Community competition via leaderboards

### RPG Servers
- Class-specific enchantment pools per tier
- Quest integration for narrative progression
- Milestone rewards as level-up bonuses
- Lore-friendly rune item customization

### Economy Servers
- Trade commodity (unrevealed runes)
- Auction house integration
- Player shops selling specific tiers
- Coin + XP dual economy balance

### Minigame Servers
- Weekly quest competitions
- Seasonal leaderboard resets
- Tournament rewards (admin giverune command)
- VIP/rank perks (reduced cooldowns via permissions)

---

## 🔧 Configuration Examples

### Balanced Survival Economy
```yaml
economy:
  bubbleCoinCosts:
    common: 5
    uncommon: 15
    rare: 50
    epic: 150
    legendary: 500
```

### XP-Only Mode
```yaml
economy:
  bubbleCoinEnabled: false
```

### High-Stakes Gambling
```yaml
tiers:
  legendary:
    xpCost:
      min: 100000
      max: 100000  # Fixed cost
economy:
  bubbleCoinCosts:
    legendary: 1000
```

### Casual Server (Low Costs)
```yaml
tiers:
  common:
    xpCost:
      min: 100
      max: 200
economy:
  bubbleCoinCosts:
    common: 1
    uncommon: 1
    rare: 2
```

---

## 💡 Why Choose BubbleRune?

✅ **Feature-complete** - Everything you need out of the box
✅ **Performance-focused** - Async operations, optimized queries
✅ **Extensively customizable** - 200+ config options
✅ **Active development** - Regular updates and bug fixes
✅ **Database-backed** - Never lose player progress
✅ **Economy integration** - Works with existing plugins
✅ **Modern codebase** - Java 21, latest APIs
✅ **No dependencies required** - Soft-depend architecture
✅ **Visual polish** - Particles, sounds, fireworks
✅ **Community features** - Leaderboards, quests, milestones

---

## 📊 Statistics Example

After 1 week on a 50-player server:
- 15,000+ total rolls recorded
- 42 active players in database
- 750,000 XP consumed (economy sink)
- 12,500 BubbleCoins consumed (coin sink)
- Top player: 847 rolls
- Rarest tier rolled: Very Special (12 times)

All tracked automatically in SQLite for admin analysis.

---

## 🎯 Target Audience

**Server Owners** seeking:
- Unique enchantment progression
- Player retention mechanics
- Economy sinks for balance
- Community engagement tools
- Data-driven insights

**Players** who enjoy:
- Loot box mechanics (but balanced)
- Long-term progression
- Leaderboard competition
- Weekly challenges
- Resource management

---

## 📞 Support & Links

- **Documentation:** [DATABASE.md](DATABASE.md) for database details
- **Issues:** Report bugs via GitHub Issues
- **Suggestions:** Open feature requests
- **Updates:** Watch the repository for releases

---

## 📝 License & Credits

**BubbleRune** - Created for BubbleCraft
- Licensed under standard plugin terms
- EcoEnchants integration (Auxilor)
- CoinsEngine integration (NightExpress)
- PlaceholderAPI support (clip)

---

**Version:** 1.0.0
**Minecraft:** 1.21+
**Last Updated:** December 2025
