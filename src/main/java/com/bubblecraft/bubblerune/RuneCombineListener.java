package com.bubblecraft.bubblerune;

import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class RuneCombineListener implements Listener {
    private final BubbleRunePlugin plugin;
    private final RuneService runeService;

    public RuneCombineListener(BubbleRunePlugin plugin, RuneService runeService) {
        this.plugin = plugin;
        this.runeService = runeService;
    }

    @EventHandler
    public void onAnvilClick(InventoryClickEvent event) {
        if (!plugin.getConfig().getBoolean("runeCombining.enabled", true)) return;
        if (!(event.getInventory() instanceof AnvilInventory)) return;
        if (event.getSlotType() != InventoryType.SlotType.RESULT) return;
        
        AnvilInventory anvil = (AnvilInventory) event.getInventory();
        ItemStack left = anvil.getItem(0);
        ItemStack right = anvil.getItem(1);
        
        if (left == null || right == null) return;
        
        RuneTier leftTier = getRuneTier(left);
        RuneTier rightTier = getRuneTier(right);
        
        if (leftTier == null || rightTier == null) return;
        if (leftTier != rightTier) return;
        
        int required = plugin.getConfig().getInt("runeCombining.requiredRunes", 3);
        if (left.getAmount() + right.getAmount() < required) return;
        
        // Check if this is the highest tier
        RuneTier nextTier = getNextTier(leftTier);
        if (nextTier == null) {
            if (event.getWhoClicked() instanceof Player) {
                Player player = (Player) event.getWhoClicked();
                player.sendMessage(ChatColor.RED + "This rune tier cannot be upgraded further!");
            }
            event.setCancelled(true);
            return;
        }
        
        event.setCancelled(true);
        
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        
        // Consume the runes
        int consumed = 0;
        if (left.getAmount() >= required) {
            left.setAmount(left.getAmount() - required);
            consumed = required;
        } else {
            consumed = left.getAmount();
            int remaining = required - consumed;
            left.setAmount(0);
            if (right.getAmount() >= remaining) {
                right.setAmount(right.getAmount() - remaining);
            }
        }
        
        // Give upgraded rune
        ItemStack upgraded = runeService.createRuneItem(nextTier);
        player.getInventory().addItem(upgraded);
        
        // Effects
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.2f);
        player.sendMessage(TextFormatter.format(
            plugin.getConfig().getString("messages.runeCombined", 
                "&aYou combined %count% &f%tier% &arunes into a &f%newtier% &arune!")
                .replace("%count%", String.valueOf(required))
                .replace("%tier%", leftTier.name().toLowerCase())
                .replace("%newtier%", nextTier.name().toLowerCase())));
        
        player.closeInventory();
    }
    
    private RuneTier getRuneTier(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasDisplayName()) return null;
        
        String name = ChatColor.stripColor(meta.getDisplayName()).toLowerCase();
        for (RuneTier tier : RuneTier.values()) {
            if (name.contains(tier.name().toLowerCase()) && name.contains("rune")) {
                return tier;
            }
        }
        return null;
    }
    
    private RuneTier getNextTier(RuneTier current) {
        RuneTier[] tiers = RuneTier.values();
        for (int i = 0; i < tiers.length - 1; i++) {
            if (tiers[i] == current) {
                return tiers[i + 1];
            }
        }
        return null; // Already at max tier
    }
}
