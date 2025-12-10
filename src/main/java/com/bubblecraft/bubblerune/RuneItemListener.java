package com.bubblecraft.bubblerune;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Random;

public class RuneItemListener implements Listener {
    private final BubbleRunePlugin plugin;
    private final RuneService runeService;
    private final Random random = new Random();

    public RuneItemListener(BubbleRunePlugin plugin, RuneService runeService) {
        this.plugin = plugin;
        this.runeService = runeService;
        checkEcoEnchants();
    }

    private void checkEcoEnchants() {
        try {
            Class.forName("com.willfp.ecoenchants.EcoEnchantsPlugin");
            plugin.getLogger().info("EcoEnchants detected - custom enchantments enabled.");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().info("EcoEnchants not found - using vanilla enchantments only.");
        }
    }

    @EventHandler
    public void onRightClickRune(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) return;

        // Check if it's a preview rune first
        if (plugin.getConfig().getBoolean("runePreview.enabled", true)) {
            RunePreviewService previewService = new RunePreviewService(plugin);
            if (previewService.isPreviewRune(item)) {
                String enchantId = previewService.getEnchantIdFromPreview(item);
                if (enchantId != null) {
                    event.setCancelled(true);
                    revealPreviewRune(player, item, enchantId);
                    return;
                }
            }
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;

        String rawName = ChatColor.stripColor(meta.getDisplayName()).toLowerCase();
        RuneTier tier = null;
        for (RuneTier t : RuneTier.values()) {
            if (rawName.contains(t.name().toLowerCase())) {
                tier = t;
                break;
            }
        }
        if (tier == null) return;

        event.setCancelled(true);

        // Determine a random enchant ID for this tier from config.
        String enchantId = runeService.getRandomEnchantIdForTier(tier);
        if (enchantId == null) {
            player.sendMessage(ChatColor.RED + "No enchants configured for this rune tier.");
            return;
        }

        // Get level range from config for this tier
        int minLevel = plugin.getConfig().getInt("tiers." + tier.name().toLowerCase() + ".minLevel", 1);
        int maxLevel = plugin.getConfig().getInt("tiers." + tier.name().toLowerCase() + ".maxLevel", 1);
        if (minLevel < 1) minLevel = 1;
        if (maxLevel < minLevel) maxLevel = minLevel;

        ItemStack book = createEnchantedBook(enchantId, minLevel, maxLevel);
        if (book == null) {
            player.sendMessage(ChatColor.RED + "Could not create enchanted book for: " + enchantId);
            return;
        }

        // Consume one rune item
        item.setAmount(item.getAmount() - 1);

        // Give the book to the player
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(book);
        for (ItemStack it : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), it);
        }

        String msg = plugin.getConfig().getString("messages.runeRevealed", "&aYour rune revealed a &f%enchant% &abook!");
        msg = msg.replace("%enchant%", enchantId);
        player.sendMessage(TextFormatter.format(msg));
        
        // Play reveal sound
        if (plugin.getConfig().getBoolean("sounds.enabled", true)) {
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
        }
    }

    private ItemStack createEnchantedBook(String enchantId, int minLevel, int maxLevel) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta bookMeta = (EnchantmentStorageMeta) book.getItemMeta();
        if (bookMeta == null) return null;

        Enchantment enchantment = findEnchantment(enchantId);

        if (enchantment == null) {
            plugin.getLogger().warning("Enchantment not found: " + enchantId + 
                " (make sure the ID matches exactly - check EcoEnchants config files for the correct ID)");
            return null;
        }

        int enchantMaxLevel = enchantment.getMaxLevel();

        // Clamp level range to actual enchantment max
        if (maxLevel > enchantMaxLevel) maxLevel = enchantMaxLevel;
        if (minLevel > maxLevel) minLevel = maxLevel;
        if (minLevel < 1) minLevel = 1;

        int level = (minLevel == maxLevel) ? minLevel : minLevel + random.nextInt(maxLevel - minLevel + 1);

        bookMeta.addStoredEnchant(enchantment, level, true);
        book.setItemMeta(bookMeta);

        return book;
    }

    /**
     * Find an enchantment by ID. EcoEnchants registers its enchantments into the Bukkit Registry,
     * so we can look them up via NamespacedKey. Tries both minecraft: and ecoenchants: namespaces.
     */
    private Enchantment findEnchantment(String enchantId) {
        String id = enchantId.toLowerCase().replace(" ", "_");
        
        // Try minecraft namespace first (vanilla enchants)
        NamespacedKey vanillaKey = NamespacedKey.minecraft(id);
        Enchantment enchantment = Registry.ENCHANTMENT.get(vanillaKey);
        if (enchantment != null) {
            return enchantment;
        }

        // EcoEnchants uses minecraft namespace for custom enchants too
        // Iterate through registry to find by key name
        for (Enchantment ench : Registry.ENCHANTMENT) {
            if (ench.getKey().getKey().equalsIgnoreCase(id)) {
                return ench;
            }
        }

        return null;
    }
    
    private void revealPreviewRune(Player player, ItemStack previewItem, String enchantId) {
        // Extract tier from the preview rune
        ItemMeta meta = previewItem.getItemMeta();
        if (meta == null) return;
        
        RuneTier tier = null;
        String rawName = ChatColor.stripColor(meta.getDisplayName()).toLowerCase();
        for (RuneTier t : RuneTier.values()) {
            if (rawName.contains(t.name().toLowerCase())) {
                tier = t;
                break;
            }
        }
        
        if (tier == null) return;
        
        // Get level range from config for this tier
        int minLevel = plugin.getConfig().getInt("tiers." + tier.getConfigKey() + ".minLevel", 1);
        int maxLevel = plugin.getConfig().getInt("tiers." + tier.getConfigKey() + ".maxLevel", 1);
        if (minLevel < 1) minLevel = 1;
        if (maxLevel < minLevel) maxLevel = minLevel;
        
        ItemStack book = createEnchantedBook(enchantId, minLevel, maxLevel);
        if (book == null) {
            player.sendMessage(ChatColor.RED + "Could not create enchanted book for: " + enchantId);
            return;
        }
        
        // Consume one preview rune
        previewItem.setAmount(previewItem.getAmount() - 1);
        
        // Give the book to the player
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(book);
        for (ItemStack it : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), it);
        }
        
        String msg = plugin.getConfig().getString("messages.runeRevealed", "&aYour rune revealed a &f%enchant% &abook!");
        msg = msg.replace("%enchant%", enchantId);
        player.sendMessage(TextFormatter.format(msg));
        
        // Play reveal sound
        if (plugin.getConfig().getBoolean("sounds.enabled", true)) {
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
        }
    }
}
