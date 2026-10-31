package dev.wisuazt.wizhax.module;

import dev.wisuazt.wizhax.WizHax;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

public class ElytraSwap extends Module {
    private static final long SWAP_DELAY = 500;
    private long lastSwap;
    private boolean waitingForRepair = false;

    public ElytraSwap() {
        super(WizHax.WIZHAX, "[WHax] ElytraSwap", "Cycles elytras while repairing.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.interactionManager == null) return;
        ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (!chest.isOf(Items.ELYTRA)) return;
        if (chest.getDamage() > 0) {
            waitingForRepair = true;
            return;
        }
        if (waitingForRepair) {
            if (System.currentTimeMillis() - lastSwap < SWAP_DELAY) return;
            int next = findDamagedElytra();
            if (next == -1) {
                toggle();
                return;
            }
            swapElytra(next);
            lastSwap = System.currentTimeMillis();
            waitingForRepair = true;
        }
    }

    private int findDamagedElytra() {
        for (int i = 0; i < 36; i++) {
            assert mc.player != null;
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isOf(Items.ELYTRA)) continue;
            if (stack.getDamage() > 0) return i;
        }
        return -1;
    }

    private void swapElytra(int inventorySlot) {
        int slot = inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
        mc.execute(() -> {
            assert mc.interactionManager != null;
            assert mc.player != null;
            mc.interactionManager.clickSlot(
                mc.player.playerScreenHandler.syncId,
                6,
                0,
                SlotActionType.PICKUP,
                mc.player
            );

            mc.interactionManager.clickSlot(
                mc.player.playerScreenHandler.syncId,
                slot,
                0,
                SlotActionType.PICKUP,
                mc.player
            );

            mc.interactionManager.clickSlot(
                mc.player.playerScreenHandler.syncId,
                6,
                0,
                SlotActionType.PICKUP,
                mc.player
            );
        });
    }
}
