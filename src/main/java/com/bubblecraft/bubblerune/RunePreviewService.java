package com.bubblecraft.bubblerune;

import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class RunePreviewService {
    private final BubbleRunePlugin plugin;
    
    public RunePreviewService(BubbleRunePlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Creates a preview rune that shows the tier but not the enchantment.
     * Enhanced with better validation and error handling.
     */
    public ItemStack createPreviewRune(RuneTier tier, String enchantId) {
        if (tier == null || enchantId == null || enchantId.isEmpty()) {
            plugin.getLogger().warning("Invalid tier or enchantId for preview rune");
            return null;
        }
        
        org.bukkit.configuration.file.FileConfiguration runesConfig = plugin.getRunesConfig();
        
        // Get material from global settings with validation
        String materialName = runesConfig.getString("global.material", "PAPER");
        Material material = Material.matchMaterial(materialName.toUpperCase());
        if (material == null || !material.isItem()) {
            plugin.getLogger().warning("Invalid material '" + materialName + "', using PAPER");
            material = Material.PAPER;
        }
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            plugin.getLogger().severe("Failed to get ItemMeta for preview rune");
            return item;
        }
        
        // Set display name from preview settings (supports both legacy & and MiniMessage <>)
        // Use Adventure Component API to properly render MiniMessage formatting
        String name = runesConfig.getString("preview.name", "&e&l? %tier% Rune ?");
        name = name.replace("%tier%", tier.name());
        meta.displayName(TextFormatter.toComponent(name));
        
        // Set lore from preview settings (supports both legacy & and MiniMessage <>)
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        List<String> configLore = runesConfig.getStringList("preview.lore");
        for (String line : configLore) {
            line = line.replace("%tier%", tier.name());
            lore.add(TextFormatter.toComponent(line));
        }
        meta.lore(lore);
        
        // Set custom model data from preview settings or tier-specific
        int customModelData = runesConfig.getInt("preview.customModelData", 0);
        if (customModelData == 0) {
            customModelData = runesConfig.getInt("tiers." + tier.name().toLowerCase() + ".customModelData", 0);
        }
        if (customModelData > 0) {
            meta.setCustomModelData(customModelData);
        }
        
        // Add glow effect if enabled
        if (runesConfig.getBoolean("preview.glow", true)) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        // Mark as a genuine preview rune (stores the hidden enchant ID securely)
        RuneItemData.markPreviewRune(plugin, meta, tier, enchantId);
        
        item.setItemMeta(meta);
        return item;
    }
    
    /**
     * Checks if an item is a preview rune.
     * Enhanced with better validation.
     */
    public boolean isPreviewRune(ItemStack item) {
        return RuneItemData.isPreviewRune(item, plugin);
    }
    
    /**
     * Extracts the enchantment ID from a preview rune.
     * Uses modern Component API for better compatibility.
     */
    public String getEnchantIdFromPreview(ItemStack item) {
        return RuneItemData.getPreviewEnchantId(item, plugin);
    }
}
