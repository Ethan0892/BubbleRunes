package com.bubblecraft.bubblerune;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/**
 * Allows upgrading runes via crafting grid: 3 same-tier runes -> 1 next-tier rune.
 * Works for both player 2x2 crafting and crafting table 3x3.
 */
public class RuneCraftingCombineListener implements Listener {
    private final BubbleRunePlugin plugin;
    private final RuneService runeService;

    public RuneCraftingCombineListener(BubbleRunePlugin plugin, RuneService runeService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.runeService = Objects.requireNonNull(runeService, "runeService");
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (!plugin.getConfig().getBoolean("runeCraftingCombining.enabled", true)) return;
        if (!(event.getInventory() instanceof CraftingInventory)) return;

        CraftingInventory inv = (CraftingInventory) event.getInventory();
        ItemStack result = computeCraftResult(inv.getMatrix());
        inv.setResult(result);
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (!plugin.getConfig().getBoolean("runeCraftingCombining.enabled", true)) return;
        if (!(event.getInventory() instanceof CraftingInventory)) return;

        CraftingInventory inv = (CraftingInventory) event.getInventory();
        ItemStack expected = computeCraftResult(inv.getMatrix());
        if (expected == null) return;

        // We handle the craft ourselves to ensure we only consume genuine rune items.
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        // Prevent losing item if inventory is full.
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(TextFormatter.format(plugin.getMessage(
                "messages.inventoryFull",
                "&cYour inventory is full! Clear a slot first.")));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        if (!consumeRequiredFromMatrix(inv.getMatrix())) {
            return;
        }

        inv.setMatrix(inv.getMatrix());
        inv.setResult(null);

        player.getInventory().addItem(expected);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.2f);
    }

    ItemStack computeCraftResult(ItemStack[] matrix) {
        if (matrix == null || matrix.length == 0) return null;

        int required = plugin.getConfig().getInt("runeCraftingCombining.requiredRunes", 3);
        if (required < 2) required = 2;

        RuneTier tier = null;
        int runeCount = 0;

        for (ItemStack item : matrix) {
            if (item == null || item.getType().isAir()) continue;

            // Only genuine runes (PDC-marked) count.
            if (!RuneItemData.isRune(item, plugin)) return null;

            RuneTier itemTier = RuneItemData.getTier(item, plugin);
            if (itemTier == null) return null;

            if (tier == null) {
                tier = itemTier;
            } else if (tier != itemTier) {
                return null;
            }

            runeCount += item.getAmount();
            if (runeCount >= required) break;
        }

        if (tier == null) return null;
        if (runeCount < required) return null;

        RuneTier next = getNextTier(tier);
        if (next == null) return null;

        return runeService.createRuneItem(next);
    }

    boolean consumeRequiredFromMatrix(ItemStack[] matrix) {
        if (matrix == null || matrix.length == 0) return false;

        int required = plugin.getConfig().getInt("runeCraftingCombining.requiredRunes", 3);
        if (required < 2) required = 2;

        // Validate again and determine tier.
        RuneTier tier = null;
        int total = 0;
        for (ItemStack item : matrix) {
            if (item == null || item.getType().isAir()) continue;
            if (!RuneItemData.isRune(item, plugin)) return false;
            RuneTier t = RuneItemData.getTier(item, plugin);
            if (t == null) return false;
            if (tier == null) tier = t;
            else if (tier != t) return false;
            total += item.getAmount();
        }
        if (tier == null || total < required) return false;
        if (getNextTier(tier) == null) return false;

        int remaining = required;
        for (int i = 0; i < matrix.length && remaining > 0; i++) {
            ItemStack item = matrix[i];
            if (item == null || item.getType().isAir()) continue;

            int take = Math.min(item.getAmount(), remaining);
            remaining -= take;

            int newAmount = item.getAmount() - take;
            if (newAmount <= 0) {
                matrix[i] = null;
            } else {
                item.setAmount(newAmount);
                matrix[i] = item;
            }
        }

        return remaining == 0;
    }

    private static RuneTier getNextTier(RuneTier current) {
        RuneTier[] tiers = RuneTier.values();
        for (int i = 0; i < tiers.length - 1; i++) {
            if (tiers[i] == current) {
                return tiers[i + 1];
            }
        }
        return null;
    }
}
