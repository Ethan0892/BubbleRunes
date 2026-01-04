package com.bubblecraft.bubblerune;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.scheduler.BukkitTask;

public class RuneCombineListener implements Listener {
    private final BubbleRunePlugin plugin;
    private final RuneService runeService;

    // Prevent multiple scheduled tasks from double-combining on the same anvil.
    private final Map<String, Long> recentDropCombinesNanos = new HashMap<>();

    // Track short-lived per-item checks so we don't schedule duplicates.
    private final Map<UUID, BukkitTask> dropCheckTasks = new HashMap<>();

    public RuneCombineListener(BubbleRunePlugin plugin, RuneService runeService) {
        this.plugin = plugin;
        this.runeService = runeService;
    }

    @EventHandler
    public void onItemSpawn(ItemSpawnEvent event) {
        if (!plugin.getConfig().getBoolean("runeCombining.enabled", true)) return;

        Item itemEntity = event.getEntity();
        if (itemEntity == null) return;

        // Lightweight fast-path: only do any work for rune items.
        // (Rune drops are rare compared to global item spawns.)
        if (getRuneTier(itemEntity.getItemStack()) == null) return;

        scheduleDropOnAnvilChecks(itemEntity);
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (!plugin.getConfig().getBoolean("runeCombining.enabled", true)) return;
        if (event == null) return;
        Item drop = event.getItemDrop();
        if (drop == null) return;
        if (getRuneTier(drop.getItemStack()) == null) return;

        scheduleDropOnAnvilChecks(drop);
    }

    private void scheduleDropOnAnvilChecks(Item itemEntity) {
        if (itemEntity == null) return;
        UUID uuid = itemEntity.getUniqueId();
        if (dropCheckTasks.containsKey(uuid)) return;

        final int[] attempts = {0};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!itemEntity.isValid()) {
                cancelDropTask(uuid);
                return;
            }

            // If it combines, stop early.
            boolean combined = tryCombineDroppedRunesOnAnvil(itemEntity);
            if (combined) {
                cancelDropTask(uuid);
                return;
            }

            attempts[0]++;
            if (attempts[0] >= 25) { // ~2.5s at 2-tick interval
                cancelDropTask(uuid);
            }
        }, 2L, 2L);

        dropCheckTasks.put(uuid, task);
    }

    private void cancelDropTask(UUID uuid) {
        if (uuid == null) return;
        BukkitTask task = dropCheckTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    private boolean tryCombineDroppedRunesOnAnvil(Item triggerEntity) {
        if (triggerEntity == null || !triggerEntity.isValid()) return false;

        ItemStack triggerStack = triggerEntity.getItemStack();
        RuneTier triggerTier = getRuneTier(triggerStack);
        if (triggerTier == null) return false;

        Location loc = triggerEntity.getLocation();
        if (loc == null) return false;
        World world = loc.getWorld();
        if (world == null) return false;

        Block anvilBlock = getAnvilBlockUnder(loc);
        if (anvilBlock == null) return false;

        String anvilKey = anvilKey(anvilBlock.getLocation());
        long now = System.nanoTime();
        if (recentDropCombinesNanos.size() > 512) {
            // Hard cap to avoid any chance of unbounded growth.
            recentDropCombinesNanos.clear();
        }
        Long last = recentDropCombinesNanos.get(anvilKey);
        if (last != null && (now - last) < 150_000_000L) {
            // ~150ms guard against double processing.
            return false;
        }
        recentDropCombinesNanos.put(anvilKey, now);

        int required = plugin.getConfig().getInt("runeCombining.requiredRunes", 2);
        if (required < 2) required = 2;

        // Gather dropped rune items of the same tier around this anvil.
        // Use the anvil as the anchor (items can spread slightly), rather than a tiny radius around one entity.
        List<Item> candidates = new ArrayList<>();
        Location anvilTop = anvilBlock.getLocation().add(0.5, 1.0, 0.5);
        for (org.bukkit.entity.Entity e : world.getNearbyEntities(anvilTop, 1.25, 1.25, 1.25)) {
            if (!(e instanceof Item)) continue;
            Item other = (Item) e;
            if (!other.isValid()) continue;
            RuneTier otherTier = getRuneTier(other.getItemStack());
            if (otherTier != triggerTier) continue;

            Block otherAnvil = getAnvilBlockUnder(other.getLocation());
            if (otherAnvil == null) continue;
            if (!sameBlock(otherAnvil, anvilBlock)) continue;

            candidates.add(other);
        }

        // Ensure trigger is included even if the world query missed it for any reason.
        if (!candidates.contains(triggerEntity)) {
            Block triggerAnvil = getAnvilBlockUnder(triggerEntity.getLocation());
            if (triggerAnvil != null && sameBlock(triggerAnvil, anvilBlock)) {
                candidates.add(triggerEntity);
            }
        }

        // Stable ordering so consumption is predictable.
        candidates.sort(Comparator.comparingDouble(i -> i.getLocation().getY()));

        int total = 0;
        for (Item i : candidates) {
            ItemStack s = i.getItemStack();
            if (s == null) continue;
            total += s.getAmount();
            if (total >= required) break;
        }
        if (total < required) return false;

        RuneTier nextTier = getNextTier(triggerTier);
        if (nextTier == null) return false;

        // Consume required runes across the nearby stacks.
        int remaining = required;
        for (Item i : candidates) {
            if (remaining <= 0) break;
            if (!i.isValid()) continue;
            ItemStack s = i.getItemStack();
            if (s == null) continue;
            int take = Math.min(s.getAmount(), remaining);
            remaining -= take;
            int newAmount = s.getAmount() - take;
            if (newAmount <= 0) {
                i.remove();
            } else {
                s.setAmount(newAmount);
                i.setItemStack(s);
            }
        }
        if (remaining > 0) return false;

        ItemStack upgraded = runeService.createRuneItem(nextTier);
        Location dropLoc = anvilBlock.getLocation().add(0.5, 1.1, 0.5);
        world.dropItemNaturally(dropLoc, upgraded);
        world.playSound(dropLoc, Sound.BLOCK_ANVIL_USE, 1.0f, 1.2f);

        return true;
    }

    private static Block getAnvilBlockUnder(Location location) {
        if (location == null) return null;
        World world = location.getWorld();
        if (world == null) return null;

        // Item entity location can fluctuate while falling/bouncing; scan down a couple blocks.
        int x = location.getBlockX();
        int z = location.getBlockZ();
        for (int dy = 0; dy <= 2; dy++) {
            Block b = world.getBlockAt(x, location.getBlockY() - dy, z);
            if (isAnvilMaterial(b.getType())) return b;
        }
        return null;
    }

    private static boolean isAnvilMaterial(Material type) {
        return type == Material.ANVIL || type == Material.CHIPPED_ANVIL || type == Material.DAMAGED_ANVIL;
    }

    private static boolean sameBlock(Block a, Block b) {
        if (a == null || b == null) return false;
        if (a.getWorld() == null || b.getWorld() == null) return false;
        return a.getWorld().equals(b.getWorld())
            && a.getX() == b.getX()
            && a.getY() == b.getY()
            && a.getZ() == b.getZ();
    }

    private static String anvilKey(Location l) {
        if (l == null || l.getWorld() == null) return "unknown";
        return l.getWorld().getName() + ":" + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!plugin.getConfig().getBoolean("runeCombining.enabled", true)) return;
        Inventory inv = event.getInventory();
        if (inv == null || inv.getType() != InventoryType.ANVIL) return;

        ItemStack result = computeCombineResult(inv);
        event.setResult(result);

        if (result == null) return;

        // Make it look like a "free" combine in the anvil UI.
        trySetAnvilRepairCost(inv, 0);
    }

    private static void trySetAnvilRepairCost(Inventory inventory, int repairCost) {
        if (inventory == null || inventory.getType() != InventoryType.ANVIL) return;
        try {
            // Purpur/Paper API has changed this over time; use reflection for max compatibility.
            inventory.getClass().getMethod("setRepairCost", int.class).invoke(inventory, repairCost);
        } catch (Throwable ignored) {
            // Not supported on this server/API; safe to ignore.
        }
    }

    ItemStack computeCombineResult(Inventory anvilInventory) {
        if (anvilInventory == null || anvilInventory.getType() != InventoryType.ANVIL) return null;

        ItemStack left = anvilInventory.getItem(0);
        ItemStack right = anvilInventory.getItem(1);
        if (left == null || right == null) {
            return null;
        }

        RuneTier leftTier = getRuneTier(left);
        RuneTier rightTier = getRuneTier(right);
        if (leftTier == null || rightTier == null) {
            return null;
        }
        if (leftTier != rightTier) {
            return null;
        }

        int required = plugin.getConfig().getInt("runeCombining.requiredRunes", 2);
        if (required < 2) required = 2;

        int total = left.getAmount() + right.getAmount();
        if (total < required) {
            return null;
        }

        RuneTier nextTier = getNextTier(leftTier);
        if (nextTier == null) {
            return null;
        }

        return runeService.createRuneItem(nextTier);
    }

    @EventHandler
    public void onAnvilClick(InventoryClickEvent event) {
        if (!plugin.getConfig().getBoolean("runeCombining.enabled", true)) return;
        Inventory inv = event.getInventory();
        if (inv == null || inv.getType() != InventoryType.ANVIL) return;
        if (event.getSlotType() != InventoryType.SlotType.RESULT) return;

        ItemStack left = inv.getItem(0);
        ItemStack right = inv.getItem(1);
        
        if (left == null || right == null) return;
        
        RuneTier leftTier = getRuneTier(left);
        RuneTier rightTier = getRuneTier(right);
        
        if (leftTier == null || rightTier == null) return;
        if (leftTier != rightTier) return;
        
        int required = plugin.getConfig().getInt("runeCombining.requiredRunes", 2);
        if (required < 2) required = 2;
        if (left.getAmount() + right.getAmount() < required) return;
        
        // Check if this is the highest tier
        RuneTier nextTier = getNextTier(leftTier);
        if (nextTier == null) {
            if (event.getWhoClicked() instanceof Player) {
                Player player = (Player) event.getWhoClicked();
                player.sendMessage(TextFormatter.format(plugin.getMessage(
                    "messages.runeTierCannotBeUpgraded",
                    "&cThis rune tier cannot be upgraded further!")));
            }
            event.setCancelled(true);
            return;
        }
        
        event.setCancelled(true);
        
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        // Prevent losing items if inventory is full
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(TextFormatter.format(plugin.getMessage(
                "messages.inventoryFull",
                "&cYour inventory is full! Clear a slot first.")));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }
        
        // Consume the runes
        int remaining = required;
        int leftTake = Math.min(left.getAmount(), remaining);
        remaining -= leftTake;
        int newLeftAmount = left.getAmount() - leftTake;

        int rightTake = Math.min(right.getAmount(), remaining);
        remaining -= rightTake;
        int newRightAmount = right.getAmount() - rightTake;

        if (remaining > 0) {
            // Shouldn't happen; keep things safe.
            return;
        }

        if (newLeftAmount <= 0) {
            inv.setItem(0, null);
        } else {
            left.setAmount(newLeftAmount);
            inv.setItem(0, left);
        }

        if (newRightAmount <= 0) {
            inv.setItem(1, null);
        } else {
            right.setAmount(newRightAmount);
            inv.setItem(1, right);
        }

        // Clear output slot
        inv.setItem(2, null);
        
        // Give upgraded rune
        ItemStack upgraded = runeService.createRuneItem(nextTier);
        player.getInventory().addItem(upgraded);
        
        // Effects
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.2f);
        String msg = TextFormatter.format(
            plugin.getMessage("messages.runeCombined",
                "&aYou combined %count% &f%tier% &arunes into a &f%newtier% &arune!")
                .replace("%count%", String.valueOf(required))
                .replace("%tier%", leftTier.name().toLowerCase())
                .replace("%newtier%", nextTier.name().toLowerCase())
        );
        
        player.sendMessage(msg);
        player.closeInventory();
    }
    
    private RuneTier getRuneTier(ItemStack item) {
        if (!RuneItemData.isRune(item, plugin)) return null;
        return RuneItemData.getTier(item, plugin);
    }
    
    private RuneTier getNextTier(RuneTier current) {
        RuneTier[] tiers = RuneTier.values();
        for (int i = 0; i < tiers.length - 1; i++) {
            if (tiers[i] == current) {
                return tiers[i + 1];
            }
        }
        return null; // Already at max tier
    }
}
