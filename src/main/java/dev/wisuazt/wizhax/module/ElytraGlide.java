package dev.wisuazt.wizhax.module;

import dev.wisuazt.wizhax.WizHax;
import dev.wisuazt.wizhax.util.Utils;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;

import java.util.Objects;

public class ElytraGlide extends Module {
    private enum Phase {
        CLIMBING, GLIDING
    }

    private Phase phase = Phase.CLIMBING;

    private long lastRocketUse = 0L;
    private long lastGlidingAttempt = 0L;
    private boolean launched = false;
    private double yTarget = -1.0;
    private boolean emergencyTriggered = false;

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgWebhook = this.settings.createGroup("Webhook");

    private final Setting<Integer> fireworkDelay = sgGeneral.add(new IntSetting.Builder()
        .name("timed-delay")
        .description("Delay between firework usages in milliseconds (CLIMBING phase).")
        .defaultValue(4000).sliderRange(0, 10000).build());

    private final Setting<Boolean> useManualY = sgGeneral.add(new BoolSetting.Builder()
        .name("use-manual-y-level")
        .description("Use a manual target Y. Off = use current Y at activation.")
        .defaultValue(true).build());

    private final Setting<Integer> manualYLevel = sgGeneral.add(new IntSetting.Builder()
        .name("manual-y-level")
        .description("Upper Y to climb to before gliding.")
        .defaultValue(256).sliderRange(-64, 320)
        .visible(useManualY::get)
        .onChanged(val -> yTarget = val).build());

    private final Setting<Integer> minY = sgGeneral.add(new IntSetting.Builder()
        .name("min-y")
        .description("Lower Y. When the glide reaches this altitude, the cycle restarts.")
        .defaultValue(100).sliderRange(-64, 320).build());

    private final Setting<Double> climbPitch = sgGeneral.add(new DoubleSetting.Builder()
        .name("climb-pitch")
        .description("Pitch during the climb. Negative looks up.")
        .defaultValue(-40.0).sliderRange(-90.0, 90.0).build());

    private final Setting<Double> glidePitch = sgGeneral.add(new DoubleSetting.Builder()
        .name("glide-pitch")
        .description("Pitch during the glide. Negative looks up, positive looks down.")
        .defaultValue(-2.0).sliderRange(-90.0, 90.0).build());

    private final Setting<Double> velocityThreshold = sgGeneral.add(new DoubleSetting.Builder()
        .name("velocity-threshold")
        .description("vy threshold for launching a firework while climbing.")
        .defaultValue(0.0).sliderRange(-0.5, 0.5).build());

    private final Setting<Integer> tolerance = sgGeneral.add(new IntSetting.Builder()
        .name("tolerance")
        .description("Altitude tolerance for phase transitions.")
        .defaultValue(2).sliderRange(0, 20).build());

    private final Setting<Boolean> useWebhook = sgWebhook.add(new BoolSetting.Builder()
        .name("webhook-enabled")
        .description("Send a Discord webhook when out of fireworks.")
        .defaultValue(false).build());

    private final Setting<String> webhookUrl = sgWebhook.add(new StringSetting.Builder()
        .name("webhook-url")
        .description("Discord webhook URL.")
        .defaultValue("")
        .visible(useWebhook::get).build());

    private final Setting<String> webhookPingId = sgWebhook.add(new StringSetting.Builder()
        .name("webhook-ping-id")
        .description("Optional Discord user ID to ping (empty = no ping).")
        .defaultValue("")
        .visible(useWebhook::get).build());

    private final Setting<Boolean> disconnectOnEmpty = sgWebhook.add(new BoolSetting.Builder()
        .name("disconnect-when-empty")
        .description("Disconnect from the server when fireworks run out.")
        .defaultValue(true).build());

    private final Setting<Utils.IllegalDisconnectMethod> disconnectMethod = sgWebhook.add(
        new EnumSetting.Builder<Utils.IllegalDisconnectMethod>()
            .name("disconnect-method")
            .description("Packet-spam method used for the illegal disconnect.")
            .defaultValue(Utils.IllegalDisconnectMethod.Slot)
            .visible(disconnectOnEmpty::get).build());

    public ElytraGlide() {
        super(WizHax.WIZHAX, "[WHax] ElytraGlide",
            "Cycles between climbing to target-y with fireworks and gliding down to min-y at a fixed pitch. Disconnects and pings Discord when out of fireworks.");
    }

    @Override
    public void onActivate() {
        if (minY.get() >= (useManualY.get() ? manualYLevel.get() : (int) Objects.requireNonNull(mc.player).getY())) {
            error("min-y must be below the target Y.");
            toggle();
            return;
        }

        phase = Phase.CLIMBING;
        launched = false;
        yTarget = -1.0;
        lastRocketUse = 0L;
        lastGlidingAttempt = 0L;
        emergencyTriggered = false;

        if (mc.player == null || !mc.player.isGliding()) {
            info("You must be flying before enabling ElytraGlide.");
        }
    }

    @EventHandler
    private void onTick(Pre event) {
        if (mc.player == null || mc.world == null || emergencyTriggered) return;
        if (!mc.player.isGliding()) {
            handleTakeoff();
            yTarget = -1.0;
            return;
        }
        launched = false;

        if (yTarget == -1.0) {
            yTarget = useManualY.get() ? manualYLevel.get() : mc.player.getY();
        }

        double currentY = mc.player.getY();

        if (phase == Phase.CLIMBING && currentY - yTarget >= -tolerance.get()) {
            phase = Phase.GLIDING;
            info("Target height reached: gliding at pitch " + glidePitch.get() + "°.");
        } else if (phase == Phase.GLIDING && currentY <= minY.get() + tolerance.get()) {
            phase = Phase.CLIMBING;
            info("Min height reached: climbing back up.");
        }

        if (phase == Phase.CLIMBING) tickClimbing();
        else tickGliding();
    }

    private void tickClimbing() {
        float target = climbPitch.get().floatValue();
        float cur = mc.player.getPitch();
        mc.player.setPitch(cur + (target - cur) * 0.1f);

        if (mc.player.getVelocity().y < velocityThreshold.get()
            && System.currentTimeMillis() - lastRocketUse > fireworkDelay.get()) {
            tryUseFirework();
        }
    }

    private void tickGliding() {
        float target = glidePitch.get().floatValue();
        float cur = mc.player.getPitch();
        mc.player.setPitch(cur + (target - cur) * 0.2f);
    }

    private void handleTakeoff() {
        if (!launched) {
            tryStartGliding();
            launched = true;
        } else if (System.currentTimeMillis() - lastRocketUse > 1000L) {
            tryUseFirework();
        }
    }

    private void tryStartGliding() {
        if (mc.player == null || mc.player.isGliding()) return;

        if (mc.player.isOnGround()) {
            mc.player.jump();
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastGlidingAttempt > 1000L) {
            lastGlidingAttempt = now;
            if (mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(
                    mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING
                ));
            }
        }
    }

    private void tryUseFirework() {
        int result = Utils.firework(mc, false);
        if (result == -1) {
            triggerEmergency("Out of fireworks");
            return;
        }
        lastRocketUse = System.currentTimeMillis();
    }

    private void triggerEmergency(String reason) {
        if (emergencyTriggered) return;
        emergencyTriggered = true;

        info("Emergency: " + reason + " - disconnecting.");

        if (useWebhook.get() && !webhookUrl.get().isEmpty()) {
            try {
                String player = mc.getSession().getUsername();
                String pingId = webhookPingId.get().isEmpty() ? null : webhookPingId.get();
                Utils.sendWebhook(
                    webhookUrl.get(),
                    "WizHax ElytraGlide out of fireworks",
                    reason + " (phase: " + phase.name() + ", Y: " + String.format("%.0f", mc.player.getY()) + ")",
                    pingId,
                    player
                );
            } catch (Exception e) {
                error("Webhook failed: " + e.getMessage());
            }
        }

        toggle();

        if (disconnectOnEmpty.get()) {
            Utils.illegalDisconnect(true, disconnectMethod.get());
        }
    }
}
