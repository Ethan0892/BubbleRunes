package com.bubblecraft.bubblerune;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.util.*;

public class RuneService {
    private final BubbleRunePlugin plugin;
    private final Random random = new Random();
    private volatile NavigableMap<Double, RuneTier> tierWeights = new TreeMap<>();
    private volatile int minXp;
    private volatile int maxXp;
    private final Map<RuneTier, List<String>> tierEnchants = new EnumMap<>(RuneTier.class);
    private RunePreviewService previewService;
    private final Object reloadLock = new Object();
    private boolean coinsEngineAvailable = false;
    private Object coinsEngineAPI = null;

    public RuneService(BubbleRunePlugin plugin) {
        this.plugin = plugin;
        this.previewService = new RunePreviewService(plugin);
        initializeCoinsEngine();
        reload();
    }

    public void reload() {
        synchronized (reloadLock) {
            FileConfiguration config = plugin.getConfig();
            ConfigurationSection tiers = config.getConfigurationSection("tiers");
            
            // Create new maps to avoid concurrent modification
            NavigableMap<Double, RuneTier> newTierWeights = new TreeMap<>();
            Map<RuneTier, List<String>> newTierEnchants = new EnumMap<>(RuneTier.class);
            double total = 0.0;
            if (tiers != null) {
                for (String key : tiers.getKeys(false)) {
                    RuneTier tier;
                    try {
                        tier = RuneTier.valueOf(key.toUpperCase());
                    } catch (IllegalArgumentException ex) {
                        continue;
                    }
                    double weight = tiers.getDouble(key + ".weight", 1.0);
                    if (weight <= 0) continue;
                    total += weight;
                    newTierWeights.put(total, tier);

                    List<String> enchants = tiers.getStringList(key + ".enchants");
                    newTierEnchants.put(tier, new ArrayList<>(enchants)); // Defensive copy
                }
            }
            if (newTierWeights.isEmpty()) {
                total = 0;
                total += 60; newTierWeights.put(total, RuneTier.COMMON);
                total += 25; newTierWeights.put(total, RuneTier.UNCOMMON);
                total += 10; newTierWeights.put(total, RuneTier.RARE);
                total += 5; newTierWeights.put(total, RuneTier.LEGENDARY);
            }

            // Legacy global XP cost (deprecated - kept for backwards compatibility)
            int newMinXp = config.getInt("xpCost.min", 1000);
            int newMaxXp = config.getInt("xpCost.max", 10000);
            if (newMinXp < 0) newMinXp = 0;
            if (newMaxXp < newMinXp) newMaxXp = newMinXp;
            
            // Atomic replacement
            this.tierWeights = newTierWeights;
            this.tierEnchants.clear();
            this.tierEnchants.putAll(newTierEnchants);
            this.minXp = newMinXp;
            this.maxXp = newMaxXp;
        }
    }

    /**
     * Gets random XP cost for a specific tier.
     * If tier has xpCost.min/max configured, uses that.
     * Otherwise falls back to legacy global xpCost settings.
     */
    public int getRandomXpCost(RuneTier tier) {
        FileConfiguration config = plugin.getConfig();
        String tierPath = "tiers." + tier.name().toLowerCase();
        
        // Check if tier-specific XP cost is configured
        if (config.contains(tierPath + ".xpCost")) {
            int min = config.getInt(tierPath + ".xpCost.min", minXp);
            int max = config.getInt(tierPath + ".xpCost.max", maxXp);
            if (min < 0) min = 0;
            if (max < min) max = min;
            if (min == max) return min;
            return min + random.nextInt(max - min + 1);
        }
        
        // Fall back to legacy global setting
        if (minXp == maxXp) return minXp;
        return minXp + random.nextInt(maxXp - minXp + 1);
    }

    /**
     * Legacy method - gets random XP cost using global settings.
     * @deprecated Use getRandomXpCost(RuneTier) instead for tier-specific costs.
     */
    @Deprecated
    public int getRandomXpCost() {
        if (minXp == maxXp) return minXp;
        return minXp + random.nextInt(maxXp - minXp + 1);
    }

    /**
     * Determines the highest tier a player can afford based on their XP.
     * This creates a deterministic progression system where more XP = better guaranteed tier.
     * @param playerXp The player's total XP
     * @return The highest affordable tier, or null if they can't afford any tier
     */
    public RuneTier getAffordableTier(int playerXp) {
        // Check tiers from highest to lowest
        RuneTier[] tiers = {RuneTier.VERYSPECIAL, RuneTier.SPECIAL, RuneTier.LEGENDARY, 
                           RuneTier.EPIC, RuneTier.RARE, RuneTier.UNCOMMON, RuneTier.COMMON};
        
        for (RuneTier tier : tiers) {
            String tierPath = "tiers." + tier.name().toLowerCase();
            int minCost = plugin.getConfig().getInt(tierPath + ".xpCost.min", Integer.MAX_VALUE);
            
            if (playerXp >= minCost) {
                return tier;
            }
        }
        
        return null; // Can't afford any tier
    }
    
    /**
     * Rolls a random tier based on weights.
     * Uses weighted random selection algorithm.
     * @deprecated Use getAffordableTier() instead for deterministic tier selection
     */
    @Deprecated
    public RuneTier rollTier() {
        if (tierWeights.isEmpty()) {
            plugin.getLogger().warning("Tier weights are empty! Using COMMON as fallback.");
            return RuneTier.COMMON;
        }
        
        double total = tierWeights.lastKey();
        double r = random.nextDouble() * total;
        
        Map.Entry<Double, RuneTier> entry = tierWeights.higherEntry(r);
        if (entry == null) {
            // Fallback to highest tier if something goes wrong
            return tierWeights.lastEntry().getValue();
        }
        
        return entry.getValue();
    }
    
    /**
     * Rolls a tier with luck modifier.
     * Higher luck increases chance of better tiers.
     * @param luckModifier 0.0 to 1.0, where 1.0 = maximum luck
     */
    public RuneTier rollTierWithLuck(double luckModifier) {
        if (luckModifier <= 0) return rollTier();
        
        luckModifier = Math.min(1.0, Math.max(0.0, luckModifier));
        
        // Roll multiple times and take the best result
        int extraRolls = (int) (luckModifier * 3);
        RuneTier best = rollTier();
        
        for (int i = 0; i < extraRolls; i++) {
            RuneTier current = rollTier();
            if (current.ordinal() > best.ordinal()) {
                best = current;
            }
        }
        
        return best;
    }

    /**
     * Grants a rune of the specified tier to the player
     * @param player The player to receive the rune
     * @param tableLocation The location of the rune table
     * @param chosenTier The tier the player chose to roll for
     */
    public void grantRune(Player player, Location tableLocation, RuneTier chosenTier) {
        if (player == null || !player.isOnline()) {
            plugin.getLogger().warning("Attempted to grant rune to null or offline player");
            return;
        }
        
        if (chosenTier == null) {
            player.sendMessage(TextFormatter.format("&cInvalid tier selection!"));
            return;
        }
        
        int currentXp = player.getTotalExperience();
        
        // Get cost for the chosen tier
        int cost = getRandomXpCost(chosenTier);
        
        // Get BubbleCoin cost for this tier
        int coinCost = getBubbleCoinCost(chosenTier);
        
        // Verify player can afford the chosen tier (XP)
        String tierPath = "tiers." + chosenTier.name().toLowerCase();
        int minCost = plugin.getConfig().getInt(tierPath + ".xpCost.min", cost);
        
        if (currentXp < minCost) {
            String template = plugin.getConfig().getString("messages.notEnoughXp", "&cYou need at least %cost_xp% XP and %cost_coins% BubbleCoins to roll a %tier% rune!");
            String msg = TextFormatter.format(template
                .replace("%cost%", String.valueOf(minCost)) // Legacy placeholder support
                .replace("%cost_xp%", String.valueOf(minCost))
                .replace("%cost_coins%", String.valueOf(coinCost))
                .replace("%tier%", chosenTier.name().toLowerCase()));
            player.sendMessage(msg);
            
            // Play fail sound with pitch variation
            if (plugin.getConfig().getBoolean("sounds.enabled", true)) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
            }
            return;
        }
        
        // Check BubbleCoin balance if enabled
        if (isBubbleCoinEnabled() && coinCost > 0) {
            if (!hasEnoughCoins(player, coinCost)) {
                String template = plugin.getConfig().getString("messages.notEnoughCoins", "&cYou need %cost_coins% BubbleCoins to roll a rune!");
                String msg = TextFormatter.format(template.replace("%cost_coins%", String.valueOf(coinCost)));
                player.sendMessage(msg);
                
                if (plugin.getConfig().getBoolean("sounds.enabled", true)) {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
                }
                return;
            }
        }
        
        // Check if inventory has space
        if (player.getInventory().firstEmpty() == -1) {
            String msg = TextFormatter.format(plugin.getConfig().getString("messages.inventoryFull", "&cYour inventory is full! Clear a slot first."));
            player.sendMessage(msg);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Deduct XP
        player.setTotalExperience(currentXp - cost);
        
        // Deduct BubbleCoins if enabled
        boolean coinsDeducted = false;
        if (isBubbleCoinEnabled() && coinCost > 0) {
            if (!deductCoins(player, coinCost)) {
                // Refund XP if coin deduction fails
                player.setTotalExperience(currentXp);
                player.sendMessage(TextFormatter.format("&cFailed to deduct BubbleCoins! XP refunded."));
                return;
            }
            coinsDeducted = true;
        }
        
        // Track XP spent
        plugin.getStatsManager().recordXpSpent(player.getUniqueId(), cost);
        
        // Create rune (preview or direct based on config)
        ItemStack rune;
        String enchantId = null;
        try {
            if (plugin.getConfig().getBoolean("runePreview.enabled", true)) {
                enchantId = getRandomEnchantIdForTier(chosenTier);
                if (enchantId == null) {
                    plugin.getLogger().warning("No enchantments available for tier: " + chosenTier);
                    player.setTotalExperience(currentXp); // Refund XP
                    if (coinsDeducted) refundCoins(player, coinCost); // Refund coins
                    player.sendMessage(TextFormatter.format("&cError creating rune! XP and coins refunded."));
                    return;
                }
                rune = previewService.createPreviewRune(chosenTier, enchantId);
            } else {
                rune = createRuneItem(chosenTier);
            }
            
            if (rune == null) {
                plugin.getLogger().severe("Failed to create rune item for tier: " + chosenTier);
                player.setTotalExperience(currentXp); // Refund XP
                if (coinsDeducted) refundCoins(player, coinCost); // Refund coins
                player.sendMessage(TextFormatter.format("&cError creating rune! XP and coins refunded."));
                return;
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Exception creating rune: " + e.getMessage());
            e.printStackTrace();
            player.setTotalExperience(currentXp); // Refund XP on error
            if (coinsDeducted) refundCoins(player, coinCost); // Refund coins
            player.sendMessage(TextFormatter.format("&cError creating rune! XP and coins refunded."));
            return;
        }
        
        // Give rune to player
        player.getInventory().addItem(rune);
        
        // Spawn enhanced tier-specific particles
        if (plugin.getConfig().getBoolean("particles.enabled", true) && tableLocation != null) {
            spawnEnhancedParticles(tableLocation, chosenTier);
        }
        
        String template = plugin.getConfig().getString("messages.runeReceived", "&aYou received a &f%tier% &arune! (-%cost_xp% XP, -%cost_coins% BubbleCoins)");
        String message = TextFormatter.format(template
            .replace("%tier%", chosenTier.name().toLowerCase())
            .replace("%cost%", String.valueOf(cost)) // Legacy placeholder support
            .replace("%cost_xp%", String.valueOf(cost))
            .replace("%cost_coins%", String.valueOf(coinCost))
            .replace("%enchant%", enchantId != null ? enchantId : "unknown"));
        player.sendMessage(message);
        
        // Play tier-specific success sound with pitch variation
        if (plugin.getConfig().getBoolean("sounds.enabled", true)) {
            float pitch = getTierPitch(chosenTier);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, pitch);
            
            // Extra sound for legendary+ tiers
            if (chosenTier.ordinal() >= RuneTier.LEGENDARY.ordinal()) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, pitch);
                    }
                }, 5L);
            }
        }
        
        // Firework effects for legendary+ tiers
        if (plugin.getConfig().getBoolean("fireworks.enabled", true)) {
            if (chosenTier == RuneTier.LEGENDARY || chosenTier == RuneTier.SPECIAL || chosenTier == RuneTier.VERYSPECIAL) {
                spawnFirework(player, chosenTier);
            }
        }
        
        // Broadcast for legendary+ tiers
        if (plugin.getConfig().getBoolean("broadcasts.enabled", true)) {
            if (chosenTier == RuneTier.LEGENDARY || chosenTier == RuneTier.SPECIAL || chosenTier == RuneTier.VERYSPECIAL) {
                broadcastRoll(player, chosenTier);
            }
        }
        
        // Record stats
        plugin.getStatsManager().recordRoll(player.getUniqueId(), chosenTier);
        
        // Record roll to database asynchronously
        if (plugin.getDatabaseManager() != null) {
            String enchantName = enchantId != null ? enchantId : "Unknown";
            int enchantLevel = 1; // Default level for preview runes
            
            // Try to extract actual enchant info from the rune item
            if (rune.hasItemMeta() && rune.getItemMeta().hasEnchants()) {
                Map<Enchantment, Integer> enchants = rune.getItemMeta().getEnchants();
                if (!enchants.isEmpty()) {
                    Map.Entry<Enchantment, Integer> firstEnchant = enchants.entrySet().iterator().next();
                    enchantName = firstEnchant.getKey().getKey().getKey();
                    enchantLevel = firstEnchant.getValue();
                }
            }
            
            final String finalEnchantId = enchantId != null ? enchantId : enchantName;
            final String finalEnchantName = enchantName;
            final int finalEnchantLevel = enchantLevel;
            final int finalCost = cost;
            final int finalCoinCost = coinCost;
            
            plugin.getDatabaseManager().recordRollAsync(
                player.getUniqueId(),
                player.getName(),
                chosenTier,
                finalEnchantId,
                finalEnchantName,
                finalEnchantLevel,
                finalCost,
                finalCoinCost,
                tableLocation
            );
        }
        
        // Check milestones
        if (plugin.getConfig().getBoolean("milestones.enabled", true)) {
            checkMilestones(player);
        }
        
        // Track quest progress
        if (plugin.getQuestListener() != null) {
            plugin.getQuestListener().onRuneRoll(player, chosenTier);
        }
        
        // Set cooldown
        if (plugin.getConfig().getBoolean("cooldown.enabled", true)) {
            plugin.getCooldownManager().setCooldown(player.getUniqueId());
        }
    }    public ItemStack createRuneItem(RuneTier tier) {
        org.bukkit.configuration.file.FileConfiguration runesConfig = plugin.getRunesConfig();
        String tierPath = "tiers." + tier.name().toLowerCase();
        
        // Get material from global settings
        String materialName = runesConfig.getString("global.material", "PAPER");
        Material material = Material.matchMaterial(materialName.toUpperCase());
        if (material == null || material.isAir()) {
            material = Material.PAPER;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Set display name from runes.yml (supports both legacy & and MiniMessage <>)
            // Use Adventure Component API to properly render MiniMessage formatting
            String name = runesConfig.getString(tierPath + ".name", "&f" + tier.name() + " Rune");
            meta.displayName(TextFormatter.toComponent(name));

            // Set lore from runes.yml (supports both legacy & and MiniMessage <>)
            // Use Component API to properly render gradients and advanced formatting
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            List<String> configLore = runesConfig.getStringList(tierPath + ".lore");
            for (String line : configLore) {
                line = line.replace("%tier%", tier.name());
                lore.add(TextFormatter.toComponent(line));
            }
            meta.lore(lore);

            // Set custom model data from runes.yml
            int customModelData = runesConfig.getInt(tierPath + ".customModelData", 0);
            if (customModelData > 0) {
                meta.setCustomModelData(customModelData);
            }
            
            // Add glow effect if enabled
            if (runesConfig.getBoolean("global.glow", true)) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            
            item.setItemMeta(meta);
        }

        return item;
    }

    public String getRandomEnchantIdForTier(RuneTier tier) {
        List<String> list = tierEnchants.getOrDefault(tier, Collections.emptyList());
        if (list.isEmpty()) {
            return null;
        }
        return list.get(random.nextInt(list.size()));
    }
    
    /**
     * Spawns enhanced particle effects with spiral animation
     */
    private void spawnEnhancedParticles(Location tableLocation, RuneTier tier) {
        if (tableLocation == null || tableLocation.getWorld() == null) return;
        
        final Location center = tableLocation.clone().add(0.5, 1.2, 0.5);
        final World world = tableLocation.getWorld();
        final Particle.DustOptions dustOptions = getTierDustColor(tier);
        final int particleCount = plugin.getConfig().getInt("particles.count", 50);
        final boolean spiralEffect = tier.ordinal() >= RuneTier.RARE.ordinal();
        
        // Immediate burst
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                if (dustOptions != null) {
                    world.spawnParticle(Particle.DUST, center, particleCount / 2, 0.5, 0.5, 0.5, 0.1, dustOptions);
                }
                world.spawnParticle(Particle.ENCHANT, center, particleCount / 3, 0.5, 0.5, 0.5, 1.0);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to spawn particles: " + e.getMessage());
            }
        });
        
        // Spiral animation for rare+ tiers
        if (spiralEffect) {
            final int spiralTicks = 20;
            final double radius = 1.5;
            
            for (int i = 0; i < spiralTicks; i++) {
                final int tick = i;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    try {
                        double angle = tick * Math.PI / 5;
                        double height = tick * 0.1;
                        double x = center.getX() + Math.cos(angle) * radius;
                        double y = center.getY() + height;
                        double z = center.getZ() + Math.sin(angle) * radius;
                        Location particleLoc = new Location(world, x, y, z);
                        
                        if (dustOptions != null) {
                            world.spawnParticle(Particle.DUST, particleLoc, 3, 0.1, 0.1, 0.1, 0, dustOptions);
                        }
                    } catch (Exception e) {
                        // Ignore particle errors on animated effects
                    }
                }, i);
            }
        }
    }
    
    private Particle.DustOptions getTierDustColor(RuneTier tier) {
        Color color;
        float size;
        switch (tier) {
            case COMMON:
                color = Color.fromRGB(170, 170, 170); // Gray
                size = 1.0f;
                break;
            case UNCOMMON:
                color = Color.fromRGB(85, 255, 85); // Green
                size = 1.2f;
                break;
            case RARE:
                color = Color.fromRGB(85, 85, 255); // Blue
                size = 1.4f;
                break;
            case EPIC:
                color = Color.fromRGB(255, 85, 85); // Red
                size = 1.6f;
                break;
            case LEGENDARY:
                color = Color.fromRGB(255, 170, 0); // Gold
                size = 1.8f;
                break;
            case SPECIAL:
                color = Color.fromRGB(85, 255, 255); // Cyan
                size = 2.0f;
                break;
            case VERYSPECIAL:
                color = Color.fromRGB(200, 100, 255); // Purple/Pink
                size = 2.2f;
                break;
            default:
                return null;
        }
        return new Particle.DustOptions(color, size);
    }
    
    /**
     * Gets pitch for tier-specific sound effects
     */
    private float getTierPitch(RuneTier tier) {
        switch (tier) {
            case COMMON: return 1.0f;
            case UNCOMMON: return 1.1f;
            case RARE: return 1.2f;
            case EPIC: return 1.3f;
            case LEGENDARY: return 1.4f;
            case SPECIAL: return 1.5f;
            case VERYSPECIAL: return 1.6f;
            default: return 1.0f;
        }
    }
    
    /**
     * Spawns enhanced firework with tier-specific patterns
     */
    private void spawnFirework(Player player, RuneTier tier) {
        if (player == null || !player.isOnline() || player.getWorld() == null) return;
        
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                Location loc = player.getLocation().add(0, 1, 0);
                Firework firework = player.getWorld().spawn(loc, Firework.class);
                FireworkMeta meta = firework.getFireworkMeta();
                if (meta == null) return;
            
                // Tier-specific colors and effects
                Color color1, color2, fade;
                FireworkEffect.Type type;
                boolean flicker, trail;
                int power;
                
                switch (tier) {
                    case LEGENDARY:
                        color1 = Color.ORANGE;
                        color2 = Color.YELLOW;
                        fade = Color.RED;
                        type = FireworkEffect.Type.BALL_LARGE;
                        flicker = true;
                        trail = true;
                        power = 1;
                        break;
                    case SPECIAL:
                        color1 = Color.AQUA;
                        color2 = Color.fromRGB(85, 255, 255);
                        fade = Color.WHITE;
                        type = FireworkEffect.Type.STAR;
                        flicker = true;
                        trail = true;
                        power = 1;
                        break;
                    case VERYSPECIAL:
                        color1 = Color.FUCHSIA;
                        color2 = Color.fromRGB(200, 100, 255);
                        fade = Color.fromRGB(150, 50, 200);
                        type = FireworkEffect.Type.BURST;
                        flicker = true;
                        trail = true;
                        power = 2;
                        break;
                    default:
                        color1 = Color.WHITE;
                        color2 = Color.SILVER;
                        fade = Color.GRAY;
                        type = FireworkEffect.Type.BALL;
                        flicker = false;
                        trail = false;
                        power = 1;
                }
                
                FireworkEffect effect = FireworkEffect.builder()
                    .with(type)
                    .withColor(color1, color2)
                    .withFade(fade)
                    .flicker(flicker)
                    .trail(trail)
                    .build();
                
                meta.addEffect(effect);
                meta.setPower(power);
                firework.setFireworkMeta(meta);
                
                // Detonate immediately for instant effect
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (firework.isValid()) {
                        firework.detonate();
                    }
                }, 1L);
                
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to spawn firework: " + e.getMessage());
            }
        });
    }
    
    private void checkMilestones(Player player) {
        if (player == null || !player.isOnline()) return;
        
        org.bukkit.configuration.ConfigurationSection milestones = plugin.getConfig().getConfigurationSection("milestones.rewards");
        if (milestones == null) return;
        
        int playerRolls = plugin.getStatsManager().getPlayerRolls(player.getUniqueId());
        
        for (String milestoneStr : milestones.getKeys(false)) {
            try {
                int milestone = Integer.parseInt(milestoneStr);
                if (plugin.getStatsManager().shouldTriggerMilestone(player.getUniqueId(), milestone)) {
                    try {
                        String tierName = plugin.getConfig().getString("milestones.rewards." + milestone + ".tier", "RARE");
                        String message = plugin.getConfig().getString("milestones.rewards." + milestone + ".message", 
                            "&a&lMilestone! &eYou've rolled &f" + milestone + " &erunes!");
                        
                        RuneTier rewardTier = RuneTier.valueOf(tierName.toUpperCase());
                        ItemStack reward = createRuneItem(rewardTier);
                        if (reward == null) {
                            plugin.getLogger().warning("Failed to create milestone reward for tier: " + tierName);
                            continue;
                        }
                        
                        player.getInventory().addItem(reward);
                        player.sendMessage(TextFormatter.format(message));
                        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                        
                        // Spawn celebration particles on main thread
                        if (player.getWorld() != null) {
                            player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, 
                                player.getLocation().add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.1);
                        }
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid milestone reward configuration: " + e.getMessage());
                    } catch (Exception e) {
                        plugin.getLogger().severe("Error processing milestone " + milestone + ": " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            } catch (NumberFormatException e) {
                // Skip non-numeric keys
            } catch (Exception e) {
                plugin.getLogger().severe("Unexpected error in milestone check: " + e.getMessage());
            }
        }
    }
    
    /**
     * Broadcasts rare rune rolls to all players using Component API
     */
    private void broadcastRoll(Player player, RuneTier tier) {
        String template = plugin.getConfig().getString("messages.broadcast", 
            "&6&l✦ &e%player% &6rolled a &f%tier% &6rune! &6&l✦");
        String message = template
            .replace("%player%", player.getName())
            .replace("%tier%", tier.name().toLowerCase());
        
        // Use Component API for proper formatting
        net.kyori.adventure.text.Component component = TextFormatter.toComponent(message);
        plugin.getServer().broadcast(component);
        
        // Play announcement sound for all nearby players
        if (plugin.getConfig().getBoolean("sounds.enabled", true)) {
            Location playerLoc = player.getLocation();
            double radius = 50.0;
            for (Player nearby : player.getWorld().getPlayers()) {
                if (nearby.getLocation().distance(playerLoc) <= radius) {
                    nearby.playSound(nearby.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.5f, 1.0f);
                }
            }
        }
    }
    
    // ===========================
    // CoinsEngine Integration
    // ===========================
    
    /**
     * Initializes CoinsEngine API if available
     */
    private void initializeCoinsEngine() {
        if (!plugin.getConfig().getBoolean("economy.bubbleCoinEnabled", true)) {
            plugin.getLogger().info("BubbleCoin economy disabled in config");
            return;
        }
        
        try {
            Plugin coinsPlugin = Bukkit.getPluginManager().getPlugin("CoinsEngine");
            if (coinsPlugin != null && coinsPlugin.isEnabled()) {
                coinsEngineAvailable = true;
                coinsEngineAPI = coinsPlugin;
                plugin.getLogger().info("CoinsEngine integration enabled!");
            } else {
                plugin.getLogger().warning("CoinsEngine plugin not found - BubbleCoin costs disabled");
                if (plugin.getConfig().getBoolean("economy.requireCoinsEnginePlugin", true)) {
                    plugin.getLogger().warning("Set economy.requireCoinsEnginePlugin to false to disable this warning");
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to hook into CoinsEngine: " + e.getMessage());
        }
    }
    
    /**
     * Checks if BubbleCoin economy is enabled and available
     */
    private boolean isBubbleCoinEnabled() {
        return plugin.getConfig().getBoolean("economy.bubbleCoinEnabled", true) && coinsEngineAvailable;
    }
    
    /**
     * Gets the BubbleCoin cost for rolling a rune
     */
    private int getBubbleCoinCost(RuneTier tier) {
        // Check for per-tier cost first (new format)
        String tierPath = "economy.bubbleCoinCosts." + tier.name().toLowerCase();
        if (plugin.getConfig().contains(tierPath)) {
            return plugin.getConfig().getInt(tierPath, 1);
        }
        // Fallback to global cost (legacy format)
        return plugin.getConfig().getInt("economy.bubbleCoinCost", 1);
    }
    
    /**
     * Checks if player has enough BubbleCoins
     */
    private boolean hasEnoughCoins(Player player, int amount) {
        if (!isBubbleCoinEnabled()) return true;
        
        try {
            // Use CoinsEngine API
            Class<?> coinsEngineClass = Class.forName("su.nightexpress.coinsengine.api.CoinsEngineAPI");
            java.lang.reflect.Method getBalanceMethod = coinsEngineClass.getMethod("getBalance", 
                org.bukkit.entity.Player.class, String.class);
            
            String currency = plugin.getConfig().getString("economy.bubbleCoinCurrency", "bubblecoin");
            double balance = (double) getBalanceMethod.invoke(null, player, currency);
            
            return balance >= amount;
        } catch (Exception e) {
            plugin.getLogger().warning("Error checking BubbleCoin balance: " + e.getMessage());
            return true; // Allow transaction if check fails
        }
    }
    
    /**
     * Deducts BubbleCoins from player
     */
    private boolean deductCoins(Player player, int amount) {
        if (!isBubbleCoinEnabled()) return true;
        
        try {
            // Use CoinsEngine API
            Class<?> coinsEngineClass = Class.forName("su.nightexpress.coinsengine.api.CoinsEngineAPI");
            java.lang.reflect.Method removeBalanceMethod = coinsEngineClass.getMethod("removeBalance", 
                org.bukkit.entity.Player.class, String.class, double.class);
            
            String currency = plugin.getConfig().getString("economy.bubbleCoinCurrency", "bubblecoin");
            removeBalanceMethod.invoke(null, player, currency, (double) amount);
            
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Error deducting BubbleCoins: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Refunds BubbleCoins to player
     */
    private void refundCoins(Player player, int amount) {
        if (!isBubbleCoinEnabled()) return;
        
        try {
            // Use CoinsEngine API
            Class<?> coinsEngineClass = Class.forName("su.nightexpress.coinsengine.api.CoinsEngineAPI");
            java.lang.reflect.Method addBalanceMethod = coinsEngineClass.getMethod("addBalance", 
                org.bukkit.entity.Player.class, String.class, double.class);
            
            String currency = plugin.getConfig().getString("economy.bubbleCoinCurrency", "bubblecoin");
            addBalanceMethod.invoke(null, player, currency, (double) amount);
            
            plugin.getLogger().info("Refunded " + amount + " BubbleCoins to " + player.getName());
        } catch (Exception e) {
            plugin.getLogger().severe("Error refunding BubbleCoins: " + e.getMessage());
        }
    }
    
    /**
     * Gets player's BubbleCoin balance
     */
    public double getPlayerCoins(Player player) {
        if (!isBubbleCoinEnabled()) return 0.0;
        
        try {
            Class<?> coinsEngineClass = Class.forName("su.nightexpress.coinsengine.api.CoinsEngineAPI");
            java.lang.reflect.Method getBalanceMethod = coinsEngineClass.getMethod("getBalance", 
                org.bukkit.entity.Player.class, String.class);
            
            String currency = plugin.getConfig().getString("economy.bubbleCoinCurrency", "bubblecoin");
            return (double) getBalanceMethod.invoke(null, player, currency);
        } catch (Exception e) {
            return 0.0;
        }
    }
}
