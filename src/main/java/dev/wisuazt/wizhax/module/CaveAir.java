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
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Set;

public class CaveAir extends Module {
    private final Set<BlockPos> found = new HashSet<>();
    private int tickCounter = 0;

    public CaveAir() {
        super(WizHax.WIZHAX, "[WHax] CaveAir", "Highlights the cave air blocks around you.");
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range")
        .description("The scanning radius around the player (in blocks).")
        .defaultValue(24)
        .min(4)
        .sliderMax(64)
        .build()
    );

    private final Setting<Integer> updateInterval = sgGeneral.add(new IntSetting.Builder()
        .name("update-interval")
        .description("How often to re-scan blocks (in ticks). Higher value = fewer lags.")
        .defaultValue(10)
        .min(1)
        .sliderMax(40)
        .build()
    );

    private final Setting<Integer> maxBlocks = sgGeneral.add(new IntSetting.Builder()
        .name("max-blocks")
        .description("Maximum blocks for rendering at a time.")
        .defaultValue(4000)
        .min(100)
        .sliderMax(20000)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How to draw blocks")
        .defaultValue(ShapeMode.Sides)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("The color of the block fill.")
        .defaultValue(new SettingColor(0, 200, 255, 25))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("The color of the block outline.")
        .defaultValue(new SettingColor(0, 200, 255, 200))
        .build()
    );

    @Override
    public void onActivate() {
        found.clear();
        tickCounter = 0;
    }

    @Override
    public void onDeactivate() {
        found.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        tickCounter++;
        if (tickCounter < updateInterval.get()) return;
        tickCounter = 0;
        scan();
    }

    private void scan() {
        found.clear();
        assert mc.player != null;
        BlockPos playerPos = mc.player.getBlockPos();
        int r = range.get();
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    if (found.size() >= maxBlocks.get()) return;
                    mutable.set(playerPos.getX() + x, playerPos.getY() + y, playerPos.getZ() + z);
                    assert mc.world != null;
                    if (mc.world.getBlockState(mutable).isOf(Blocks.CAVE_AIR)) {
                        found.add(mutable.toImmutable());
                    }
                }
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (found.isEmpty()) return;
        for (BlockPos pos : found) {
            event.renderer.box(
                pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1,
                sideColor.get(), lineColor.get(), shapeMode.get(), 0
            );
        }
    }
}
