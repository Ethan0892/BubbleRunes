package com.bubblecraft.bubblerune;

import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class RuneTableGUI {
    private final BubbleRunePlugin plugin;
    
    public RuneTableGUI(BubbleRunePlugin plugin) {
        this.plugin = plugin;
    }
    
    public void openGUI(Player player, Location tableLocation) {
        Inventory gui = Bukkit.createInventory(null, 27, TextFormatter.toComponent(
            plugin.getConfig().getString("gui.title", "&5&lRune Table - Choose Your Tier")));
        
        // Fill with glass panes
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        if (glassMeta != null) {
            glassMeta.displayName(net.kyori.adventure.text.Component.empty());
            glass.setItemMeta(glassMeta);
        }
        for (int i = 0; i < 27; i++) {
            gui.setItem(i, glass);
        }
        
        int playerXp = player.getTotalExperience();
        boolean onCooldown = plugin.getConfig().getBoolean("cooldown.enabled", true) && 
                           plugin.getCooldownManager().isOnCooldown(player.getUniqueId());
        
        // Create tier selection buttons
        RuneTier[] tiers = {RuneTier.COMMON, RuneTier.UNCOMMON, RuneTier.RARE, RuneTier.EPIC, 
                           RuneTier.LEGENDARY, RuneTier.SPECIAL, RuneTier.VERYSPECIAL};
        int[] slots = {10, 11, 12, 13, 14, 15, 16}; // Positions for 7 tiers
        
        for (int i = 0; i < tiers.length; i++) {
            RuneTier tier = tiers[i];
            ItemStack tierButton = createTierButton(tier, playerXp, onCooldown);
            gui.setItem(slots[i], tierButton);
        }
        
        // Info item at bottom
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.displayName(TextFormatter.toComponent("&6&lHow to Use"));
            List<String> infoLore = new ArrayList<>();
            infoLore.add(TextFormatter.format("&7Click on a tier above to"));
            infoLore.add(TextFormatter.format("&7roll a rune of that tier!"));
            infoLore.add("");
            infoLore.add(TextFormatter.format("&7Your XP: &e" + playerXp));
            
            // Show BubbleCoin balance if enabled
            if (plugin.getConfig().getBoolean("economy.bubbleCoinEnabled", true)) {
                double coins = plugin.getRuneService().getPlayerCoins(player);
                infoLore.add(TextFormatter.format("&7Your BubbleCoins: &e" + String.format("%.0f", coins)));
                int coinCost = plugin.getConfig().getInt("economy.bubbleCoinCost", 1);
                infoLore.add("");
                infoLore.add(TextFormatter.format("&7Cost per roll: &e" + coinCost + " BubbleCoin" + (coinCost > 1 ? "s" : "")));
            }
            
            if (onCooldown) {
                long remaining = plugin.getCooldownManager().getRemainingCooldown(player.getUniqueId());
                infoLore.add("");
                infoLore.add(TextFormatter.format("&cCooldown: &f" + remaining + "s"));
            }
            
            infoMeta.setLore(infoLore);
            info.setItemMeta(infoMeta);
        }
        gui.setItem(22, info);
        
        // Store table location in player metadata for later use
        player.setMetadata("runetable_location", new org.bukkit.metadata.FixedMetadataValue(plugin, tableLocation));
        
        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
    }
    
    /**
     * Creates a clickable tier selection button
     */
    private ItemStack createTierButton(RuneTier tier, int playerXp, boolean onCooldown) {
        String tierPath = "tiers." + tier.name().toLowerCase();
        int minCost = plugin.getConfig().getInt(tierPath + ".xpCost.min", 1000);
        int maxCost = plugin.getConfig().getInt(tierPath + ".xpCost.max", minCost);
        
        boolean canAfford = playerXp >= minCost;
        
        // Determine material and appearance based on tier and affordability
        Material material;
        String color = getTierColor(tier);
        
        if (onCooldown) {
            material = Material.RED_STAINED_GLASS_PANE;
        } else if (!canAfford) {
            material = Material.GRAY_STAINED_GLASS_PANE;
        } else {
            // Use tier-specific materials when affordable
            switch (tier) {
                case COMMON: material = Material.WHITE_WOOL; break;
                case UNCOMMON: material = Material.LIME_WOOL; break;
                case RARE: material = Material.BLUE_WOOL; break;
                case EPIC: material = Material.RED_WOOL; break;
                case LEGENDARY: material = Material.ORANGE_WOOL; break;
                case SPECIAL: material = Material.CYAN_WOOL; break;
                case VERYSPECIAL: material = Material.MAGENTA_WOOL; break;
                default: material = Material.PAPER;
            }
        }
        
        ItemStack button = new ItemStack(material);
        ItemMeta meta = button.getItemMeta();
        if (meta != null) {
            String tierName = tier.name().charAt(0) + tier.name().substring(1).toLowerCase();
            meta.displayName(TextFormatter.toComponent(color + "&l" + tierName + " Rune"));
            
            List<String> lore = new ArrayList<>();
            if (onCooldown) {
                lore.add(TextFormatter.format("&cOn Cooldown!"));
                lore.add(TextFormatter.format("&7Wait before rolling again"));
            } else if (!canAfford) {
                lore.add(TextFormatter.format("&cNot Enough XP!"));
                lore.add(TextFormatter.format("&7Required: &e" + minCost + " XP"));
                lore.add(TextFormatter.format("&7You have: &e" + playerXp + " XP"));
            } else {
                lore.add(TextFormatter.format("&aClick to roll!"));
                lore.add("");
                lore.add(TextFormatter.format("&7XP Cost: &e" + minCost + "-" + maxCost + " XP"));
                
                // Show BubbleCoin cost if enabled
                if (plugin.getConfig().getBoolean("economy.bubbleCoinEnabled", true)) {
                    String coinPath = "economy.bubbleCoinCosts." + tier.name().toLowerCase();
                    int coinCost = plugin.getConfig().getInt(coinPath, plugin.getConfig().getInt("economy.bubbleCoinCost", 1));
                    if (coinCost > 0) {
                        lore.add(TextFormatter.format("&7Coin Cost: &e" + coinCost + " BubbleCoin" + (coinCost > 1 ? "s" : "")));
                    }
                }
                
                lore.add(TextFormatter.format("&7You have: &e" + playerXp + " XP"));
                
                // Show enchants for this tier
                List<String> enchants = plugin.getConfig().getStringList(tierPath + ".enchants");
                if (!enchants.isEmpty()) {
                    lore.add("");
                    lore.add(TextFormatter.format("&7Possible Enchants: &f" + enchants.size()));
                }
            }
            
            meta.setLore(lore);
            
            // Add glow effect if affordable
            if (canAfford && !onCooldown) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            
            button.setItemMeta(meta);
        }
        
        return button;
    }
    
    /**
     * Gets the appropriate color code for a tier
     */
    private String getTierColor(RuneTier tier) {
        switch (tier) {
            case COMMON: return "&f";
            case UNCOMMON: return "&a";
            case RARE: return "&9";
            case EPIC: return "&c";
            case LEGENDARY: return "&6";
            case SPECIAL: return "&b";
            case VERYSPECIAL: return "&d";
            default: return "&7";
        }
    }
}
