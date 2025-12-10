package com.bubblecraft.bubblerune;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WeeklyQuestManager {
    private final BubbleRunePlugin plugin;
    private final Map<UUID, Map<String, Integer>> playerProgress = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> completedQuests = new ConcurrentHashMap<>();
    private long questResetTime = 0;
    
    public WeeklyQuestManager(BubbleRunePlugin plugin) {
        this.plugin = plugin;
        calculateNextReset();
        startResetTask();
    }
    
    private void calculateNextReset() {
        // Reset every Monday at midnight
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        
        // If it's already past Monday this week, go to next Monday
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.WEEK_OF_YEAR, 1);
        }
        
        questResetTime = cal.getTimeInMillis();
    }
    
    private void startResetTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (System.currentTimeMillis() >= questResetTime) {
                    resetAllQuests();
                    calculateNextReset();
                }
            }
        }.runTaskTimer(plugin, 20L * 60, 20L * 60); // Check every minute
    }
    
    private void resetAllQuests() {
        playerProgress.clear();
        completedQuests.clear();
        plugin.getLogger().info("Weekly rune quests have been reset!");
        
        // Notify online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(TextFormatter.format(
                plugin.getConfig().getString("weeklyQuests.messages.reset", 
                "&6&lWeekly Quests Reset! &eNew challenges await!")));
        }
    }
    
    public void incrementProgress(UUID playerId, String questId, int amount) {
        playerProgress.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                    .merge(questId, amount, Integer::sum);
        
        checkQuestCompletion(playerId, questId);
    }
    
    private void checkQuestCompletion(UUID playerId, String questId) {
        // Skip if already completed
        if (completedQuests.getOrDefault(playerId, Collections.emptySet()).contains(questId)) {
            return;
        }
        
        ConfigurationSection quest = plugin.getConfig().getConfigurationSection("weeklyQuests.quests." + questId);
        if (quest == null) return;
        
        int required = quest.getInt("required", 0);
        int current = playerProgress.getOrDefault(playerId, Collections.emptyMap()).getOrDefault(questId, 0);
        
        if (current >= required) {
            completeQuest(playerId, questId);
        }
    }
    
    private void completeQuest(UUID playerId, String questId) {
        completedQuests.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet()).add(questId);
        
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return;
        
        ConfigurationSection quest = plugin.getConfig().getConfigurationSection("weeklyQuests.quests." + questId);
        if (quest == null) return;
        
        String questName = quest.getString("name", questId);
        String rewardType = quest.getString("reward.type", "rune");
        
        // Grant reward
        if (rewardType.equalsIgnoreCase("rune")) {
            String tierName = quest.getString("reward.tier", "RARE");
            try {
                RuneTier tier = RuneTier.valueOf(tierName.toUpperCase());
                ItemStack rune = plugin.getRuneService().createRuneItem(tier);
                player.getInventory().addItem(rune);
                
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("weeklyQuests.messages.complete",
                    "&a&lQuest Complete! &f%quest%")
                    .replace("%quest%", questName)));
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&6Reward: &f" + tier.name() + " &6Rune"));
                
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                
                // Firework effect for quest completion
                player.getWorld().spawnParticle(Particle.FIREWORK, player.getLocation().add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.1);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid reward tier for quest " + questId + ": " + tierName);
            }
        }
    }
    
    public int getProgress(UUID playerId, String questId) {
        return playerProgress.getOrDefault(playerId, Collections.emptyMap()).getOrDefault(questId, 0);
    }
    
    public boolean isCompleted(UUID playerId, String questId) {
        return completedQuests.getOrDefault(playerId, Collections.emptySet()).contains(questId);
    }
    
    public long getTimeUntilReset() {
        return Math.max(0, questResetTime - System.currentTimeMillis());
    }
    
    public String getFormattedTimeUntilReset() {
        long ms = getTimeUntilReset();
        long days = ms / (1000 * 60 * 60 * 24);
        long hours = (ms % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60);
        
        if (days > 0) {
            return days + "d " + hours + "h";
        } else if (hours > 0) {
            return hours + "h";
        } else {
            return "< 1h";
        }
    }
    
    public List<String> getAllQuestIds() {
        ConfigurationSection questsSection = plugin.getConfig().getConfigurationSection("weeklyQuests.quests");
        if (questsSection == null) return Collections.emptyList();
        return new ArrayList<>(questsSection.getKeys(false));
    }
}
