package dev.wisuazt.wizhax.module;

import dev.wisuazt.wizhax.WizHax;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AutoPortal extends Module {
    public AutoPortal() {
        super(WizHax.WIZHAX, "[WHax] AutoPortal", "Automatically builds and lights a portal to the nether.");
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> autoDisable = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-disable")
        .description("Disables the module after the portal is successfully built.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseOnInput = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-on-input")
        .description("Pauses construction while you move, jump, or squat.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> placeDelay = sgGeneral.add(new IntSetting.Builder()
        .name("place-delay")
        .description("Ticks delay between block place.")
        .defaultValue(1)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<Integer> placementTimeout = sgGeneral.add(new IntSetting.Builder()
        .name("placement-timeout")
        .description("After how many ticks, a failed attempt to install a block is considered failed and repeats.")
        .defaultValue(20)
        .sliderRange(5, 100)
        .build()
    );

    private final Setting<Boolean> render = sgGeneral.add(new BoolSetting.Builder()
        .name("render")
        .description("Draws the frame of the portal.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How to draw a frame.")
        .defaultValue(ShapeMode.Lines)
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgGeneral.add(new ColorSetting.Builder()
        .name("side-color")
        .defaultValue(new SettingColor(0, 225, 255, 60))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgGeneral.add(new ColorSetting.Builder()
        .name("line-color")
        .defaultValue(new SettingColor(0, 225, 255, 200))
        .visible(render::get)
        .build()
    );

    private static final SettingColor BLOCKED_LINE = new SettingColor(255, 45, 45, 220);
    private static final SettingColor BLOCKED_SIDE = new SettingColor(255, 45, 45, 60);
    private enum Phase { PLACING, LIGHTING, VERIFYING }
    private final List<BlockPos> frameBlocks = new ArrayList<>();
    private final List<BlockPos> interiorBlocks = new ArrayList<>();
    private final Map<BlockPos, Integer> pending = new HashMap<>();

    private boolean positionLocked;
    private Phase phase;
    private BlockPos ignitionPos;
    private int delay;
    private int tickCounter;

    @Override
    public void onActivate() {
        positionLocked = false;
        phase = Phase.PLACING;
        frameBlocks.clear();
        interiorBlocks.clear();
        pending.clear();
        ignitionPos = null;
        delay = 0;
        tickCounter = 0;
    }

    @Override
    public void onDeactivate() {
        frameBlocks.clear();
        interiorBlocks.clear();
        pending.clear();
        ignitionPos = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (!positionLocked) computeFrame();
        tickCounter++;
        pending.entrySet().removeIf(entry -> {
            if (mc.world.getBlockState(entry.getKey()).getBlock() == Blocks.OBSIDIAN) return true;
            return tickCounter - entry.getValue() > placementTimeout.get();
        });

        if (delay > 0) delay--;
        if (pauseOnInput.get() && isPlayerMoving()) return;
        if (!positionLocked) {
            if (frameBlocks.isEmpty()) return;
            int obsidianCount = countItem(Items.OBSIDIAN);
            if (obsidianCount < 10) {
                error("Not enough obsidian (you need 10, there is %d)!", obsidianCount);
                toggle();
                return;
            }

            if (findHotbarSlot(Items.FLINT_AND_STEEL) < 0 && !hasItemAnywhere(Items.FLINT_AND_STEEL)) {
                error("No flint and steel found in the inventory!");
                toggle();
                return;
            }

            if (isPortalAlreadyHere()) {
                error("The portal has already been built here!");
                toggle();
                return;
            }

            positionLocked = true;
            phase = Phase.PLACING;
            info("The position is fixed, building a portal...");
        }

        switch (phase) {
            case PLACING -> placeFrame();
            case LIGHTING -> lightPortal();
            case VERIFYING -> verify();
        }
    }

    private void placeFrame() {
        boolean anyPlaceable = false;
        for (BlockPos pos : frameBlocks) {
            if (isPlaceableTarget(pos)) {
                anyPlaceable = true;
                break;
            }
        }

        if (!anyPlaceable) {
            if (isFrameComplete()) {
                phase = Phase.LIGHTING;
            }
            return;
        }

        if (delay > 0) return;

        int slot = findHotbarSlot(Items.OBSIDIAN);
        if (slot < 0) {
            error("Obsidian is over in hotbar!");
            toggle();
            return;
        }
        mc.player.getInventory().setSelectedSlot(slot);

        for (BlockPos pos : frameBlocks) {
            if (!isPlaceableTarget(pos) || pending.containsKey(pos)) continue;

            placeBlock(pos);
            pending.put(pos, tickCounter);
        }

        delay = placeDelay.get();
    }

    private void lightPortal() {
        if (ignitionPos == null) return;

        if (!isFrameComplete()) {
            phase = Phase.PLACING;
            return;
        }

        if (delay > 0) return;
        int slot = findHotbarSlot(Items.FLINT_AND_STEEL);
        if (slot < 0) {
            error("The flint and steel are missing from the hotbar!");
            toggle();
            return;
        }
        assert mc.player != null;
        mc.player.getInventory().setSelectedSlot(slot);
        BlockPos firePos = ignitionPos.up();
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(firePos), Direction.UP, ignitionPos, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);
        phase = Phase.VERIFYING;
        delay = 10;
    }

    private void verify() {
        if (delay > 0) return;
        if (isPortalLit()) {
            info("The portal has been successfully built!");
            if (autoDisable.get()) toggle();
            return;
        }
        phase = Phase.LIGHTING;
    }

    private void computeFrame() {
        frameBlocks.clear();
        interiorBlocks.clear();
        ignitionPos = null;

        BlockPos foot = mc.player.getBlockPos();
        Direction facing = directionFromYaw(mc.player.getYaw());
        int yOffset = yOffsetFromPitch(mc.player.getPitch());
        int fx = facing.getOffsetX();
        int fz = facing.getOffsetZ();
        int rx = -fz;
        int rz = fx;
        int planeX = foot.getX() + fx * 2;
        int planeZ = foot.getZ() + fz * 2;
        int baseY = foot.getY() + yOffset;
        for (int h = 0; h < 5; h++) {
            for (int w = -1; w <= 2; w++) {
                BlockPos pos = new BlockPos(planeX + rx * w, baseY + h, planeZ + rz * w);
                boolean isCorner = (h == 0 || h == 4) && (w == -1 || w == 2);
                boolean isInterior = h >= 1 && h <= 3 && w >= 0 && w <= 1;
                if (isCorner) continue;
                if (isInterior) {
                    interiorBlocks.add(pos);
                } else {
                    frameBlocks.add(pos);
                }
            }
        }
        for (BlockPos pos : frameBlocks) {
            if (pos.getY() == baseY) {
                ignitionPos = pos;
                break;
            }
        }
    }

    private Direction directionFromYaw(float yaw) {
        float n = ((yaw % 360.0F) + 360.0F) % 360.0F;
        if (n >= 315.0F || n < 45.0F) return Direction.SOUTH;
        if (n < 135.0F) return Direction.WEST;
        if (n < 225.0F) return Direction.NORTH;
        return Direction.EAST;
    }

    private int yOffsetFromPitch(float pitch) {
        float clamped = Math.max(-45.0F, Math.min(45.0F, pitch));
        int step = Math.round(-clamped / 9.0F);
        return Math.max(-5, Math.min(5, step));
    }

    private boolean isFrameComplete() {
        for (BlockPos pos : frameBlocks) {
            if (mc.world.getBlockState(pos).getBlock() != Blocks.OBSIDIAN) return false;
        }
        return true;
    }

    private boolean isPortalLit() {
        for (BlockPos pos : interiorBlocks) {
            if (mc.world.getBlockState(pos).getBlock() == Blocks.NETHER_PORTAL) return true;
        }
        return false;
    }

    private boolean isPortalAlreadyHere() {
        int obsidianCount = 0;
        for (BlockPos pos : frameBlocks)
            if (mc.world.getBlockState(pos).getBlock() == Blocks.OBSIDIAN) obsidianCount++;
        return obsidianCount >= frameBlocks.size();
    }

    private boolean isPlaceableTarget(BlockPos pos) {
        return mc.world.getBlockState(pos).getBlock() != Blocks.OBSIDIAN && mc.world.getBlockState(pos).isReplaceable();
    }

    private boolean isBlocked() {
        for (BlockPos pos : frameBlocks) {
            if (mc.world.getBlockState(pos).getBlock() != Blocks.OBSIDIAN && !mc.world.getBlockState(pos).isReplaceable()) return true;
        }
        for (BlockPos pos : interiorBlocks) {
            var block = mc.world.getBlockState(pos).getBlock();
            if (block != Blocks.NETHER_PORTAL && block != Blocks.FIRE && !mc.world.getBlockState(pos).isReplaceable()) return true;
        }
        return false;
    }

    private boolean isPlayerMoving() {
        return mc.options.forwardKey.isPressed()
            || mc.options.backKey.isPressed()
            || mc.options.leftKey.isPressed()
            || mc.options.rightKey.isPressed()
            || mc.options.jumpKey.isPressed()
            || mc.options.sneakKey.isPressed();
    }

    private int countItem(net.minecraft.item.Item item) {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) count += mc.player.getInventory().getStack(i).getCount();
        }
        return count;
    }

    private int findHotbarSlot(net.minecraft.item.Item item) {
        for (int i = 0; i < 9; i++)
            if (mc.player.getInventory().getStack(i).getItem() == item) return i;
        return -1;
    }

    private boolean hasItemAnywhere(net.minecraft.item.Item item) {
        return countItem(item) > 0;
    }

    private void placeBlock(BlockPos pos) {
        BlockHitResult bhr = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
        mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
        mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(
            Hand.OFF_HAND, bhr, mc.player.currentScreenHandler.getRevision() + 2));
        mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get()) return;
        if (frameBlocks.isEmpty() && interiorBlocks.isEmpty()) return;
        boolean blocked = isBlocked();
        SettingColor side = blocked ? BLOCKED_SIDE : sideColor.get();
        SettingColor line = blocked ? BLOCKED_LINE : lineColor.get();
        for (BlockPos pos : frameBlocks) event.renderer.box(pos, side, line, shapeMode.get(), 0);
        for (BlockPos pos : interiorBlocks) event.renderer.box(pos, side, line, shapeMode.get(), 0);
    }
}
