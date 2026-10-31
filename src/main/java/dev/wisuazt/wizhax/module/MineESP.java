package dev.wisuazt.wizhax.module;

import dev.wisuazt.wizhax.WizHax;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.network.packet.s2c.play.BlockBreakingProgressS2CPacket;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import java.util.ArrayList;
import java.util.List;

public class MineESP extends Module {
    public MineESP() {
        super(WizHax.WIZHAX, "[WHax] MineESP", "Renders a box at blocks being mined.");
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Boolean> showOthers = sgGeneral.add(new BoolSetting.Builder()
        .name("show-others")
        .description("Render blocks mined by other players (via server packets).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showSelf = sgGeneral.add(new BoolSetting.Builder()
        .name("show-self")
        .description("Render blocks you're mining. Uses local progress, so it's instant.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("Range")
        .description("Only renders inside this range.")
        .defaultValue(10)
        .min(0)
        .sliderRange(0, 50)
        .build()
    );

    private final Setting<Double> maxTime = sgGeneral.add(new DoubleSetting.Builder()
        .name("Max Time")
        .description("Removes rendered box after this time (remote players).")
        .defaultValue(10)
        .min(0)
        .sliderRange(0, 50)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("Shape Mode")
        .description("Which parts of boxes should be rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    public final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("Line Color")
        .description("Color of the outline.")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .build()
    );

    public final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("Side Color")
        .description("Color of the sides.")
        .defaultValue(new SettingColor(255, 0, 0, 50))
        .build()
    );

    private final List<Render> renders = new ArrayList<>();
    private Render pendingRender = null;

    private BlockPos localBreakPos = null;
    private float localProgress = 0f;

    @Override
    public void onDeactivate() {
        renders.clear();
        pendingRender = null;
        localBreakPos = null;
        localProgress = 0f;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) {
            localBreakPos = null;
            localProgress = 0f;
            return;
        }
        if (!showSelf.get()) {
            localBreakPos = null;
            localProgress = 0f;
            return;
        }

        if (!mc.interactionManager.isBreakingBlock()
            || !(mc.crosshairTarget instanceof BlockHitResult hit)) {
            localBreakPos = null;
            localProgress = 0f;
            return;
        }

        BlockPos pos = hit.getBlockPos();
        if (localBreakPos == null || !localBreakPos.equals(pos)) {
            localBreakPos = pos;
            localProgress = 0f;
        }

        BlockState state = mc.world.getBlockState(pos);
        if (state.isAir()) {
            localBreakPos = null;
            localProgress = 0f;
            return;
        }

        localProgress += state.calcBlockBreakingDelta(mc.player, mc.world, pos);
        if (localProgress >= 1f) {
            localBreakPos = null;
            localProgress = 0f;
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (pendingRender != null && !contains(pendingRender)) {
            renders.add(pendingRender);
        }
        pendingRender = null;

        long now = System.currentTimeMillis();
        double maxMs = maxTime.get() * 1000d;
        renders.removeIf(r -> now - r.time > maxMs);

        for (Render r : renders) {
            double delta = Math.min((now - r.time) / maxMs, 1);
            event.renderer.box(
                getBox(r.pos, getProgress(Math.min(delta * 4, 1))),
                getColor(sideColor.get(), 1 - delta),
                getColor(lineColor.get(), 1 - delta),
                shapeMode.get(), 0);
        }

        if (localBreakPos != null) {
            float prog = Math.min(localProgress, 1f);
            event.renderer.box(
                getBox(localBreakPos, prog),
                sideColor.get(),
                lineColor.get(),
                shapeMode.get(), 0);
        }
    }

    @EventHandler
    private void onReceive(PacketEvent.Receive event) {
        if (mc.player == null) return;
        if (!showOthers.get()) return;
        if (!(event.packet instanceof BlockBreakingProgressS2CPacket packet)) return;

        if (packet.getEntityId() == mc.player.getId()) return;
        if (packet.getProgress() < 0) return;
        if (!packet.getPos().isWithinDistance(mc.player.getBlockPos(), range.get())) return;

        pendingRender = new Render(packet.getPos(), System.currentTimeMillis());
    }

    private boolean contains(Render candidate) {
        for (Render r : renders) {
            if (r.pos.equals(candidate.pos)) return true;
        }
        return false;
    }

    private Color getColor(Color color, double delta) {
        return new Color(color.r, color.g, color.b, (int) Math.floor(color.a * delta));
    }

    private double getProgress(double delta) {
        return 1 - Math.pow(1 - delta, 5);
    }

    private Box getBox(BlockPos pos, double progress) {
        return new Box(
            pos.getX() + 0.5 - progress / 2,
            pos.getY() + 0.5 - progress / 2,
            pos.getZ() + 0.5 - progress / 2,
            pos.getX() + 0.5 + progress / 2,
            pos.getY() + 0.5 + progress / 2,
            pos.getZ() + 0.5 + progress / 2);
    }

    private record Render(BlockPos pos, long time) {}
}
