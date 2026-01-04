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
    private volatile boolean coinsEngineIncompatibleLogged = false;

    public RuneService(BubbleRunePlugin plugin) {
        this.plugin = plugin;
        this.previewService = new RunePreviewService(plugin);
        initializeCoinsEngine();
        reload();
    }

    private double getXpCostMultiplier() {
        double multiplier = plugin.getConfig().getDouble("xpCost.multiplier", 1.0);
        if (Double.isNaN(multiplier) || Double.isInfinite(multiplier)) return 1.0;
        return Math.max(0.0, multiplier);
    }

    private int applyXpMultiplier(int baseCost) {
        if (baseCost <= 0) return 0;
        double multiplier = getXpCostMultiplier();
        long scaled = Math.round(baseCost * multiplier);
        if (scaled <= 0) return 0;
        if (scaled > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) scaled;
    }

    /**
     * Returns the configured minimum XP cost for a tier (after multiplier).
     */
    public int getTierMinXpCost(RuneTier tier) {
        FileConfiguration config = plugin.getConfig();
        String tierPath = "tiers." + tier.name().toLowerCase();
        int min = config.getInt(tierPath + ".xpCost.min", minXp);
        if (min < 0) min = 0;
        return applyXpMultiplier(min);
    }

    /**
     * Returns the configured maximum XP cost for a tier (after multiplier).
     */
    public int getTierMaxXpCost(RuneTier tier) {
        FileConfiguration config = plugin.getConfig();
        String tierPath = "tiers." + tier.name().toLowerCase();
        int max = config.getInt(tierPath + ".xpCost.max", maxXp);
        if (max < 0) max = 0;
        return applyXpMultiplier(max);
    }

    /**
     * Returns the highest tier the player can currently afford.
     * Considers XP minimums and (if enabled/available) per-tier BubbleCoin costs.
     */
    public RuneTier getBestAffordableTier(Player player) {
        if (player == null) return null;

        int playerXp = ExperienceUtil.getTotalExperience(player);
        boolean coinsAvailable = isBubbleCoinEconomyAvailable();
        double playerCoins = coinsAvailable ? getPlayerCoins(player) : 0.0;

        RuneTier[] descending = {
            RuneTier.VERYSPECIAL,
            RuneTier.SPECIAL,
            RuneTier.LEGENDARY,
            RuneTier.EPIC,
            RuneTier.RARE,
            RuneTier.UNCOMMON,
            RuneTier.COMMON
        };

        for (RuneTier tier : descending) {
            int minXp = getTierMinXpCost(tier);
            if (playerXp < minXp) continue;

            if (coinsAvailable) {
                String coinPath = "economy.bubbleCoinCosts." + tier.name().toLowerCase();
                int coinCost = plugin.getConfig().getInt(coinPath, plugin.getConfig().getInt("economy.bubbleCoinCost", 1));
                if (coinCost > 0 && playerCoins < coinCost) continue;
            }

            return tier;
        }

        return null;
    }

    /**
     * Returns a random tier the player can currently afford.
     * Considers XP minimums and (if enabled/available) per-tier BubbleCoin costs.
     */
    public RuneTier getRandomAffordableTier(Player player) {
        if (player == null) return null;

        int playerXp = ExperienceUtil.getTotalExperience(player);
        boolean coinsAvailable = isBubbleCoinEconomyAvailable();
        double playerCoins = coinsAvailable ? getPlayerCoins(player) : 0.0;

        RuneTier[] all = {
            RuneTier.COMMON,
            RuneTier.UNCOMMON,
            RuneTier.RARE,
            RuneTier.EPIC,
            RuneTier.LEGENDARY,
            RuneTier.SPECIAL,
            RuneTier.VERYSPECIAL
        };

        List<RuneTier> affordable = new ArrayList<>();
        for (RuneTier tier : all) {
            int minXp = getTierMinXpCost(tier);
            if (playerXp < minXp) continue;

            if (coinsAvailable) {
                String coinPath = "economy.bubbleCoinCosts." + tier.name().toLowerCase();
                int coinCost = plugin.getConfig().getInt(coinPath, plugin.getConfig().getInt("economy.bubbleCoinCost", 1));
                if (coinCost > 0 && playerCoins < coinCost) continue;
            }

            affordable.add(tier);
        }

        if (affordable.isEmpty()) return null;
        return affordable.get(random.nextInt(affordable.size()));
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
                total = 0.0;
                total += 60;
                newTierWeights.put(total, RuneTier.COMMON);
                total += 25;
                newTierWeights.put(total, RuneTier.UNCOMMON);
                total += 10;
                newTierWeights.put(total, RuneTier.RARE);
                total += 5;
                newTierWeights.put(total, RuneTier.LEGENDARY);
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

            int rolled;
            if (min == max) {
                rolled = min;
            } else {
                rolled = min + random.nextInt(max - min + 1);
            }

            return applyXpMultiplier(rolled);
        }
        
        // Fall back to legacy global setting
        if (minXp == maxXp) return applyXpMultiplier(minXp);
        return applyXpMultiplier(minXp + random.nextInt(maxXp - minXp + 1));
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
            int minCost = getTierMinXpCost(tier);
            
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
        if (!validateGrantRuneInputs(player, chosenTier)) {
            return;
        }

        int currentXp = ExperienceUtil.getTotalExperience(player);
        int minCost = getTierMinXpCost(chosenTier);

        if (currentXp < minCost) {
            handleNotEnoughXp(player, chosenTier, minCost);
            return;
        }

        int cost = rollAffordableXpCost(currentXp, chosenTier, minCost);
        int coinCost = getBubbleCoinCost(chosenTier);

        if (!validateCoinAffordability(player, coinCost)) {
            return;
        }

        if (!ensureInventorySpace(player)) {
            return;
        }

        boolean coinsDeducted = deductXpAndCoins(player, currentXp, cost, coinCost);
        if (coinCost > 0 && isBubbleCoinEnabled() && !coinsDeducted) {
            return;
        }

        plugin.getStatsManager().recordXpSpent(player.getUniqueId(), cost);

        RuneCreation creation = createRuneWithRefunds(player, chosenTier, currentXp, coinsDeducted, coinCost);
        if (creation == null) {
            return;
        }

        // Give rune to player
        player.getInventory().addItem(creation.rune);

        // Spawn enhanced tier-specific particles
        if (plugin.getConfig().getBoolean("particles.enabled", true) && tableLocation != null) {
            spawnEnhancedParticles(tableLocation, chosenTier);
        }

        sendRuneReceivedMessage(player, chosenTier, cost, coinCost, creation.enchantId);
        playSuccessSounds(player, chosenTier);
        triggerFireworksAndBroadcasts(player, chosenTier);

        plugin.getStatsManager().recordRoll(player.getUniqueId(), chosenTier);
        recordRollToDatabaseAsync(player, chosenTier, creation.enchantId, creation.rune, cost, coinCost, tableLocation);

        if (plugin.getConfig().getBoolean("milestones.enabled", true)) {
            checkMilestones(player);
        }

        if (plugin.getQuestListener() != null) {
            plugin.getQuestListener().onRuneRoll(player, chosenTier);
        }

        if (plugin.getConfig().getBoolean("cooldown.enabled", true)) {
            plugin.getCooldownManager().setCooldown(player.getUniqueId());
        }
    }

    private boolean validateGrantRuneInputs(Player player, RuneTier chosenTier) {
        if (player == null || !player.isOnline()) {
            plugin.getLogger().warning("Attempted to grant rune to null or offline player");
            return false;
        }

        if (chosenTier == null) {
            player.sendMessage(TextFormatter.format(plugin.getMessage(
                "messages.invalidTierSelection",
                "&cInvalid tier selection!")));
            return false;
        }

        return true;
    }

    private void handleNotEnoughXp(Player player, RuneTier chosenTier, int minCost) {
        int coinCost = getBubbleCoinCost(chosenTier);
        String template = plugin.getMessage(
            "messages.notEnoughXp",
            "&cYou need at least %cost_xp% XP and %cost_coins% BubbleCoins to roll a %tier% rune!"
        );
        String msg = TextFormatter.format(template
            .replace("%cost%", String.valueOf(minCost)) // Legacy placeholder support
            .replace("%cost_xp%", String.valueOf(minCost))
            .replace("%cost_coins%", String.valueOf(coinCost))
            .replace("%tier%", chosenTier.name().toLowerCase()));
        player.sendMessage(msg);

        if (plugin.getConfig().getBoolean("sounds.enabled", true)) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
        }
    }

    private int rollAffordableXpCost(int currentXp, RuneTier chosenTier, int minCost) {
        int maxCost = getTierMaxXpCost(chosenTier);
        if (maxCost < minCost) {
            maxCost = minCost;
        }

        int effectiveMaxCost = Math.min(maxCost, currentXp);
        if (effectiveMaxCost <= minCost) {
            return minCost;
        }

        return minCost + random.nextInt(effectiveMaxCost - minCost + 1);
    }

    private boolean validateCoinAffordability(Player player, int coinCost) {
        if (isBubbleCoinEnabled() && coinCost > 0 && !hasEnoughCoins(player, coinCost)) {
            String template = plugin.getMessage(
                "messages.notEnoughCoins",
                "&cYou need %cost_coins% BubbleCoins to roll a rune!"
            );
            player.sendMessage(TextFormatter.format(template.replace("%cost_coins%", String.valueOf(coinCost))));
            if (plugin.getConfig().getBoolean("sounds.enabled", true)) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
            }
            return false;
        }
        return true;
    }

    private boolean ensureInventorySpace(Player player) {
        if (player.getInventory().firstEmpty() != -1) {
            return true;
        }
        String msg = TextFormatter.format(plugin.getMessage(
            "messages.inventoryFull",
            "&cYour inventory is full! Clear a slot first."
        ));
        player.sendMessage(msg);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        return false;
    }

    /**
     * Deducts XP immediately. If coin deduction is required and fails, XP is refunded and a message is sent.
     *
     * @return true if coins were deducted (or not required), false if coin deduction failed.
     */
    private boolean deductXpAndCoins(Player player, int originalXp, int xpCost, int coinCost) {
        int newTotalXp = originalXp - xpCost;
        if (newTotalXp < 0) {
            newTotalXp = 0;
        }
        ExperienceUtil.setTotalExperience(player, newTotalXp);

        if (!isBubbleCoinEnabled() || coinCost <= 0) {
            return true;
        }

        if (deductCoins(player, coinCost)) {
            return true;
        }

        ExperienceUtil.setTotalExperience(player, originalXp);
        player.sendMessage(TextFormatter.format(plugin.getMessage(
            "messages.failedDeductCoinsRefunded",
            "&cFailed to deduct BubbleCoins! XP refunded."
        )));
        return false;
    }

    private static final class RuneCreation {
        private final ItemStack rune;
        private final String enchantId;

        private RuneCreation(ItemStack rune, String enchantId) {
            this.rune = rune;
            this.enchantId = enchantId;
        }
    }

    private RuneCreation createRuneWithRefunds(Player player, RuneTier chosenTier, int refundXp, boolean coinsDeducted, int coinCost) {
        ItemStack rune;
        String enchantId = null;

        try {
            if (plugin.getConfig().getBoolean("runePreview.enabled", true)) {
                enchantId = getRandomEnchantIdForTier(chosenTier);
                if (enchantId == null) {
                    plugin.getLogger().warning("No enchantments available for tier: " + chosenTier);
                    refundAfterFailedCreation(player, refundXp, coinsDeducted, coinCost);
                    return null;
                }
                rune = previewService.createPreviewRune(chosenTier, enchantId);
            } else {
                rune = createRuneItem(chosenTier);
            }

            if (rune == null) {
                plugin.getLogger().severe("Failed to create rune item for tier: " + chosenTier);
                refundAfterFailedCreation(player, refundXp, coinsDeducted, coinCost);
                return null;
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Exception creating rune: " + e.getMessage());
            e.printStackTrace();
            refundAfterFailedCreation(player, refundXp, coinsDeducted, coinCost);
            return null;
        }

        return new RuneCreation(rune, enchantId);
    }

    private void refundAfterFailedCreation(Player player, int originalXp, boolean coinsDeducted, int coinCost) {
        ExperienceUtil.setTotalExperience(player, originalXp);
        if (coinsDeducted) {
            refundCoins(player, coinCost);
        }
        player.sendMessage(TextFormatter.format(plugin.getMessage(
            "messages.errorCreatingRuneRefunded",
            "&cError creating rune! XP and coins refunded."
        )));
    }

    private void sendRuneReceivedMessage(Player player, RuneTier chosenTier, int cost, int coinCost, String enchantId) {
        String template = plugin.getMessage(
            "messages.runeReceived",
            "&aYou received a &f%tier% &arune! (-%cost_xp% XP, -%cost_coins% BubbleCoins)"
        );
        String message = TextFormatter.format(template
            .replace("%tier%", chosenTier.name().toLowerCase())
            .replace("%cost%", String.valueOf(cost)) // Legacy placeholder support
            .replace("%cost_xp%", String.valueOf(cost))
            .replace("%cost_coins%", String.valueOf(coinCost))
            .replace("%enchant%", enchantId != null ? enchantId : "unknown"));
        player.sendMessage(message);
    }

    private void playSuccessSounds(Player player, RuneTier chosenTier) {
        if (!plugin.getConfig().getBoolean("sounds.enabled", true)) {
            return;
        }

        float pitch = getTierPitch(chosenTier);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, pitch);

        if (chosenTier.ordinal() >= RuneTier.LEGENDARY.ordinal()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, pitch);
                }
            }, 5L);
        }
    }

    private void triggerFireworksAndBroadcasts(Player player, RuneTier chosenTier) {
        boolean isHighTier = chosenTier == RuneTier.LEGENDARY || chosenTier == RuneTier.SPECIAL || chosenTier == RuneTier.VERYSPECIAL;

        if (isHighTier && plugin.getConfig().getBoolean("fireworks.enabled", true)) {
            spawnFirework(player, chosenTier);
        }

        if (isHighTier && plugin.getConfig().getBoolean("broadcasts.enabled", true)) {
            broadcastRoll(player, chosenTier);
        }
    }

    private void recordRollToDatabaseAsync(Player player, RuneTier chosenTier, String enchantId, ItemStack rune, int cost, int coinCost, Location tableLocation) {
        if (plugin.getDatabaseManager() == null) {
            return;
        }

        String enchantName = enchantId != null ? enchantId : "Unknown";
        int enchantLevel = 1;

        if (rune.hasItemMeta() && rune.getItemMeta().hasEnchants()) {
            Map<Enchantment, Integer> enchants = rune.getItemMeta().getEnchants();
            if (!enchants.isEmpty()) {
                Map.Entry<Enchantment, Integer> firstEnchant = enchants.entrySet().iterator().next();
                enchantName = firstEnchant.getKey().getKey().getKey();
                enchantLevel = firstEnchant.getValue();
            }
        }

        String finalEnchantId = enchantId != null ? enchantId : enchantName;
        String finalEnchantName = enchantName;
        int finalEnchantLevel = enchantLevel;
        int finalCost = cost;
        int finalCoinCost = coinCost;

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

    public ItemStack createRuneItem(RuneTier tier) {
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

            // Mark as a genuine rune item (prevents renamed/enchant-hidden items from triggering rune logic)
            RuneItemData.markRune(plugin, meta, tier);
            
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
        String template = plugin.getMessage("messages.broadcast", 
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
                // Only mark available if the API signatures we use exist.
                if (verifyCoinsEngineApi()) {
                    coinsEngineAvailable = true;
                    coinsEngineAPI = coinsPlugin;
                    plugin.getLogger().info("CoinsEngine integration enabled!");
                } else {
                    disableCoinsEngine("CoinsEngine detected but API signatures are incompatible; BubbleCoin costs disabled.", null);
                }
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

    private void disableCoinsEngine(String reason, Throwable error) {
        coinsEngineAvailable = false;
        coinsEngineAPI = null;
        if (!coinsEngineIncompatibleLogged) {
            coinsEngineIncompatibleLogged = true;
            if (error != null) {
                plugin.getLogger().warning(reason + " (" + error.getClass().getSimpleName() + ": " + error.getMessage() + ")");
            } else {
                plugin.getLogger().warning(reason);
            }
        }
    }

    private boolean verifyCoinsEngineApi() {
        try {
            Class<?> coinsEngineClass = Class.forName("su.nightexpress.coinsengine.api.CoinsEngineAPI");

            // At least one supported getBalance signature
            if (tryGetStaticMethod(coinsEngineClass, "getBalance", org.bukkit.entity.Player.class, String.class) == null
                && tryGetStaticMethod(coinsEngineClass, "getBalance", java.util.UUID.class, String.class) == null) {
                return false;
            }

            // At least one supported removeBalance signature
            if (tryGetStaticMethod(coinsEngineClass, "removeBalance", org.bukkit.entity.Player.class, String.class, double.class) == null
                && tryGetStaticMethod(coinsEngineClass, "removeBalance", java.util.UUID.class, String.class, double.class) == null) {
                return false;
            }

            // Optional: addBalance for refunds (if missing, we can still operate but refunds won't work).
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private java.lang.reflect.Method tryGetStaticMethod(Class<?> clazz, String name, Class<?>... params) {
        try {
            return clazz.getMethod(name, params);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Number invokeGetBalance(Player player, String currency) throws Exception {
        Class<?> coinsEngineClass = Class.forName("su.nightexpress.coinsengine.api.CoinsEngineAPI");

        java.lang.reflect.Method m = tryGetStaticMethod(coinsEngineClass, "getBalance", org.bukkit.entity.Player.class, String.class);
        if (m != null) {
            Object result = m.invoke(null, player, currency);
            return (result instanceof Number) ? (Number) result : 0.0;
        }

        m = tryGetStaticMethod(coinsEngineClass, "getBalance", java.util.UUID.class, String.class);
        if (m != null) {
            Object result = m.invoke(null, player.getUniqueId(), currency);
            return (result instanceof Number) ? (Number) result : 0.0;
        }

        throw new NoSuchMethodException("CoinsEngineAPI.getBalance(<supported signature>)");
    }

    private void invokeRemoveBalance(Player player, String currency, double amount) throws Exception {
        Class<?> coinsEngineClass = Class.forName("su.nightexpress.coinsengine.api.CoinsEngineAPI");

        java.lang.reflect.Method m = tryGetStaticMethod(coinsEngineClass, "removeBalance", org.bukkit.entity.Player.class, String.class, double.class);
        if (m != null) {
            m.invoke(null, player, currency, amount);
            return;
        }

        m = tryGetStaticMethod(coinsEngineClass, "removeBalance", java.util.UUID.class, String.class, double.class);
        if (m != null) {
            m.invoke(null, player.getUniqueId(), currency, amount);
            return;
        }

        throw new NoSuchMethodException("CoinsEngineAPI.removeBalance(<supported signature>)");
    }

    private void invokeAddBalance(Player player, String currency, double amount) throws Exception {
        Class<?> coinsEngineClass = Class.forName("su.nightexpress.coinsengine.api.CoinsEngineAPI");

        java.lang.reflect.Method m = tryGetStaticMethod(coinsEngineClass, "addBalance", org.bukkit.entity.Player.class, String.class, double.class);
        if (m != null) {
            m.invoke(null, player, currency, amount);
            return;
        }

        m = tryGetStaticMethod(coinsEngineClass, "addBalance", java.util.UUID.class, String.class, double.class);
        if (m != null) {
            m.invoke(null, player.getUniqueId(), currency, amount);
            return;
        }

        // Refund is best-effort.
        throw new NoSuchMethodException("CoinsEngineAPI.addBalance(<supported signature>)");
    }

    public boolean isBubbleCoinEconomyAvailable() {
        return isBubbleCoinEnabled();
    }
    
    /**
     * Checks if BubbleCoin economy is enabled and available
     */
    private boolean isBubbleCoinEnabled() {
        return plugin.getConfig().getBoolean("economy.bubbleCoinEnabled", true)
            && "coinsengine".equalsIgnoreCase(plugin.getConfig().getString("economy.provider", "coinsengine"))
            && coinsEngineAvailable;
    }

    private String getEconomyCurrencyId() {
        // New config key
        String currency = plugin.getConfig().getString("economy.currencyId", null);
        if (currency != null && !currency.isBlank()) return currency;

        // Backwards compatible key
        return plugin.getConfig().getString("economy.bubbleCoinCurrency", "bubblecoin");
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
            double balance = invokeGetBalance(player, getEconomyCurrencyId()).doubleValue();
            return balance >= amount;
        } catch (Throwable t) {
            disableCoinsEngine("Error checking BubbleCoin balance; disabling BubbleCoin economy", t);
            return true; // Allow transaction if check fails
        }
    }
    
    /**
     * Deducts BubbleCoins from player
     */
    private boolean deductCoins(Player player, int amount) {
        if (!isBubbleCoinEnabled()) return true;
        
        try {
            invokeRemoveBalance(player, getEconomyCurrencyId(), (double) amount);
            return true;
        } catch (Throwable t) {
            disableCoinsEngine("Error deducting BubbleCoins; disabling BubbleCoin economy", t);
            return true; // Don't block rune rolls if economy is broken
        }
    }
    
    /**
     * Refunds BubbleCoins to player
     */
    private void refundCoins(Player player, int amount) {
        if (!isBubbleCoinEnabled()) return;
        
        try {
            invokeAddBalance(player, getEconomyCurrencyId(), (double) amount);
        } catch (Throwable t) {
            disableCoinsEngine("Error refunding BubbleCoins; disabling BubbleCoin economy", t);
        }
    }
    
    /**
     * Gets player's BubbleCoin balance
     */
    public double getPlayerCoins(Player player) {
        if (!isBubbleCoinEnabled()) return 0.0;
        
        try {
            return invokeGetBalance(player, getEconomyCurrencyId()).doubleValue();
        } catch (Throwable t) {
            disableCoinsEngine("Error reading BubbleCoin balance; disabling BubbleCoin economy", t);
            return 0.0;
        }
    }
}
