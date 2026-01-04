package com.bubblecraft.bubblerune;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

class RuneCombineListenerTest {
    private ServerMock server;
    private BubbleRunePlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(BubbleRunePlugin.class);

        plugin.getConfig().set("runeCombining.enabled", true);
        plugin.getConfig().set("runeCombining.requiredRunes", 2);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void prepareAnvil_whenEnoughRunes_setsResultToNextTier() {
        RuneCombineListener listener = new RuneCombineListener(plugin, plugin.getRuneService());

        Inventory anvil = server.createInventory(null, InventoryType.ANVIL);

        ItemStack left = plugin.getRuneService().createRuneItem(RuneTier.COMMON);
        ItemStack right = plugin.getRuneService().createRuneItem(RuneTier.COMMON);
        assertNotNull(left);
        assertNotNull(right);
        assertTrue(RuneItemData.isRune(left, plugin));
        assertTrue(RuneItemData.isRune(right, plugin));

        left.setAmount(1);
        right.setAmount(1);

        anvil.setItem(0, left);
        anvil.setItem(1, right);

        ItemStack result = listener.computeCombineResult(anvil);
        assertNotNull(result, "Expected an upgraded rune in the output slot");
        assertTrue(RuneItemData.isRune(result, plugin), "Upgraded rune must be PDC-marked");
        assertEquals(RuneTier.UNCOMMON, RuneItemData.getTier(result, plugin), "COMMON should upgrade to UNCOMMON");
    }

    @Test
    void prepareAnvil_whenNotEnoughRunes_setsNoResult() {
        RuneCombineListener listener = new RuneCombineListener(plugin, plugin.getRuneService());

        // Override to ensure 1+1 is NOT enough for this test.
        plugin.getConfig().set("runeCombining.requiredRunes", 3);

        Inventory anvil = server.createInventory(null, InventoryType.ANVIL);

        ItemStack left = plugin.getRuneService().createRuneItem(RuneTier.COMMON);
        ItemStack right = plugin.getRuneService().createRuneItem(RuneTier.COMMON);
        assertNotNull(left);
        assertNotNull(right);

        left.setAmount(1);
        right.setAmount(1);

        anvil.setItem(0, left);
        anvil.setItem(1, right);

        assertNull(listener.computeCombineResult(anvil), "Not enough runes to combine should yield no output");
    }

    @Test
    void prepareAnvil_whenItemsAreNotRunes_evenIfTheyLookSimilar_setsNoResult() {
        RuneCombineListener listener = new RuneCombineListener(plugin, plugin.getRuneService());

        Inventory anvil = server.createInventory(null, InventoryType.ANVIL);

        // Fake item: looks like a rune (name/lore), but has no BubbleRune PDC tags.
        ItemStack fakeLeft = new ItemStack(Material.PAPER);
        ItemMeta leftMeta = fakeLeft.getItemMeta();
        assertNotNull(leftMeta);
        leftMeta.setDisplayName("§bcommon rune");
        leftMeta.setLore(java.util.List.of(
            "§7Right-click to reveal a",
            "§7random common EcoEnchant book.",
            "",
            "§7Combine 2 in an anvil to upgrade!"
        ));
        fakeLeft.setItemMeta(leftMeta);

        ItemStack fakeRight = fakeLeft.clone();
        fakeLeft.setAmount(1);
        fakeRight.setAmount(1);

        assertFalse(RuneItemData.isRune(fakeLeft, plugin), "Spoofed item must not be treated as a rune");
        assertFalse(RuneItemData.isRune(fakeRight, plugin), "Spoofed item must not be treated as a rune");

        anvil.setItem(0, fakeLeft);
        anvil.setItem(1, fakeRight);

        assertNull(listener.computeCombineResult(anvil), "Non-rune items must never combine");
    }

    @Test
    void crafting_whenThreeSameTierRunes_returnsNextTier() {
        plugin.getConfig().set("runeCraftingCombining.enabled", true);
        plugin.getConfig().set("runeCraftingCombining.requiredRunes", 3);

        RuneCraftingCombineListener listener = new RuneCraftingCombineListener(plugin, plugin.getRuneService());

        ItemStack r1 = plugin.getRuneService().createRuneItem(RuneTier.COMMON);
        ItemStack r2 = plugin.getRuneService().createRuneItem(RuneTier.COMMON);
        ItemStack r3 = plugin.getRuneService().createRuneItem(RuneTier.COMMON);
        r1.setAmount(1);
        r2.setAmount(1);
        r3.setAmount(1);

        ItemStack[] matrix = new ItemStack[9];
        matrix[0] = r1;
        matrix[1] = r2;
        matrix[2] = r3;

        ItemStack result = listener.computeCraftResult(matrix);
        assertNotNull(result);
        assertTrue(RuneItemData.isRune(result, plugin));
        assertEquals(RuneTier.UNCOMMON, RuneItemData.getTier(result, plugin));
    }

    @Test
    void crafting_whenItemsAreNotRunes_evenIfTheyLookSimilar_returnsNoResult() {
        plugin.getConfig().set("runeCraftingCombining.enabled", true);
        plugin.getConfig().set("runeCraftingCombining.requiredRunes", 3);

        RuneCraftingCombineListener listener = new RuneCraftingCombineListener(plugin, plugin.getRuneService());

        ItemStack fake = new ItemStack(Material.PAPER);
        ItemMeta meta = fake.getItemMeta();
        assertNotNull(meta);
        meta.setDisplayName("§bcommon rune");
        meta.setLore(java.util.List.of(
            "§7Right-click to reveal a",
            "§7random common EcoEnchant book.",
            "",
            "§7Combine 3 in a crafting grid to upgrade!"
        ));
        fake.setItemMeta(meta);

        ItemStack[] matrix = new ItemStack[9];
        matrix[0] = fake.clone();
        matrix[1] = fake.clone();
        matrix[2] = fake.clone();

        assertNull(listener.computeCraftResult(matrix));
    }

    @Test
    void clickResult_consumesInputs_andGivesUpgradedRune() {
        RuneCombineListener listener = new RuneCombineListener(plugin, plugin.getRuneService());

        PlayerMock player = server.addPlayer();
        assertNotEquals(-1, player.getInventory().firstEmpty());

        Inventory anvil = server.createInventory(null, InventoryType.ANVIL);

        ItemStack left = plugin.getRuneService().createRuneItem(RuneTier.COMMON);
        ItemStack right = plugin.getRuneService().createRuneItem(RuneTier.COMMON);
        left.setAmount(1);
        right.setAmount(1);

        anvil.setItem(0, left);
        anvil.setItem(1, right);
        anvil.setItem(2, plugin.getRuneService().createRuneItem(RuneTier.UNCOMMON));

        player.openInventory(anvil);

        int beforeUncommon = countTier(player, RuneTier.UNCOMMON);

        InventoryClickEvent click = new InventoryClickEvent(
            player.getOpenInventory(),
            InventoryType.SlotType.RESULT,
            2,
            ClickType.LEFT,
            InventoryAction.PICKUP_ALL
        );

        listener.onAnvilClick(click);

        assertTrue(click.isCancelled(), "Result click should be cancelled because we handle it");
        assertNull(anvil.getItem(0), "Left slot should be consumed");
        assertNull(anvil.getItem(1), "Right slot should be consumed");
        assertNull(anvil.getItem(2), "Result slot should be cleared");

        assertTrue(countTier(player, RuneTier.UNCOMMON) > beforeUncommon, "Player should receive upgraded rune");
    }

    private static int countTier(PlayerMock player, RuneTier tier, BubbleRunePlugin plugin) {
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() == Material.AIR) continue;
            if (!RuneItemData.isRune(item, plugin)) continue;
            RuneTier t = RuneItemData.getTier(item, plugin);
            if (t == tier) total += item.getAmount();
        }
        return total;
    }

    private int countTier(PlayerMock player, RuneTier tier) {
        return countTier(player, tier, plugin);
    }
}
