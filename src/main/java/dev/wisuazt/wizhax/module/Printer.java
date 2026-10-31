package dev.wisuazt.wizhax.module;

import dev.wisuazt.wizhax.WizHax;
import dev.wisuazt.wizhax.compat.LitematicaHelper;
import dev.wisuazt.wizhax.util.printer.PlacementHelper;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Printer extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender  = settings.createGroup("Render");

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range").defaultValue(5).min(1).max(10)
        .description("Search radius (in blocks) for placement targets.")
        .build());

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay").defaultValue(3).min(0).max(20)
        .description("Delay in ticks between placement cycles.")
        .build());

    private final Setting<Integer> blocksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("blocks-per-tick").defaultValue(1).min(1).max(10)
        .description("Maximum number of blocks to place per cycle.")
        .build());

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate").defaultValue(true)
        .description("Automatically rotate the camera to place directional blocks correctly.")
        .build());

    private final Setting<Boolean> onlyBelow = sgGeneral.add(new BoolSetting.Builder()
        .name("only-below").defaultValue(false)
        .description("Only place blocks below the player's feet level.")
        .build());

    private final Setting<List<Block>> ignoredBlocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("ignored-blocks").defaultValue()
        .description("Blocks that should never be placed.")
        .build());

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render").defaultValue(true)
        .description("Render the current list of placement targets.")
        .build());

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color").defaultValue(new SettingColor(0, 255, 0, 50))
        .description("Fill color of the rendered placement boxes.")
        .build());

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color").defaultValue(new SettingColor(0, 255, 0, 255))
        .description("Outline color of the rendered placement boxes.")
        .build());

    private int timer = 0;
    private final List<BlockPos> toPlace = new ArrayList<>();
    private final List<BlockPos> renderList = new ArrayList<>();

    public Printer() {
        super(WizHax.WIZHAX, "[WizHax] Printer",
            "Automatically prints blocks from the loaded Litematica schematic.");
    }

    @Override
    public void onActivate() {
        timer = 0;
        toPlace.clear();
        renderList.clear();

        if (!LitematicaHelper.isLoaded()) {
            error("Litematica is not installed. Printer disabled.");
            toggle();
            return;
        }
        if (LitematicaHelper.getSchematicWorld() == null) {
            error("No schematic is loaded in Litematica.");
            toggle();
        }
    }

    @Override
    public void onDeactivate() {
        toPlace.clear();
        renderList.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (!LitematicaHelper.isLoaded()) return;
        if (LitematicaHelper.getSchematicWorld() == null) return;

        if (timer < delay.get()) { timer++; return; }
        timer = 0;

        collectBlocks();

        renderList.clear();
        renderList.addAll(toPlace);

        if (toPlace.isEmpty()) return;

        toPlace.sort(Comparator.comparingDouble(pos ->
            mc.player.squaredDistanceTo(Vec3d.ofCenter(pos))));

        int placed = 0;
        for (BlockPos pos : toPlace) {
            if (placed >= blocksPerTick.get()) break;
            if (tryPlace(pos)) placed++;
        }
    }

    private void collectBlocks() {
        toPlace.clear();
        assert mc.player != null;
        BlockPos playerPos = mc.player.getBlockPos();
        int r = range.get();

        for (int x = -r; x <= r; x++)
            for (int y = -r; y <= r; y++)
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    if (isValidTarget(pos)) toPlace.add(pos);
                }
    }

    private boolean isValidTarget(BlockPos pos) {
        BlockState required = LitematicaHelper.getSchematicBlockState(pos);
        if (required == null || required.isAir()) return false;

        if (ignoredBlocks.get().contains(required.getBlock())) return false;

        assert mc.world != null;
        BlockState current = mc.world.getBlockState(pos);
        if (!current.isReplaceable()) return false;
        if (current.getBlock() == required.getBlock()) return false;

        if (!required.canPlaceAt(mc.world, pos)) return false;

        assert mc.player != null;
        Box playerBox = mc.player.getBoundingBox();
        Box blockBox = new Box(pos.getX(), pos.getY(), pos.getZ(),
            pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
        if (playerBox.intersects(blockBox)) return false;

        if (onlyBelow.get() && pos.getY() > mc.player.getBlockPos().getY() - 1) return false;

        Item item = required.getBlock().asItem();
        if (item == Items.AIR) return false;
        if (!InvUtils.find(item).found()) return false;

        return true;
    }

    private boolean tryPlace(BlockPos pos) {
        BlockState required = LitematicaHelper.getSchematicBlockState(pos);
        if (required == null) return false;

        Item item = required.getBlock().asItem();

        FindItemResult slot = InvUtils.findInHotbar(item);
        if (!slot.found()) {
            FindItemResult inv = InvUtils.find(item);
            if (!inv.found()) return false;
            assert mc.player != null;
            InvUtils.move().from(inv.slot()).to(mc.player.getInventory().getSelectedSlot());
            return false;
        }

        if (!BlockUtils.canPlace(pos, true)) return false;

        if (rotate.get() && PlacementHelper.isRotationDependent(required)) {
            float[] rots = PlacementHelper.getRotations(required);
            Rotations.rotate(rots[0], rots[1], 50, () -> {
                BlockUtils.place(pos, slot, false, 0);
            });
        } else {
            BlockUtils.place(pos, slot, true, 50);
        }
        return true;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || renderList.isEmpty()) return;
        for (BlockPos pos : renderList) {
            event.renderer.box(
                pos.getX(),     pos.getY(),     pos.getZ(),
                pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1,
                sideColor.get(), lineColor.get(), ShapeMode.Both, 0);
        }
    }
}
