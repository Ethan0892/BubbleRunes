package com.bubblecraft.bubblerune;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class BubbleRunePlugin extends JavaPlugin {
    private static BubbleRunePlugin instance;
    private List<Location> runeTableLocations = new ArrayList<>();
    private RuneService runeService;
    private CooldownManager cooldownManager;
    private StatsManager statsManager;
    private WeeklyQuestManager questManager;
    private QuestTrackingListener questListener;
    private FileConfiguration runesConfig;
    private FileConfiguration messagesConfig;
    private RuneTableGUI runeTableGUI;
    private DatabaseManager databaseManager;
    private PlaceholderStatsCache placeholderStatsCache;
    private org.bukkit.scheduler.BukkitTask placeholderRefreshTask;
    private boolean debugEnabled;

    @Override
    public void onEnable() {
        instance = this;
        
        try {
            saveDefaultConfig();
            loadRunesConfig();
            loadMessagesConfig();
            reloadConfigValues();
        } catch (Exception e) {
            getLogger().severe("Failed to load configuration: " + e.getMessage());
            e.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        try {
            // Initialize database
            databaseManager = new DatabaseManager(this);
            databaseManager.initialize();

            placeholderStatsCache = new PlaceholderStatsCache(this);
            
            runeService = new RuneService(this);
            cooldownManager = new CooldownManager(getConfig().getInt("cooldown.seconds", 60));
            statsManager = new StatsManager();
            
            // Initialize weekly quests if enabled
            if (getConfig().getBoolean("weeklyQuests.enabled", true)) {
                questManager = new WeeklyQuestManager(this);
                questListener = new QuestTrackingListener(this, questManager);
                Bukkit.getPluginManager().registerEvents(questListener, this);
            }
            
            runeTableGUI = new RuneTableGUI(this);

            Bukkit.getPluginManager().registerEvents(new RuneTableListener(this, runeTableGUI), this);
            Bukkit.getPluginManager().registerEvents(new RuneGUIListener(this, runeService), this);
            Bukkit.getPluginManager().registerEvents(new RuneItemListener(this, runeService), this);
            Bukkit.getPluginManager().registerEvents(new RuneCombineListener(this, runeService), this);
            Bukkit.getPluginManager().registerEvents(new RuneCraftingCombineListener(this, runeService), this);
        } catch (Exception e) {
            getLogger().severe("Failed to initialize plugin components: " + e.getMessage());
            e.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        try {
            BubbleRuneCommand commandExecutor = new BubbleRuneCommand(this);
            if (getCommand("bubblerune") != null) {
                getCommand("bubblerune").setExecutor(commandExecutor);
                getCommand("bubblerune").setTabCompleter(commandExecutor);
            }
        } catch (Exception e) {
            getLogger().severe("Failed to register commands: " + e.getMessage());
            e.printStackTrace();
        }

        // Register PlaceholderAPI expansion if available
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                boolean registeredAny = false;

                // Register both identifiers for user friendliness / backwards compatibility.
                BubbleRunePlaceholderExpansion singular = new BubbleRunePlaceholderExpansion(this, "bubblerune");
                registeredAny |= singular.register();

                BubbleRunePlaceholderExpansion plural = new BubbleRunePlaceholderExpansion(this, "bubblerunes");
                registeredAny |= plural.register();

                if (registeredAny) {
                    getLogger().info("PlaceholderAPI expansion registered!");
                } else {
                    getLogger().warning("PlaceholderAPI expansion was not registered (may already be registered or not supported).");
                }
            } catch (Exception e) {
                getLogger().warning("Failed to register PlaceholderAPI expansion: " + e.getMessage());
            } catch (NoClassDefFoundError e) {
                // PlaceholderAPI present but classes not loadable (shaded/classloader edge case)
                getLogger().warning("Failed to register PlaceholderAPI expansion (missing classes): " + e.getMessage());
            }
        }

        // Start placeholder refresh task (used by PlaceholderAPI expansions)
        restartPlaceholderRefreshTask();
        
        // Start periodic cleanup tasks
        startCleanupTasks();
        
        getLogger().info("BubbleRune v" + getDescription().getVersion() + " enabled successfully!");
    }
    
    private void startCleanupTasks() {
        // Cleanup expired cooldowns every 5 minutes
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            try {
                if (cooldownManager != null) {
                    cooldownManager.cleanupExpired();
                }
            } catch (Exception e) {
                getLogger().warning("Error during cooldown cleanup: " + e.getMessage());
            }
        }, 6000L, 6000L); // 5 minutes = 6000 ticks
    }

    @Override
    public void onDisable() {
        if (placeholderRefreshTask != null) {
            placeholderRefreshTask.cancel();
            placeholderRefreshTask = null;
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("BubbleRune disabled.");
    }

    public static BubbleRunePlugin getInstance() {
        return instance;
    }

    public void reloadConfigValues() {
        FileConfiguration cfg = getConfig();
        runeTableLocations.clear();

        debugEnabled = cfg.getBoolean("debug", false);

        // Support multiple tables
        ConfigurationSection tablesSection = cfg.getConfigurationSection("runeTables");
        if (tablesSection != null) {
            for (String key : tablesSection.getKeys(false)) {
                String worldName = cfg.getString("runeTables." + key + ".world");
                double x = cfg.getDouble("runeTables." + key + ".x");
                double y = cfg.getDouble("runeTables." + key + ".y");
                double z = cfg.getDouble("runeTables." + key + ".z");
                
                if (worldName != null && Bukkit.getWorld(worldName) != null) {
                    Location loc = new Location(Bukkit.getWorld(worldName), x, y, z);
                    runeTableLocations.add(loc);
                }
            }
        }
        
        // Fallback to single table for backwards compatibility
        if (runeTableLocations.isEmpty() && cfg.isConfigurationSection("runeTable")) {
            String worldName = cfg.getString("runeTable.world");
            double x = cfg.getDouble("runeTable.x");
            double y = cfg.getDouble("runeTable.y");
            double z = cfg.getDouble("runeTable.z");
            
            if (worldName != null && Bukkit.getWorld(worldName) != null) {
                Location loc = new Location(Bukkit.getWorld(worldName), x, y, z);
                runeTableLocations.add(loc);
            }
        }

        // Reload cooldown settings
        if (cooldownManager != null) {
            cooldownManager.setCooldownSeconds(cfg.getInt("cooldown.seconds", 60));
        }

        restartPlaceholderRefreshTask();
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    /**
     * Updates the debug flag in-memory and in config.yml.
     * Callers should use this rather than setting config directly.
     */
    public void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
        getConfig().set("debug", enabled);
        saveConfig();
    }

    public PlaceholderStatsCache getPlaceholderStatsCache() {
        return placeholderStatsCache;
    }

    private void restartPlaceholderRefreshTask() {
        if (!isEnabled()) return;
        if (placeholderStatsCache == null) return;
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) return;

        if (placeholderRefreshTask != null) {
            placeholderRefreshTask.cancel();
            placeholderRefreshTask = null;
        }

        long refreshSeconds = getConfig().getLong("placeholders.refreshSeconds", 300L);
        if (refreshSeconds < 5L) refreshSeconds = 5L;
        long refreshTicks = refreshSeconds * 20L;

        // Prime cache immediately.
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                placeholderStatsCache.refresh();
            } catch (Exception e) {
                getLogger().warning("Placeholder refresh priming error: " + e.getMessage());
            }
        });

        // Initial refresh quickly, then every interval.
        placeholderRefreshTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            try {
                placeholderStatsCache.refresh();
            } catch (Exception e) {
                getLogger().warning("Placeholder refresh task error: " + e.getMessage());
            }
        }, 20L, refreshTicks);
    }

    public List<Location> getRuneTableLocations() {
        return runeTableLocations;
    }
    
    @Deprecated
    public Location getRuneTableLocation() {
        return runeTableLocations.isEmpty() ? null : runeTableLocations.get(0);
    }

    public RuneService getRuneService() {
        return runeService;
    }
    
    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }
    
    public StatsManager getStatsManager() {
        return statsManager;
    }
    
    public WeeklyQuestManager getQuestManager() {
        return questManager;
    }
    
    public QuestTrackingListener getQuestListener() {
        return questListener;
    }
    
    public FileConfiguration getRunesConfig() {
        return runesConfig;
    }

    public FileConfiguration getMessagesConfig() {
        return messagesConfig;
    }

    /**
     * Reads a message from messages.yml first, then falls back to config.yml.
     */
    public String getMessage(String path, String defaultValue) {
        if (messagesConfig != null && messagesConfig.contains(path)) {
            return messagesConfig.getString(path, defaultValue);
        }
        return getConfig().getString(path, defaultValue);
    }
    
    public RuneTableGUI getRuneTableGUI() {
        return runeTableGUI;
    }
    
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
    
    private void loadRunesConfig() {
        File runesFile = new File(getDataFolder(), "runes.yml");
        if (!runesFile.exists()) {
            saveResource("runes.yml", false);
        }
        runesConfig = YamlConfiguration.loadConfiguration(runesFile);
        getLogger().info("Loaded runes.yml configuration");
    }
    
    public void reloadRunesConfig() {
        File runesFile = new File(getDataFolder(), "runes.yml");
        if (runesFile.exists()) {
            runesConfig = YamlConfiguration.loadConfiguration(runesFile);
            getLogger().info("Reloaded runes.yml configuration");
        }
    }

    private void loadMessagesConfig() {
        File messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        getLogger().info("Loaded messages.yml configuration");
    }

    public void reloadMessagesConfig() {
        File messagesFile = new File(getDataFolder(), "messages.yml");
        if (messagesFile.exists()) {
            messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
            getLogger().info("Reloaded messages.yml configuration");
        }
    }
}
