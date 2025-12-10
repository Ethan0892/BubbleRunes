package com.bubblecraft.bubblerune;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class BubbleRuneCommand implements CommandExecutor, TabCompleter {
    private final BubbleRunePlugin plugin;

    public BubbleRuneCommand(BubbleRunePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "BubbleRune v" + plugin.getDescription().getVersion());
            sender.sendMessage(ChatColor.YELLOW + "Commands:");
            sender.sendMessage(ChatColor.GRAY + "  /" + label + " reload - Reload configuration");
            sender.sendMessage(ChatColor.GRAY + "  /" + label + " settable [name] - Set rune table location");
            sender.sendMessage(ChatColor.GRAY + "  /" + label + " gui - Open rune GUI");
            sender.sendMessage(ChatColor.GRAY + "  /" + label + " giverune <player> <tier> - Give a rune");
            sender.sendMessage(ChatColor.GRAY + "  /" + label + " testroll - Test tier rolling");
            sender.sendMessage(ChatColor.GRAY + "  /" + label + " quests - View weekly quests");
            sender.sendMessage(ChatColor.GRAY + "  /" + label + " stats - View your statistics");
            sender.sendMessage(ChatColor.GRAY + "  /" + label + " history [limit] - View roll history");
            sender.sendMessage(ChatColor.GRAY + "  /" + label + " leaderboard [limit] - View top rollers");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("bubblerune.admin")) {
                sender.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }
            plugin.reloadConfig();
            plugin.reloadRunesConfig();
            plugin.reloadConfigValues();
            if (plugin.getRuneService() != null) {
                plugin.getRuneService().reload();
            }
            sender.sendMessage(ChatColor.GREEN + "BubbleRune config and runes.yml reloaded.");
            return true;
        }

        if (args[0].equalsIgnoreCase("settable")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Only players can use this.");
                return true;
            }
            if (!sender.hasPermission("bubblerune.admin")) {
                sender.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }
            Player player = (Player) sender;
            Location loc = player.getLocation();
            
            // Determine table name
            String tableName = args.length >= 2 ? args[1] : "spawn";
            
            // Set the table location in runeTables section
            plugin.getConfig().set("runeTables." + tableName + ".world", loc.getWorld().getName());
            plugin.getConfig().set("runeTables." + tableName + ".x", loc.getBlockX());
            plugin.getConfig().set("runeTables." + tableName + ".y", loc.getBlockY());
            plugin.getConfig().set("runeTables." + tableName + ".z", loc.getBlockZ());
            plugin.saveConfig();
            plugin.reloadConfigValues();
            sender.sendMessage(ChatColor.GREEN + "Rune table '" + tableName + "' location set at your position.");
            return true;
        }

        if (args[0].equalsIgnoreCase("gui")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Only players can use this.");
                return true;
            }
            if (!sender.hasPermission("bubblerune.gui")) {
                sender.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }
            Player player = (Player) sender;
            
            if (!plugin.getConfig().getBoolean("gui.enabled", true)) {
                sender.sendMessage(ChatColor.RED + "Rune GUI is disabled.");
                return true;
            }
            
            // Open the GUI for the player
            plugin.getRuneTableGUI().openGUI(player, player.getLocation());
            return true;
        }

        if (args[0].equalsIgnoreCase("giverune")) {
            if (!sender.hasPermission("bubblerune.admin")) {
                sender.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }

            if (args.length < 3) {
                sender.sendMessage(ChatColor.RED + "Usage: /" + label + " giverune <player> <tier>");
                return true;
            }

            Player target = plugin.getServer().getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found.");
                return true;
            }

            RuneTier tier;
            try {
                tier = RuneTier.valueOf(args[2].toUpperCase());
            } catch (IllegalArgumentException ex) {
                sender.sendMessage(ChatColor.RED + "Invalid tier. Use: COMMON, UNCOMMON, RARE, LEGENDARY.");
                return true;
            }

            // Directly create a rune item of the requested tier, ignoring XP cost.
            RuneService runeService = plugin.getRuneService();
            target.getInventory().addItem(runeService.createRuneItem(tier));
            sender.sendMessage(ChatColor.GREEN + "Gave a " + tier.name().toLowerCase() + " rune to " + target.getName() + ".");
            return true;
        }

        if (args[0].equalsIgnoreCase("testroll")) {
            if (!sender.hasPermission("bubblerune.admin")) {
                sender.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }

            RuneTier tier = plugin.getRuneService().rollTier();
            sender.sendMessage(ChatColor.YELLOW + "Rolled tier: " + tier.name());
            return true;
        }

        if (args[0].equalsIgnoreCase("quests")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Only players can use this.");
                return true;
            }
            
            Player player = (Player) sender;
            WeeklyQuestManager questManager = plugin.getQuestManager();
            
            if (questManager == null || !plugin.getConfig().getBoolean("weeklyQuests.enabled", true)) {
                sender.sendMessage(ChatColor.RED + "Weekly quests are not enabled.");
                return true;
            }
            
            sender.sendMessage(TextFormatter.format("&6&l━━━━━━ Weekly Quests ━━━━━━"));
            sender.sendMessage(TextFormatter.format("&7Resets in: &e" + questManager.getFormattedTimeUntilReset()));
            sender.sendMessage("");
            
            for (String questId : questManager.getAllQuestIds()) {
                org.bukkit.configuration.ConfigurationSection quest = plugin.getConfig().getConfigurationSection("weeklyQuests.quests." + questId);
                if (quest == null) continue;
                
                String name = quest.getString("name", questId);
                String description = quest.getString("description", "");
                int required = quest.getInt("required", 0);
                int current = questManager.getProgress(player.getUniqueId(), questId);
                boolean completed = questManager.isCompleted(player.getUniqueId(), questId);
                String rewardTier = quest.getString("reward.tier", "RARE");
                
                if (completed) {
                    sender.sendMessage(TextFormatter.format("&a✔ &f" + name + " &7- &aCompleted!"));
                } else {
                    sender.sendMessage(TextFormatter.format("&7▪ &f" + name));
                    sender.sendMessage(TextFormatter.format("  &8" + description));
                    sender.sendMessage(TextFormatter.format("  &7Progress: &e" + current + "&7/&e" + required));
                    sender.sendMessage(TextFormatter.format("  &7Reward: &f" + rewardTier + " &7Rune"));
                }
                sender.sendMessage("");
            }
            
            return true;
        }

        if (args[0].equalsIgnoreCase("stats")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Only players can use this.");
                return true;
            }
            
            Player player = (Player) sender;
            
            if (plugin.getDatabaseManager() == null) {
                sender.sendMessage(ChatColor.RED + "Database not available.");
                return true;
            }
            
            // Fetch stats asynchronously
            org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    DatabaseManager.PlayerStats stats = plugin.getDatabaseManager().getPlayerStats(player.getUniqueId());
                    
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                        if (stats == null) {
                            sender.sendMessage(TextFormatter.format("&cYou haven't rolled any runes yet!"));
                            return;
                        }
                        
                        sender.sendMessage(TextFormatter.format("&6&l━━━━━━ Your Rune Statistics ━━━━━━"));
                        sender.sendMessage(TextFormatter.format("&7Total Rolls: &e" + stats.totalRolls));
                        sender.sendMessage(TextFormatter.format("&7Total XP Spent: &e" + stats.totalXpSpent));
                        sender.sendMessage(TextFormatter.format("&7Total Coins Spent: &e" + stats.totalCoinsSpent));
                        sender.sendMessage("");
                        sender.sendMessage(TextFormatter.format("&7Tier Breakdown:"));
                        sender.sendMessage(TextFormatter.format("  &fCommon: &7" + stats.commonRolls));
                        sender.sendMessage(TextFormatter.format("  &aUncommon: &7" + stats.uncommonRolls));
                        sender.sendMessage(TextFormatter.format("  &9Rare: &7" + stats.rareRolls));
                        sender.sendMessage(TextFormatter.format("  &5Epic: &7" + stats.epicRolls));
                        sender.sendMessage(TextFormatter.format("  &6Legendary: &7" + stats.legendaryRolls));
                        sender.sendMessage(TextFormatter.format("  &bSpecial: &7" + stats.specialRolls));
                        sender.sendMessage(TextFormatter.format("  &dVery Special: &7" + stats.verySpecialRolls));
                        sender.sendMessage(TextFormatter.format("&6&l━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                    });
                } catch (Exception e) {
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(TextFormatter.format("&cError loading stats: " + e.getMessage()));
                    });
                }
            });
            
            return true;
        }

        if (args[0].equalsIgnoreCase("history")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Only players can use this.");
                return true;
            }
            
            Player player = (Player) sender;
            
            if (plugin.getDatabaseManager() == null) {
                sender.sendMessage(ChatColor.RED + "Database not available.");
                return true;
            }
            
            int limit = 10;
            if (args.length >= 2) {
                try {
                    limit = Integer.parseInt(args[1]);
                    if (limit <= 0) limit = 10;
                    if (limit > 50) limit = 50;
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid number. Using default limit of 10.");
                }
            }
            
            final int finalLimit = limit;
            
            // Fetch history asynchronously
            org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    List<DatabaseManager.RollRecord> rolls = plugin.getDatabaseManager().getRecentRolls(player.getUniqueId(), finalLimit);
                    
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                        if (rolls.isEmpty()) {
                            sender.sendMessage(TextFormatter.format("&cYou haven't rolled any runes yet!"));
                            return;
                        }
                        
                        sender.sendMessage(TextFormatter.format("&6&l━━━━━━ Recent Rolls (" + rolls.size() + ") ━━━━━━"));
                        
                        for (DatabaseManager.RollRecord roll : rolls) {
                            String tierColor = getTierColor(roll.tier);
                            long timeAgo = System.currentTimeMillis() - roll.timestamp;
                            String timeStr = formatTimeAgo(timeAgo);
                            
                            sender.sendMessage(TextFormatter.format(
                                tierColor + roll.tier.name() + " &7- &f" + roll.enchantName + 
                                " &7(" + roll.xpCost + " XP, " + roll.coinCost + " coins) - &8" + timeStr
                            ));
                        }
                        
                        sender.sendMessage(TextFormatter.format("&6&l━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                    });
                } catch (Exception e) {
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(TextFormatter.format("&cError loading history: " + e.getMessage()));
                    });
                }
            });
            
            return true;
        }

        if (args[0].equalsIgnoreCase("leaderboard") || args[0].equalsIgnoreCase("top")) {
            if (!sender.hasPermission("bubblerune.leaderboard")) {
                sender.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }
            
            if (plugin.getDatabaseManager() == null) {
                sender.sendMessage(ChatColor.RED + "Database not available.");
                return true;
            }
            
            int limit = 10;
            if (args.length >= 2) {
                try {
                    limit = Integer.parseInt(args[1]);
                    if (limit <= 0) limit = 10;
                    if (limit > 25) limit = 25;
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid number. Using default limit of 10.");
                }
            }
            
            final int finalLimit = limit;
            
            // Fetch leaderboard asynchronously
            org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    List<DatabaseManager.PlayerStats> topPlayers = plugin.getDatabaseManager().getTopPlayers(finalLimit);
                    
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(TextFormatter.format("&6&l━━━━━━ Top Rune Rollers ━━━━━━"));
                        
                        int rank = 1;
                        for (DatabaseManager.PlayerStats stats : topPlayers) {
                            String rankColor = rank <= 3 ? "&6" : "&7";
                            sender.sendMessage(TextFormatter.format(
                                rankColor + "#" + rank + " &f" + stats.playerName + 
                                " &7- &e" + stats.totalRolls + " rolls &7(" + stats.totalXpSpent + " XP)"
                            ));
                            rank++;
                        }
                        
                        sender.sendMessage(TextFormatter.format("&6&l━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                    });
                } catch (Exception e) {
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(TextFormatter.format("&cError loading leaderboard: " + e.getMessage()));
                    });
                }
            });
            
            return true;
        }

        sender.sendMessage(ChatColor.RED + "Unknown subcommand.");
        return true;
    }
    
    private String getTierColor(RuneTier tier) {
        switch (tier) {
            case COMMON: return "&f";
            case UNCOMMON: return "&a";
            case RARE: return "&9";
            case EPIC: return "&5";
            case LEGENDARY: return "&6";
            case SPECIAL: return "&b";
            case VERYSPECIAL: return "&d";
            default: return "&7";
        }
    }
    
    private String formatTimeAgo(long millis) {
        long seconds = millis / 1000;
        if (seconds < 60) return seconds + "s ago";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m ago";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h ago";
        long days = hours / 24;
        return days + "d ago";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("bubblerune")) {
            return Collections.emptyList();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> options = Arrays.asList("reload", "settable", "gui", "giverune", "testroll", "quests", "stats", "history", "leaderboard", "top");
            String current = args[0].toLowerCase();
            for (String opt : options) {
                if (opt.startsWith(current)) {
                    completions.add(opt);
                }
            }
            return completions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("settable")) {
            // Tab complete table names from config
            String current = args[1].toLowerCase();
            org.bukkit.configuration.ConfigurationSection tables = plugin.getConfig().getConfigurationSection("runeTables");
            if (tables != null) {
                for (String key : tables.getKeys(false)) {
                    if (key.toLowerCase().startsWith(current)) {
                        completions.add(key);
                    }
                }
            }
            // Add default option
            if ("spawn".startsWith(current)) {
                completions.add("spawn");
            }
            return completions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("giverune")) {
            // Tab complete online player names
            String current = args[1].toLowerCase();
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(current)) {
                    completions.add(player.getName());
                }
            }
            return completions;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("giverune")) {
            // Tab complete tier names
            String current = args[2].toLowerCase();
            for (RuneTier tier : RuneTier.values()) {
                String tierName = tier.name().toLowerCase();
                if (tierName.startsWith(current)) {
                    completions.add(tierName);
                }
            }
            return completions;
        }

        return completions;
    }
}
