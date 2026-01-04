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
            String help = plugin.getMessage(
                "command.help",
                "&eBubbleRune v%version%\n" +
                "&eCommands:\n" +
                "&7  /%label% reload - Reload configuration\n" +
                "&7  /%label% settable [name] - Set rune table location\n" +
                "&7  /%label% gui - Open rune GUI\n" +
                "&7  /%label% giverune <player> <tier> - Give a rune\n" +
                "&7  /%label% testroll - Test tier rolling\n" +
                "&7  /%label% quests - View weekly quests\n" +
                "&7  /%label% debug [on|off|toggle] - Toggle debug logging\n" +
                "&7  /%label% stats - View your statistics\n" +
                "&7  /%label% history [limit] - View roll history\n" +
                "&7  /%label% leaderboard [limit] - View top rollers");
            help = help
                .replace("%version%", plugin.getDescription().getVersion())
                .replace("%label%", label);
            for (String line : help.split("\\n")) {
                sender.sendMessage(TextFormatter.format(line));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("bubblerune.admin")) {
                sender.sendMessage(TextFormatter.format(plugin.getMessage(
                    "command.noPermission",
                    "&cNo permission.")));
                return true;
            }
            plugin.reloadConfig();
            plugin.reloadRunesConfig();
            plugin.reloadMessagesConfig();
            plugin.reloadConfigValues();
            if (plugin.getRuneService() != null) {
                plugin.getRuneService().reload();
            }
            sender.sendMessage(TextFormatter.format(plugin.getMessage(
                "command.reloadSuccess",
                "&aBubbleRune config, runes.yml, and messages.yml reloaded.")));
            return true;
        }

        if (args[0].equalsIgnoreCase("debug")) {
            if (!sender.hasPermission("bubblerune.admin") && !sender.hasPermission("bubblerune.debug")) {
                sender.sendMessage(TextFormatter.format(plugin.getMessage(
                    "command.noPermission",
                    "&cNo permission.")));
                return true;
            }

            boolean newValue;
            if (args.length < 2 || args[1].equalsIgnoreCase("toggle")) {
                newValue = !plugin.isDebugEnabled();
            } else if (args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("true") || args[1].equalsIgnoreCase("enable")) {
                newValue = true;
            } else if (args[1].equalsIgnoreCase("off") || args[1].equalsIgnoreCase("false") || args[1].equalsIgnoreCase("disable")) {
                newValue = false;
            } else {
                String usage = plugin.getMessage(
                    "command.debugUsage",
                    "&cUsage: /%label% debug [on|off|toggle]"
                );
                sender.sendMessage(TextFormatter.format(usage.replace("%label%", label)));
                return true;
            }

            plugin.setDebugEnabled(newValue);

            String msg = plugin.getMessage(
                "command.debugStatus",
                "&aBubbleRune debug is now: &f%value%"
            );
            sender.sendMessage(TextFormatter.format(msg.replace("%value%", String.valueOf(newValue))));
            return true;
        }

        if (args[0].equalsIgnoreCase("settable")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(TextFormatter.format(plugin.getMessage(
                    "command.onlyPlayers",
                    "&cOnly players can use this.")));
                return true;
            }
            if (!sender.hasPermission("bubblerune.admin")) {
                sender.sendMessage(TextFormatter.format(plugin.getMessage(
                    "command.noPermission",
                    "&cNo permission.")));
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
            String setMsg = plugin.getMessage(
                "command.tableSet",
                "&aRune table '&f%name%&a' location set at your position.");
            sender.sendMessage(TextFormatter.format(setMsg.replace("%name%", tableName)));
            return true;
        }

        if (args[0].equalsIgnoreCase("gui")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(TextFormatter.format(plugin.getMessage(
                    "command.onlyPlayers",
                    "&cOnly players can use this.")));
                return true;
            }
            if (!sender.hasPermission("bubblerune.gui")) {
                sender.sendMessage(TextFormatter.format(plugin.getMessage(
                    "command.noPermission",
                    "&cNo permission.")));
                return true;
            }
            Player player = (Player) sender;
            
            if (!plugin.getConfig().getBoolean("gui.enabled", true)) {
                sender.sendMessage(TextFormatter.format(plugin.getMessage(
                    "command.guiDisabled",
                    "&cRune GUI is disabled.")));
                return true;
            }
            
            // Open the GUI for the player
            plugin.getRuneTableGUI().openGUI(player, player.getLocation());
            return true;
        }

        if (args[0].equalsIgnoreCase("giverune")) {
            if (!sender.hasPermission("bubblerune.admin")) {
                sender.sendMessage(TextFormatter.format(plugin.getMessage(
                    "command.noPermission",
                    "&cNo permission.")));
                return true;
            }

            if (args.length < 3) {
                String usage = plugin.getMessage(
                    "command.giveruneUsage",
                    "&cUsage: /%label% giverune <player> <tier>");
                sender.sendMessage(TextFormatter.format(usage.replace("%label%", label)));
                return true;
            }

            Player target = plugin.getServer().getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(TextFormatter.format(plugin.getMessage(
                    "command.playerNotFound",
                    "&cPlayer not found.")));
                return true;
            }

            RuneTier tier;
            try {
                tier = RuneTier.valueOf(args[2].toUpperCase());
            } catch (IllegalArgumentException ex) {
                sender.sendMessage(TextFormatter.format(plugin.getMessage(
                    "command.invalidTier",
                    "&cInvalid tier.")));
                return true;
            }

            // Directly create a rune item of the requested tier, ignoring XP cost.
            RuneService runeService = plugin.getRuneService();
            target.getInventory().addItem(runeService.createRuneItem(tier));
            String gave = plugin.getMessage(
                "command.gaveRune",
                "&aGave a &f%tier% &arune to &f%player%&a.");
            sender.sendMessage(TextFormatter.format(
                gave.replace("%tier%", tier.name().toLowerCase()).replace("%player%", target.getName())
            ));
            return true;
        }

        if (args[0].equalsIgnoreCase("testroll")) {
            if (!sender.hasPermission("bubblerune.admin")) {
                sender.sendMessage(TextFormatter.format(plugin.getMessage(
                    "command.noPermission",
                    "&cNo permission.")));
                return true;
            }

            RuneTier tier = plugin.getRuneService().rollTier();
            String rolled = plugin.getMessage(
                "command.testrollResult",
                "&eRolled tier: &f%tier%");
            sender.sendMessage(TextFormatter.format(rolled.replace("%tier%", tier.name())));
            return true;
        }

        if (args[0].equalsIgnoreCase("quests")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(TextFormatter.format(plugin.getMessage(
                    "command.onlyPlayers",
                    "&cOnly players can use this.")));
                return true;
            }
            
            Player player = (Player) sender;
            WeeklyQuestManager questManager = plugin.getQuestManager();
            
            if (questManager == null || !plugin.getConfig().getBoolean("weeklyQuests.enabled", true)) {
                sender.sendMessage(TextFormatter.format(plugin.getMessage(
                    "command.weeklyQuestsDisabled",
                    "&cWeekly quests are not enabled.")));
                return true;
            }

            sender.sendMessage(TextFormatter.format(plugin.getMessage(
                "command.weeklyQuestsHeader",
                "&6&l━━━━━━ Weekly Quests ━━━━━━")));
            String resets = plugin.getMessage(
                "command.weeklyQuestsResetsIn",
                "&7Resets in: &e%time%");
            sender.sendMessage(TextFormatter.format(resets.replace("%time%", questManager.getFormattedTimeUntilReset())));
            sender.sendMessage(TextFormatter.format(plugin.getMessage("command.blankLine", "")));
            
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
                    String line = plugin.getMessage(
                        "command.weeklyQuestCompletedLine",
                        "&a✔ &f%name% &7- &aCompleted!");
                    sender.sendMessage(TextFormatter.format(line.replace("%name%", name)));
                } else {
                    String line1 = plugin.getMessage(
                        "command.weeklyQuestLine",
                        "&7▪ &f%name%");
                    sender.sendMessage(TextFormatter.format(line1.replace("%name%", name)));

                    String line2 = plugin.getMessage(
                        "command.weeklyQuestDescriptionLine",
                        "  &8%description%");
                    sender.sendMessage(TextFormatter.format(line2.replace("%description%", description)));

                    String line3 = plugin.getMessage(
                        "command.weeklyQuestProgressLine",
                        "  &7Progress: &e%current%&7/&e%required%");
                    sender.sendMessage(TextFormatter.format(line3
                        .replace("%current%", String.valueOf(current))
                        .replace("%required%", String.valueOf(required))
                    ));

                    String line4 = plugin.getMessage(
                        "command.weeklyQuestRewardLine",
                        "  &7Reward: &f%rewardTier% &7Rune");
                    sender.sendMessage(TextFormatter.format(line4.replace("%rewardTier%", rewardTier)));
                }
                sender.sendMessage(TextFormatter.format(plugin.getMessage("command.blankLine", "")));
            }
            
            return true;
        }

        if (args[0].equalsIgnoreCase("stats")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(TextFormatter.format(plugin.getMessage(
                    "command.onlyPlayers",
                    "&cOnly players can use this.")));
                return true;
            }
            
            Player player = (Player) sender;
            
            if (plugin.getDatabaseManager() == null) {
                sender.sendMessage(TextFormatter.format(plugin.getMessage(
                    "command.databaseNotAvailable",
                    "&cDatabase not available.")));
                return true;
            }
            
            // Fetch stats asynchronously
            org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    DatabaseManager.PlayerStats stats = plugin.getDatabaseManager().getPlayerStats(player.getUniqueId());
                    
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                        if (stats == null) {
                            sender.sendMessage(TextFormatter.format(plugin.getMessage(
                                "command.noRollsYet",
                                "&cYou haven't rolled any runes yet!")));
                            return;
                        }

                        sender.sendMessage(TextFormatter.format(plugin.getMessage(
                            "command.statsHeader",
                            "&6&l━━━━━━ Your Rune Statistics ━━━━━━")));

                        sender.sendMessage(TextFormatter.format(plugin.getMessage(
                            "command.statsTotalRolls",
                            "&7Total Rolls: &e%value%"
                        ).replace("%value%", String.valueOf(stats.totalRolls))));

                        sender.sendMessage(TextFormatter.format(plugin.getMessage(
                            "command.statsTotalXpSpent",
                            "&7Total XP Spent: &e%value%"
                        ).replace("%value%", String.valueOf(stats.totalXpSpent))));

                        sender.sendMessage(TextFormatter.format(plugin.getMessage(
                            "command.statsTotalCoinsSpent",
                            "&7Total Coins Spent: &e%value%"
                        ).replace("%value%", String.valueOf(stats.totalCoinsSpent))));

                        sender.sendMessage(TextFormatter.format(plugin.getMessage("command.blankLine", "")));

                        sender.sendMessage(TextFormatter.format(plugin.getMessage(
                            "command.statsTierBreakdownHeader",
                            "&7Tier Breakdown:")));

                        sender.sendMessage(TextFormatter.format(plugin.getMessage(
                            "command.statsTierCommon",
                            "  &fCommon: &7%value%"
                        ).replace("%value%", String.valueOf(stats.commonRolls))));

                        sender.sendMessage(TextFormatter.format(plugin.getMessage(
                            "command.statsTierUncommon",
                            "  &aUncommon: &7%value%"
                        ).replace("%value%", String.valueOf(stats.uncommonRolls))));

                        sender.sendMessage(TextFormatter.format(plugin.getMessage(
                            "command.statsTierRare",
                            "  &9Rare: &7%value%"
                        ).replace("%value%", String.valueOf(stats.rareRolls))));

                        sender.sendMessage(TextFormatter.format(plugin.getMessage(
                            "command.statsTierEpic",
                            "  &5Epic: &7%value%"
                        ).replace("%value%", String.valueOf(stats.epicRolls))));

                        sender.sendMessage(TextFormatter.format(plugin.getMessage(
                            "command.statsTierLegendary",
                            "  &6Legendary: &7%value%"
                        ).replace("%value%", String.valueOf(stats.legendaryRolls))));

                        sender.sendMessage(TextFormatter.format(plugin.getMessage(
                            "command.statsTierSpecial",
                            "  &bSpecial: &7%value%"
                        ).replace("%value%", String.valueOf(stats.specialRolls))));

                        sender.sendMessage(TextFormatter.format(plugin.getMessage(
                            "command.statsTierVerySpecial",
                            "  &dVery Special: &7%value%"
                        ).replace("%value%", String.valueOf(stats.verySpecialRolls))));

                        sender.sendMessage(TextFormatter.format(plugin.getMessage(
                            "command.sectionFooter",
                            "&6&l━━━━━━━━━━━━━━━━━━━━━━━━━━")));
                    });
                } catch (Exception e) {
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                        String err = plugin.getMessage(
                            "command.errorLoadingStats",
                            "&cError loading stats: %error%");
                        sender.sendMessage(TextFormatter.format(err.replace("%error%", e.getMessage())));
                    });
                }
            });
            
            return true;
        }

        if (args[0].equalsIgnoreCase("history")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(TextFormatter.format(plugin.getMessage(
                    "command.onlyPlayers",
                    "&cOnly players can use this.")));
                return true;
            }
            
            Player player = (Player) sender;
            
            if (plugin.getDatabaseManager() == null) {
                sender.sendMessage(TextFormatter.format(plugin.getMessage(
                    "command.databaseNotAvailable",
                    "&cDatabase not available.")));
                return true;
            }
            
            int limit = 10;
            if (args.length >= 2) {
                try {
                    limit = Integer.parseInt(args[1]);
                    if (limit <= 0) limit = 10;
                    if (limit > 50) limit = 50;
                } catch (NumberFormatException e) {
                    sender.sendMessage(TextFormatter.format(plugin.getMessage(
                        "command.invalidNumberUsingDefaultLimit",
                        "&cInvalid number. Using default limit of 10.")));
                }
            }
            
            final int finalLimit = limit;
            
            // Fetch history asynchronously
            org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    List<DatabaseManager.RollRecord> rolls = plugin.getDatabaseManager().getRecentRolls(player.getUniqueId(), finalLimit);
                    
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                        if (rolls.isEmpty()) {
                            sender.sendMessage(TextFormatter.format(plugin.getMessage(
                                "command.noRollsYet",
                                "&cYou haven't rolled any runes yet!")));
                            return;
                        }

                        String header = plugin.getMessage(
                            "command.historyHeader",
                            "&6&l━━━━━━ Recent Rolls (%count%) ━━━━━━");
                        sender.sendMessage(TextFormatter.format(header.replace("%count%", String.valueOf(rolls.size()))));
                        
                        for (DatabaseManager.RollRecord roll : rolls) {
                            String tierColor = getTierColor(roll.tier);
                            long timeAgo = System.currentTimeMillis() - roll.timestamp;
                            String timeStr = formatTimeAgo(timeAgo);
                            
                            sender.sendMessage(TextFormatter.format(
                                tierColor + roll.tier.name() + " &7- &f" + roll.enchantName + 
                                " &7(" + roll.xpCost + " XP, " + roll.coinCost + " coins) - &8" + timeStr
                            ));
                        }

                        sender.sendMessage(TextFormatter.format(plugin.getMessage(
                            "command.sectionFooter",
                            "&6&l━━━━━━━━━━━━━━━━━━━━━━━━━━")));
                    });
                } catch (Exception e) {
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                        String err = plugin.getMessage(
                            "command.errorLoadingHistory",
                            "&cError loading history: %error%");
                        sender.sendMessage(TextFormatter.format(err.replace("%error%", e.getMessage())));
                    });
                }
            });
            
            return true;
        }

        if (args[0].equalsIgnoreCase("leaderboard") || args[0].equalsIgnoreCase("top")) {
            if (!sender.hasPermission("bubblerune.leaderboard")) {
                sender.sendMessage(TextFormatter.format(plugin.getMessage(
                    "command.noPermission",
                    "&cNo permission.")));
                return true;
            }
            
            if (plugin.getDatabaseManager() == null) {
                sender.sendMessage(TextFormatter.format(plugin.getMessage(
                    "command.databaseNotAvailable",
                    "&cDatabase not available.")));
                return true;
            }
            
            int limit = 10;
            if (args.length >= 2) {
                try {
                    limit = Integer.parseInt(args[1]);
                    if (limit <= 0) limit = 10;
                    if (limit > 25) limit = 25;
                } catch (NumberFormatException e) {
                    sender.sendMessage(TextFormatter.format(plugin.getMessage(
                        "command.invalidNumberUsingDefaultLimit",
                        "&cInvalid number. Using default limit of 10.")));
                }
            }
            
            final int finalLimit = limit;
            
            // Fetch leaderboard asynchronously
            org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    List<DatabaseManager.PlayerStats> topPlayers = plugin.getDatabaseManager().getTopPlayers(finalLimit);
                    
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(TextFormatter.format(plugin.getMessage(
                            "command.leaderboardHeader",
                            "&6&l━━━━━━ Top Rune Rollers ━━━━━━")));
                        
                        int rank = 1;
                        for (DatabaseManager.PlayerStats stats : topPlayers) {
                            String rankColor = rank <= 3 ? "&6" : "&7";
                            sender.sendMessage(TextFormatter.format(
                                rankColor + "#" + rank + " &f" + stats.playerName + 
                                " &7- &e" + stats.totalRolls + " rolls &7(" + stats.totalXpSpent + " XP)"
                            ));
                            rank++;
                        }
                        
                        sender.sendMessage(TextFormatter.format(plugin.getMessage(
                            "command.sectionFooter",
                            "&6&l━━━━━━━━━━━━━━━━━━━━━━━━━━")));
                    });
                } catch (Exception e) {
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                        String err = plugin.getMessage(
                            "command.errorLoadingLeaderboard",
                            "&cError loading leaderboard: %error%");
                        sender.sendMessage(TextFormatter.format(err.replace("%error%", e.getMessage())));
                    });
                }
            });
            
            return true;
        }

        sender.sendMessage(TextFormatter.format(plugin.getMessage(
            "command.unknownSubcommand",
            "&cUnknown subcommand.")));
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
            List<String> options = Arrays.asList("reload", "settable", "gui", "giverune", "testroll", "quests", "debug", "stats", "history", "leaderboard", "top");
            String current = args[0].toLowerCase();
            for (String opt : options) {
                if (opt.startsWith(current)) {
                    completions.add(opt);
                }
            }
            return completions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("debug")) {
            String current = args[1].toLowerCase();
            for (String opt : Arrays.asList("on", "off", "toggle")) {
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
