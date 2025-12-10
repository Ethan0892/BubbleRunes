package com.bubblecraft.bubblerune;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RuneTableListener implements Listener {
    private final BubbleRunePlugin plugin;
    private final RuneTableGUI gui;
    private final Map<UUID, Long> lastInteract = new ConcurrentHashMap<>();
    private static final long INTERACT_COOLDOWN = 500; // 0.5 second spam protection

    public RuneTableListener(BubbleRunePlugin plugin, RuneTableGUI gui) {
        this.plugin = plugin;
        this.gui = gui;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        // Quick exit checks
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.ENCHANTING_TABLE) return;
        
        Player player = event.getPlayer();
        if (player == null || !player.isOnline()) return;

        // Spam protection - prevent rapid clicking
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastClick = lastInteract.get(playerId);
        if (lastClick != null && (now - lastClick) < INTERACT_COOLDOWN) {
            return; // Silently ignore spam clicks
        }
        lastInteract.put(playerId, now);
        
        List<Location> runeTables = plugin.getRuneTableLocations();
        if (runeTables == null || runeTables.isEmpty()) return;

        // Check if clicked block matches any registered rune table
        Location clickedLoc = block.getLocation();
        boolean isRuneTable = false;
        
        try {
            for (Location tableLoc : runeTables) {
                if (tableLoc == null || tableLoc.getWorld() == null) continue;
                if (clickedLoc.getWorld().equals(tableLoc.getWorld()) &&
                    clickedLoc.getBlockX() == tableLoc.getBlockX() &&
                    clickedLoc.getBlockY() == tableLoc.getBlockY() &&
                    clickedLoc.getBlockZ() == tableLoc.getBlockZ()) {
                    isRuneTable = true;
                    break;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error checking rune table locations: " + e.getMessage());
            return;
        }
        
        if (!isRuneTable) return;

        event.setCancelled(true);
        
        if (!plugin.getConfig().getBoolean("enabled", true)) {
            player.sendMessage(ChatColor.RED + "BubbleRune is currently disabled.");
            return;
        }
        
        // Check cooldown
        CooldownManager cooldown = plugin.getCooldownManager();
        if (plugin.getConfig().getBoolean("cooldown.enabled", true)) {
            if (cooldown.isOnCooldown(player.getUniqueId())) {
                long remaining = cooldown.getRemainingCooldown(player.getUniqueId());
                String msg = plugin.getConfig().getString("messages.cooldown", 
                    "&cYou must wait %seconds% seconds before using the rune table again!");
                msg = msg.replace("%seconds%", String.valueOf(remaining));
                player.sendMessage(TextFormatter.format(msg));
                
                // Play deny sound
                if (plugin.getConfig().getBoolean("sounds.enabled", true)) {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
                return;
            }
        }

        gui.openGUI(player, block.getLocation());
    }
}