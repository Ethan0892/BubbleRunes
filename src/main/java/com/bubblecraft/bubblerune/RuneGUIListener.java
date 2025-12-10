package com.bubblecraft.bubblerune;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;

public class RuneGUIListener implements Listener {
    private final BubbleRunePlugin plugin;
    private final RuneService runeService;
    
    public RuneGUIListener(BubbleRunePlugin plugin, RuneService runeService) {
        this.plugin = plugin;
        this.runeService = runeService;
    }
    
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        if (!player.isOnline()) return;
        
        try {
            String guiTitle = plugin.getConfig().getString("gui.title", "&5&lRune Table - Choose Your Tier");
            String plainTitle = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(event.getView().title());
            
            if (!plainTitle.contains("Rune Table")) return;
            
            event.setCancelled(true);
            
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;
            
            // Determine which tier was clicked based on slot
            int slot = event.getSlot();
            RuneTier chosenTier = null;
            
            switch (slot) {
                case 10: chosenTier = RuneTier.COMMON; break;
                case 11: chosenTier = RuneTier.UNCOMMON; break;
                case 12: chosenTier = RuneTier.RARE; break;
                case 13: chosenTier = RuneTier.EPIC; break;
                case 14: chosenTier = RuneTier.LEGENDARY; break;
                case 15: chosenTier = RuneTier.SPECIAL; break;
                case 16: chosenTier = RuneTier.VERYSPECIAL; break;
                default: return; // Not a tier button
            }
            
            // Check cooldown
            if (plugin.getConfig().getBoolean("cooldown.enabled", true)) {
                if (plugin.getCooldownManager().isOnCooldown(player.getUniqueId())) {
                    long remaining = plugin.getCooldownManager().getRemainingCooldown(player.getUniqueId());
                    String msg = plugin.getConfig().getString("messages.cooldown", 
                        "&cYou must wait %seconds% seconds before using the rune table again!");
                    msg = msg.replace("%seconds%", String.valueOf(remaining));
                    player.sendMessage(TextFormatter.format(msg));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return;
                }
            }
            
            // Get table location from metadata
            Location tableLocation = null;
            if (player.hasMetadata("runetable_location")) {
                for (MetadataValue value : player.getMetadata("runetable_location")) {
                    if (value.getOwningPlugin() == plugin) {
                        tableLocation = (Location) value.value();
                        break;
                    }
                }
            }
            
            // Close GUI and grant chosen rune tier
            player.closeInventory();
            runeService.grantRune(player, tableLocation, chosenTier);
            
            // Clean up metadata
            player.removeMetadata("runetable_location", plugin);
            
        } catch (Exception e) {
            plugin.getLogger().warning("Error processing GUI click: " + e.getMessage());
            e.printStackTrace();
            player.closeInventory();
        }
    }
}
