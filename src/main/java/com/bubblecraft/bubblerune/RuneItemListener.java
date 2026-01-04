package com.bubblecraft.bubblerune;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
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
    private boolean ecoEnchantsPresent;

    public RuneItemListener(BubbleRunePlugin plugin, RuneService runeService) {
        this.plugin = plugin;
        this.runeService = runeService;
        checkEcoEnchants();
    }

    private void checkEcoEnchants() {
        try {
            Class.forName("com.willfp.ecoenchants.EcoEnchantsPlugin");
            plugin.getLogger().info("EcoEnchants detected - custom enchantments enabled.");
            ecoEnchantsPresent = Bukkit.getPluginManager().getPlugin("EcoEnchants") != null;
        } catch (ClassNotFoundException e) {
            plugin.getLogger().info("EcoEnchants not found - using vanilla enchantments only.");
            ecoEnchantsPresent = false;
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

        // Only genuine rune items (PDC-marked) should trigger rune reveal logic.
        if (!RuneItemData.isRune(item, plugin)) return;

        RuneTier tier = RuneItemData.getTier(item, plugin);
        if (tier == null) {
            player.sendMessage(TextFormatter.format(plugin.getMessage(
                "messages.runeItemMissingTierData",
                "&cThis rune item is missing tier data. Please contact staff.")));
            return;
        }

        event.setCancelled(true);

        // Determine a random enchant ID for this tier from config.
        String enchantId = runeService.getRandomEnchantIdForTier(tier);
        if (enchantId == null) {
            player.sendMessage(TextFormatter.format(plugin.getMessage(
                "messages.noEnchantsConfiguredForTier",
                "&cNo enchants configured for this rune tier.")));
            return;
        }

        // Get level range from config for this tier
        int minLevel = plugin.getConfig().getInt("tiers." + tier.name().toLowerCase() + ".minLevel", 1);
        int maxLevel = plugin.getConfig().getInt("tiers." + tier.name().toLowerCase() + ".maxLevel", 1);
        if (minLevel < 1) minLevel = 1;
        if (maxLevel < minLevel) maxLevel = minLevel;

        ItemStack book = createEnchantedBook(enchantId, minLevel, maxLevel);
        if (book == null) {
            String template = plugin.getMessage(
                "messages.couldNotCreateBookForEnchant",
                "&cCould not create enchanted book for: %enchant%");
            player.sendMessage(TextFormatter.format(template.replace("%enchant%", enchantId)));
            return;
        }

        // Consume one rune item
        item.setAmount(item.getAmount() - 1);

        // Give the book to the player
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(book);
        for (ItemStack it : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), it);
        }

        String msg = plugin.getMessage("messages.runeRevealed", "&aYour rune revealed a &f%enchant% &abook!");
        msg = msg.replace("%enchant%", enchantId);
        player.sendMessage(TextFormatter.format(msg));
        
        // Play reveal sound
        if (plugin.getConfig().getBoolean("sounds.enabled", true)) {
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
        }
    }

    private ItemStack createEnchantedBook(String enchantId, int minLevel, int maxLevel) {
        try {
            ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
            EnchantmentStorageMeta bookMeta = (EnchantmentStorageMeta) book.getItemMeta();
            if (bookMeta == null) return null;

            Enchantment enchantment = findEnchantment(enchantId);
            if (enchantment == null) {
                plugin.getLogger().warning("Enchantment not found: " + enchantId +
                    " (EcoEnchants installed=" + ecoEnchantsPresent + ")");

                if (plugin.isDebugEnabled()) {
                    String normalizedId = normalizeForMatch(enchantId);
                    int matched = 0;
                    StringBuilder sb = new StringBuilder();
                    for (Enchantment ench : Registry.ENCHANTMENT) {
                        String key = ench.getKey().getNamespace() + ":" + ench.getKey().getKey();
                        String normalizedKey = normalizeForMatch(key);
                        if (normalizedKey.contains(normalizedId) || normalizedId.contains(normalizedKey)) {
                            if (matched++ == 0) sb.append("Possible matches: ");
                            if (matched <= 10) {
                                if (matched > 1) sb.append(", ");
                                sb.append(key);
                            }
                        }
                        if (matched >= 10) break;
                    }
                    if (sb.length() > 0) {
                        plugin.getLogger().info("[Debug] Enchant lookup failed for '" + enchantId + "'. " + sb);
                    } else {
                        plugin.getLogger().info("[Debug] Enchant lookup failed for '" + enchantId + "'. No similar keys found in Registry.ENCHANTMENT.");
                    }
                }

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

            if (plugin.isDebugEnabled()) {
                plugin.getLogger().info("[Debug] Created enchanted book: id='" + enchantId + "' key='" + enchantment.getKey() + "' level=" + level);
            }

            return book;
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed creating enchanted book for: " + enchantId + " (" + ex.getClass().getSimpleName() + ": " + ex.getMessage() + ")");
            if (plugin.isDebugEnabled()) {
                ex.printStackTrace();
            }
            return null;
        }
    }

    /**
     * Find an enchantment by ID. EcoEnchants registers its enchantments into the Bukkit Registry,
     * so we can look them up via NamespacedKey. Tries both minecraft: and ecoenchants: namespaces.
     */
    private Enchantment findEnchantment(String enchantId) {
        if (enchantId == null) return null;
        String raw = enchantId.trim();
        if (raw.isEmpty()) return null;

        // If the config provides a namespaced key, try it directly first.
        if (raw.contains(":")) {
            NamespacedKey direct = NamespacedKey.fromString(raw.toLowerCase());
            if (direct != null) {
                Enchantment found = Registry.ENCHANTMENT.get(direct);
                if (found != null) return found;
            }
        }

        String id = raw.toLowerCase().replace(" ", "_");

        // Try common namespaces.
        Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(id));
        if (enchantment != null) return enchantment;

        try {
            enchantment = Registry.ENCHANTMENT.get(new NamespacedKey("ecoenchants", id));
            if (enchantment != null) return enchantment;
        } catch (Exception ignored) {
            // NamespacedKey constructor will only fail on invalid namespace.
        }

        // Normalize for forgiving matches: "block_breather" vs "blockbreather" / "block-breather".
        String normalizedInput = normalizeForMatch(id);

        for (Enchantment ench : Registry.ENCHANTMENT) {
            NamespacedKey key = ench.getKey();
            String full = (key.getNamespace() + ":" + key.getKey()).toLowerCase();
            String keyPart = key.getKey().toLowerCase();

            if (keyPart.equalsIgnoreCase(id)) return ench;
            if (full.equalsIgnoreCase(raw)) return ench;
            if (normalizeForMatch(keyPart).equals(normalizedInput)) return ench;
            if (normalizeForMatch(full).equals(normalizedInput)) return ench;
        }

        return null;
    }

    private static String normalizeForMatch(String value) {
        if (value == null) return "";
        return value.toLowerCase().replaceAll("[^a-z0-9]", "");
    }
    
    private void revealPreviewRune(Player player, ItemStack previewItem, String enchantId) {
        RuneTier tier = RuneItemData.getTier(previewItem, plugin);
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
        
        String msg = plugin.getMessage("messages.runeRevealed", "&aYour rune revealed a &f%enchant% &abook!");
        msg = msg.replace("%enchant%", enchantId);
        player.sendMessage(TextFormatter.format(msg));
        
        // Play reveal sound
        if (plugin.getConfig().getBoolean("sounds.enabled", true)) {
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
        }
    }
}
