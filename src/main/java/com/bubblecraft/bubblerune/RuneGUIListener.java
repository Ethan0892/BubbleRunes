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
            
            int slot = event.getSlot();

            // Only the bottom-middle button rolls a rune
            if (slot != 22) {
                return;
            }
            
            // Check cooldown
            if (plugin.getConfig().getBoolean("cooldown.enabled", true)) {
                if (plugin.getCooldownManager().isOnCooldown(player.getUniqueId())) {
                    long remaining = plugin.getCooldownManager().getRemainingCooldown(player.getUniqueId());
                    String msg = plugin.getMessage("messages.cooldown", 
                        "&cYou must wait %seconds% seconds before using the rune table again!");
                    msg = msg.replace("%seconds%", String.valueOf(remaining));
                    player.sendMessage(TextFormatter.format(msg));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return;
                }
            }

            RuneTier chosenTier = runeService.getRandomAffordableTier(player);
            if (chosenTier == null) {
                int minCost = runeService.getTierMinXpCost(RuneTier.COMMON);
                int coinCost = 0;
                if (runeService.isBubbleCoinEconomyAvailable()) {
                    coinCost = plugin.getConfig().getInt("economy.bubbleCoinCosts.common",
                        plugin.getConfig().getInt("economy.bubbleCoinCost", 1));
                }
                int playerXp = ExperienceUtil.getTotalExperience(player);
                String msg = plugin.getMessage("messages.notEnoughXp",
                    "&cYou need at least %cost_xp% XP and %cost_coins% BubbleCoins to roll a %tier% rune!");
                msg = msg
                    .replace("%tier%", "common")
                    .replace("%cost_xp%", String.valueOf(minCost))
                    .replace("%cost_coins%", String.valueOf(Math.max(0, coinCost)));
                player.sendMessage(TextFormatter.format(msg));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
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

            // Crate-like "rolling" delay (configurable)
            boolean gambleEnabled = plugin.getConfig().getBoolean("gambleRoll.enabled", true);
            long delayTicks = plugin.getConfig().getLong("gambleRoll.delayTicks", 40L);
            if (delayTicks < 0L) delayTicks = 0L;

            if (gambleEnabled && delayTicks > 0L) {
                if (plugin.getConfig().getBoolean("sounds.enabled", true)) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.2f);
                }
                String rollingMsg = plugin.getMessage(
                    "messages.gambleRolling",
                    "&7Rolling... &f%tier% &7tier");
                rollingMsg = rollingMsg.replace("%tier%", chosenTier.name().toLowerCase());
                player.sendMessage(TextFormatter.format(rollingMsg));

                Location finalTableLocation = tableLocation;
                RuneTier finalChosenTier = chosenTier;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!player.isOnline()) return;
                    if (plugin.getConfig().getBoolean("sounds.enabled", true)) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.6f);
                    }
                    runeService.grantRune(player, finalTableLocation, finalChosenTier);
                }, delayTicks);
            } else {
                runeService.grantRune(player, tableLocation, chosenTier);
            }
            
            // Clean up metadata
            player.removeMetadata("runetable_location", plugin);
            
        } catch (Exception e) {
            plugin.getLogger().warning("Error processing GUI click: " + e.getMessage());
            e.printStackTrace();
            player.closeInventory();
        }
    }
}
