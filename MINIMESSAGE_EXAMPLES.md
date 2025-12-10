# BubbleRune MiniMessage Examples

This document showcases the new MiniMessage formatting capabilities in BubbleRune.

## Basic Formatting

### Colors
```yaml
# Legacy
name: "&aGreen &cRed &9Blue"

# MiniMessage
name: "<green>Green</green> <red>Red</red> <blue>Blue</blue>"
```

### Text Styles
```yaml
# Legacy
name: "&lBold &oItalic &nUnderline"

# MiniMessage
name: "<bold>Bold</bold> <italic>Italic</italic> <underlined>Underline</underlined>"
```

## Advanced Effects

### Gradients
```yaml
# Two-color gradient
name: "<gradient:red:blue>Fading Text</gradient>"

# Multi-color gradient
name: "<gradient:red:yellow:green>Rainbow Fade</gradient>"

# Gradient with phase
name: "<gradient:gold:yellow:gold>Shimmering Gold</gradient>"
```

### Rainbow Effect
```yaml
name: "<rainbow>Colorful Text!</rainbow>"

# Rainbow with phase (animated appearance)
name: "<rainbow:!>Static Rainbow</rainbow>"
```

### Combining Effects
```yaml
# Bold gradient
name: "<bold><gradient:gold:yellow>Legendary Rune</gradient></bold>"

# Rainbow + Bold
name: "<rainbow><bold>VERY SPECIAL</bold></rainbow>"

# Gradient + Italic
name: "<italic><gradient:aqua:blue>Mystical Enchantment</gradient></italic>"
```

## Practical Examples for BubbleRune

### Legendary Tier (runes.yml)
```yaml
tiers:
  legendary:
    name: "<gradient:gold:yellow><bold>✦ Legendary Rune ✦</bold></gradient>"
    lore:
      - "<gray>Right-click to reveal a"
      - "<gold><bold>LEGENDARY</bold></gold> <gray>enchantment!"
      - ""
      - "<yellow>This rune shimmers with power!</yellow>"
      - "<dark_gray>ID: %enchant%"
```

### Very Special Tier (runes.yml)
```yaml
tiers:
  veryspecial:
    name: "<rainbow><bold>★ VERY SPECIAL RUNE ★</bold></rainbow>"
    lore:
      - "<gradient:light_purple:pink>An incredibly rare rune!</gradient>"
      - ""
      - "<rainbow>Right-click to reveal</rainbow>"
      - "<rainbow>an ultimate enchantment!</rainbow>"
```

### Broadcast Messages (config.yml)
```yaml
messages:
  # Simple gradient
  broadcast: "<gradient:gold:yellow><bold>✦</bold></gradient> <yellow>%player%</yellow> rolled a <white>%tier%</white> rune!"
  
  # Rainbow effect for legendary+
  broadcast: "<rainbow><bold>★ %player% ★</bold></rainbow> <yellow>rolled a</yellow> <rainbow>%tier%</rainbow> <yellow>rune!</yellow>"
  
  # Glowing effect
  broadcast: "<gold><bold>[!]</bold></gold> <gradient:gold:yellow>%player% found a %tier% rune!</gradient>"
```

### Quest Completion (config.yml)
```yaml
weeklyQuests:
  quests:
    quest_1:
      name: "<gradient:green:lime><bold>Master Miner</bold></gradient>"
      description: "<gray>Mine <white>10,000</white> blocks</gray>"
      completionMessage: "<gradient:gold:yellow><bold>✦ Quest Complete! ✦</bold></gradient>"
```

### GUI Title (config.yml)
```yaml
gui:
  title: "<gradient:gold:yellow><bold>✦ Rune Table ✦</bold></gradient>"
```

## Color Reference

### Basic Colors
- `<black>`, `<dark_gray>`, `<gray>`, `<white>`
- `<red>`, `<dark_red>`, `<green>`, `<dark_green>`
- `<blue>`, `<dark_blue>`, `<aqua>`, `<dark_aqua>`
- `<yellow>`, `<gold>`, `<light_purple>`, `<dark_purple>`

### Formatting
- `<bold>`, `<italic>`, `<underlined>`
- `<strikethrough>`, `<obfuscated>`
- `<reset>` - Removes all formatting

## Tips & Best Practices

1. **Mix & Match**: You can use legacy (`&`) and MiniMessage (`<>`) in the same config!
   ```yaml
   message: "&aLegacy green <gradient:gold:yellow>with gradient</gradient>"
   ```

2. **Keep it Readable**: Don't overuse effects - players need to read the text!

3. **Test Your Gradients**: Some color combinations look better than others
   - Good: `<gradient:gold:yellow>`, `<gradient:aqua:blue>`, `<gradient:red:orange>`
   - Avoid: Very contrasting colors that make text hard to read

4. **Use Consistent Styling**: Keep similar items (like all legendary runes) using similar effects

5. **Performance**: MiniMessage is efficient, but rainbow effects on many items may impact performance slightly

## Migration Guide

### Converting Legacy to MiniMessage

**Before (Legacy):**
```yaml
name: "&6&lLegendary Rune"
lore:
  - "&7Right-click to reveal"
  - "&6&lLEGENDARY &7enchantment!"
```

**After (MiniMessage):**
```yaml
name: "<gold><bold>Legendary Rune</bold></gold>"
lore:
  - "<gray>Right-click to reveal"
  - "<gold><bold>LEGENDARY</bold></gold> <gray>enchantment!"
```

**After (MiniMessage with Effects):**
```yaml
name: "<gradient:gold:yellow><bold>Legendary Rune</bold></gradient>"
lore:
  - "<gray>Right-click to reveal"
  - "<gradient:gold:yellow><bold>LEGENDARY</bold></gradient> <gray>enchantment!"
```

## Troubleshooting

**Text appears with `<>` tags instead of colors:**
- The plugin automatically detects MiniMessage format
- Make sure you're closing all tags: `<bold>Text</bold>` not `<bold>Text`
- If it still doesn't work, the fallback to legacy formatting will apply

**Gradient doesn't show:**
- Ensure you have at least 2 colors: `<gradient:color1:color2>Text</gradient>`
- Use valid color names from the color reference above
- Close the gradient tag properly

**Want to use literal `<` or `>` characters:**
- Use `\<` and `\>` to escape them in MiniMessage context
