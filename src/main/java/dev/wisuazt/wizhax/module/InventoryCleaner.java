package dev.wisuazt.wizhax.module;

import dev.wisuazt.wizhax.WizHax;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;

import java.util.ArrayDeque;
import java.util.List;

public class InventoryCleaner extends Module {
    // PlayerInventory: 0-8 hotbar, 9-35 main, 36-39 armor, 40 offhand
    private static final int HOTBAR_MIN = 0;
    private static final int HOTBAR_MAX = 8;
    private static final int MAIN_MIN = 9;
    private static final int MAIN_MAX = 35;
    private static final int ARMOR_MIN = 36;
    private static final int ARMOR_MAX = 39;
    private static final int OFFHAND = 40;

    public InventoryCleaner() {
        super(WizHax.WIZHAX, "[WHax] InvCleaner",
            "Automatically drops selected items when they appear in your inventory.");
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgCompat = settings.createGroup("Compat");

    private final Setting<List<Item>> items = sgGeneral.add(new ItemListSetting.Builder()
        .name("items")
        .description("Items to automatically drop.")
        .defaultValue(List.of())
        .build());

    private final Setting<Boolean> cleanArmor = sgGeneral.add(new BoolSetting.Builder()
        .name("clean-armor")
        .description("Also drop unwanted equipped armor. Armor is unequipped first, then dropped next pass.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> cleanOffhand = sgGeneral.add(new BoolSetting.Builder()
        .name("clean-offhand")
        .description("Also drop unwanted offhand item.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> ignoreHotbar = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-hotbar")
        .description("Don't drop items placed in the hotbar (slots 0-8).")
        .defaultValue(false)
        .build());

    private final Setting<Integer> dropDelay = sgGeneral.add(new IntSetting.Builder()
        .name("drop-delay")
        .description("Ticks between two drop packets. Prevents anticheat kicks on 2b2t.")
        .defaultValue(2).min(0).sliderMax(10)
        .build());

    private final Setting<Integer> fallbackScanInterval = sgGeneral.add(new IntSetting.Builder()
        .name("fallback-scan-interval")
        .description("Ticks between background scans (0 = disabled). Catches events the mixin missed.")
        .defaultValue(40).min(0).sliderMax(200)
        .build());

    private final Setting<Boolean> protectMinerItems = sgCompat.add(new BoolSetting.Builder()
        .name("protect-miner-items")
        .description("Never drop items the Miner needs: its target items, throwaway blocks, tools, ender chests.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> protectFilledShulkers = sgCompat.add(new BoolSetting.Builder()
        .name("protect-filled-shulkers")
        .description("Never drop a shulker that still contains items.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> protectEnderChests = sgCompat.add(new BoolSetting.Builder()
        .name("protect-ender-chests")
        .description("Never drop ender chests.")
        .defaultValue(true)
        .build());

    private final ArrayDeque<Integer> dropQueue = new ArrayDeque<>();
    private int dropCooldown = 0;
    private int scanCooldown = 0;

    public static void onSlotChanged(int slot, ItemStack stack) {
        InventoryCleaner mod = Modules.get().get(InventoryCleaner.class);
        if (mod == null || !mod.isActive()) return;
        if (stack.isEmpty()) return;
        if (!mod.shouldCleanSlot(slot)) return;
        if (!mod.shouldDrop(stack)) return;
        if (!mod.dropQueue.contains(slot)) mod.dropQueue.addLast(slot);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        if (mc.currentScreen != null) return;

        if (dropCooldown > 0) dropCooldown--;

        int interval = fallbackScanInterval.get();
        if (interval > 0) {
            if (++scanCooldown >= interval) {
                scanCooldown = 0;
                for (int i = 0; i <= 40; i++) {
                    ItemStack s = mc.player.getInventory().getStack(i);
                    if (s.isEmpty()) continue;
                    if (!shouldCleanSlot(i)) continue;
                    if (!shouldDrop(s)) continue;
                    if (!dropQueue.contains(i)) dropQueue.addLast(i);
                }
            }
        }

        if (dropCooldown > 0) return;

        while (!dropQueue.isEmpty()) {
            int slot = dropQueue.pollFirst();
            ItemStack s = mc.player.getInventory().getStack(slot);
            if (s.isEmpty()) continue;
            if (!shouldCleanSlot(slot)) continue;
            if (!shouldDrop(s)) continue;
            dropFromSlot(slot, s);
            dropCooldown = dropDelay.get();
            return;
        }
    }

    private boolean shouldCleanSlot(int slot) {
        if (slot < 0 || slot > OFFHAND) return false;
        if (slot >= HOTBAR_MIN && slot <= HOTBAR_MAX) return !ignoreHotbar.get();
        if (slot >= MAIN_MIN && slot <= MAIN_MAX) return true;
        if (slot >= ARMOR_MIN && slot <= ARMOR_MAX) return cleanArmor.get();
        return slot == OFFHAND && cleanOffhand.get();
    }

    private boolean shouldDrop(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!items.get().contains(stack.getItem())) return false;

        if (protectEnderChests.get() && stack.getItem() == Items.ENDER_CHEST) return false;

        if (protectFilledShulkers.get() && stack.isIn(ItemTags.SHULKER_BOXES)) {
            var c = stack.get(net.minecraft.component.DataComponentTypes.CONTAINER);
            if (c != null && c.stream().anyMatch(s -> !s.isEmpty())) return false;
        }

        if (protectMinerItems.get()) {
            Miner miner = Modules.get().get(Miner.class);
            if (miner != null && miner.isActive() && miner.isProtectedItem(stack)) return false;
        }

        return true;
    }

    private void dropFromSlot(int slot, ItemStack stack) {
        if (slot >= ARMOR_MIN && slot <= ARMOR_MAX) {
            int empty = findEmptyMainSlot();
            if (empty == -1) return;
            int syncId = mc.player.playerScreenHandler.syncId;
            mc.interactionManager.clickSlot(syncId, armorHandlerSlot(slot), 0,
                net.minecraft.screen.slot.SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(syncId, invToHandlerSlot(empty), 0,
                net.minecraft.screen.slot.SlotActionType.PICKUP, mc.player);
            return;
        }
        if (slot == OFFHAND) {
            int empty = findEmptyMainSlot();
            if (empty == -1) return;
            InvUtils.move().fromOffhand().to(empty);
            return;
        }
        InvUtils.drop().slot(slot);
    }

    private int findEmptyMainSlot() {
        for (int i = MAIN_MIN; i <= MAIN_MAX; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return i;
        }
        return -1;
    }

    private int invToHandlerSlot(int invSlot) {
        if (invSlot >= 0 && invSlot <= 8) return 36 + invSlot;
        if (invSlot >= 9 && invSlot <= 35) return invSlot;
        if (invSlot >= 36 && invSlot <= 39) return armorHandlerSlot(invSlot);
        return 45;
    }

    // PlayerInventory 36=boots, 37=leggings, 38=chestplate, 39=helmet
    // PlayerScreenHandler 5=helmet, 6=chest, 7=legs, 8=boots
    private int armorHandlerSlot(int invSlot) {
        return switch (invSlot) {
            case 36 -> 8;
            case 37 -> 7;
            case 38 -> 6;
            case 39 -> 5;
            default -> -1;
        };
    }
}
