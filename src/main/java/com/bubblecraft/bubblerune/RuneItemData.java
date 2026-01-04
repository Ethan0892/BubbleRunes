package com.bubblecraft.bubblerune;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

final class RuneItemData {
    private static final String KEY_TYPE = "br_type";
    private static final String TYPE_RUNE = "rune";
    private static final String TYPE_PREVIEW_RUNE = "preview_rune";

    private static final String KEY_TIER = "br_tier";
    private static final String KEY_PREVIEW_ENCHANT = "br_preview_enchant";

    private RuneItemData() {
    }

    private static NamespacedKey key(Plugin plugin, String key) {
        return new NamespacedKey(plugin, key);
    }

    static void markRune(Plugin plugin, ItemMeta meta, RuneTier tier) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(key(plugin, KEY_TYPE), PersistentDataType.STRING, TYPE_RUNE);
        pdc.set(key(plugin, KEY_TIER), PersistentDataType.STRING, tier.name());
    }

    static void markPreviewRune(Plugin plugin, ItemMeta meta, RuneTier tier, String enchantId) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(key(plugin, KEY_TYPE), PersistentDataType.STRING, TYPE_PREVIEW_RUNE);
        pdc.set(key(plugin, KEY_TIER), PersistentDataType.STRING, tier.name());
        pdc.set(key(plugin, KEY_PREVIEW_ENCHANT), PersistentDataType.STRING, enchantId);
    }

    static boolean isRune(ItemStack item, Plugin plugin) {
        return hasType(item, plugin, TYPE_RUNE);
    }

    static boolean isPreviewRune(ItemStack item, Plugin plugin) {
        return hasType(item, plugin, TYPE_PREVIEW_RUNE);
    }

    private static boolean hasType(ItemStack item, Plugin plugin, String expectedType) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String type = pdc.get(key(plugin, KEY_TYPE), PersistentDataType.STRING);
        return expectedType.equals(type);
    }

    static RuneTier getTier(ItemStack item, Plugin plugin) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        String tierName = meta.getPersistentDataContainer().get(key(plugin, KEY_TIER), PersistentDataType.STRING);
        if (tierName == null || tierName.isBlank()) return null;
        try {
            return RuneTier.valueOf(tierName);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    static String getPreviewEnchantId(ItemStack item, Plugin plugin) {
        if (!isPreviewRune(item, plugin)) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(key(plugin, KEY_PREVIEW_ENCHANT), PersistentDataType.STRING);
    }
}
