package com.bubblecraft.bubblerune;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;

public class QuestTrackingListener implements Listener {
    private final BubbleRunePlugin plugin;
    private final WeeklyQuestManager questManager;
    
    public QuestTrackingListener(BubbleRunePlugin plugin, WeeklyQuestManager questManager) {
        this.plugin = plugin;
        this.questManager = questManager;
    }
    
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.getConfig().getBoolean("weeklyQuests.enabled", true)) return;
        
        Player player = event.getPlayer();
        String blockType = event.getBlock().getType().name().toLowerCase();
        
        // Track mining quests
        questManager.incrementProgress(player.getUniqueId(), "mine_blocks", 1);
        questManager.incrementProgress(player.getUniqueId(), "mine_" + blockType, 1);
    }
    
    @EventHandler
    public void onEntityKill(EntityDeathEvent event) {
        if (!plugin.getConfig().getBoolean("weeklyQuests.enabled", true)) return;
        if (event.getEntity().getKiller() == null) return;
        
        Player player = event.getEntity().getKiller();
        String entityType = event.getEntityType().name().toLowerCase();
        
        // Track kill quests
        questManager.incrementProgress(player.getUniqueId(), "kill_mobs", 1);
        questManager.incrementProgress(player.getUniqueId(), "kill_" + entityType, 1);
    }
    
    @EventHandler
    public void onFish(PlayerFishEvent event) {
        if (!plugin.getConfig().getBoolean("weeklyQuests.enabled", true)) return;
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        
        Player player = event.getPlayer();
        questManager.incrementProgress(player.getUniqueId(), "catch_fish", 1);
    }
    
    // Called by RuneService when a rune is rolled
    public void onRuneRoll(Player player, RuneTier tier) {
        if (!plugin.getConfig().getBoolean("weeklyQuests.enabled", true)) return;
        
        questManager.incrementProgress(player.getUniqueId(), "roll_runes", 1);
        questManager.incrementProgress(player.getUniqueId(), "roll_" + tier.getConfigKey(), 1);
    }
}
