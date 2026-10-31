package dev.wisuazt.wizhax.module;

import dev.wisuazt.wizhax.WizHax;
import dev.wisuazt.wizhax.event.EventParticle;
import dev.wisuazt.wizhax.mixin.IParticle;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.particle.*;
import net.minecraft.util.math.MathHelper;

import java.util.Random;

public class Particles extends Module {
    private final Random random = new Random();
    private final SettingGroup sgTotem = settings.createGroup("Totem");
    private final SettingGroup sgBlockDust = settings.createGroup("Block Dust");
    private final SettingGroup sgCrack = settings.createGroup("Crack");
    private final SettingGroup sgExplosions = settings.createGroup("Explosions");

    // Totem (see MixinTotemParticle.java)
    private final Setting<Boolean> totemEnabled = sgTotem.add(new BoolSetting.Builder().name("enabled").defaultValue(true).build());
    public final Setting<Integer> tAmount = sgTotem.add(new IntSetting.Builder().name("amount").defaultValue(16).min(1).max(100).build());
    private final Setting<Double> tVelocityX = sgTotem.add(new DoubleSetting.Builder().name("velocity-x").defaultValue(1.0).min(0.0).max(5.0).build());
    private final Setting<Double> tVelocityY = sgTotem.add(new DoubleSetting.Builder().name("velocity-y").defaultValue(1.0).min(0.0).max(5.0).build());
    private final Setting<Double> tVelocityZ = sgTotem.add(new DoubleSetting.Builder().name("velocity-z").defaultValue(1.0).min(0.0).max(5.0).build());
    private final Setting<Double> tGravity = sgTotem.add(new DoubleSetting.Builder().name("gravity").defaultValue(1.0).min(-2.0).max(2.0).build());
    private final Setting<Double> tLifetime = sgTotem.add(new DoubleSetting.Builder().name("lifetime").defaultValue(3.0).min(0.0).max(10.0).build());
    private final Setting<Double> tLifetimeDiff = sgTotem.add(new DoubleSetting.Builder().name("lifetime-diff").defaultValue(1.0).min(0.1).max(5.0).build());
    private final Setting<SettingColor> tColor = sgTotem.add(new ColorSetting.Builder().name("color").defaultValue(new SettingColor(255, 141, 133)).build());
    private final Setting<Double> tColorDiff = sgTotem.add(new DoubleSetting.Builder().name("color-diff").defaultValue(1.0).min(1.0).max(127.0).build());
    private final Setting<Double> tSize = sgTotem.add(new DoubleSetting.Builder().name("size").defaultValue(1.0).min(0.1).max(3.0).build());
    private final Setting<Double> tSizeDiff = sgTotem.add(new DoubleSetting.Builder().name("size-diff").defaultValue(0.5).min(0.0).max(1.5).build());

    // Block Dust (see MixinBlockDustParticle.java)
    private final Setting<Boolean> bdEnabled = sgBlockDust.add(new BoolSetting.Builder().name("enabled").defaultValue(true).build());
    public final Setting<Integer> bdAmount = sgBlockDust.add(new IntSetting.Builder().name("amount").defaultValue(16).min(1).max(100).build());
    private final Setting<Double> bdVelocityX = sgBlockDust.add(new DoubleSetting.Builder().name("velocity-x").defaultValue(1.0).min(0.0).max(5.0).build());
    private final Setting<Double> bdVelocityY = sgBlockDust.add(new DoubleSetting.Builder().name("velocity-y").defaultValue(1.0).min(0.0).max(5.0).build());
    private final Setting<Double> bdVelocityZ = sgBlockDust.add(new DoubleSetting.Builder().name("velocity-z").defaultValue(1.0).min(0.0).max(5.0).build());
    private final Setting<Double> bdGravity = sgBlockDust.add(new DoubleSetting.Builder().name("gravity").defaultValue(1.0).min(-2.0).max(2.0).build());
    private final Setting<Double> bdLifetime = sgBlockDust.add(new DoubleSetting.Builder().name("lifetime").defaultValue(3.0).min(0.0).max(10.0).build());
    private final Setting<Double> bdLifetimeDiff = sgBlockDust.add(new DoubleSetting.Builder().name("lifetime-diff").defaultValue(1.0).min(0.1).max(5.0).build());
    private final Setting<SettingColor> bdColor = sgBlockDust.add(new ColorSetting.Builder().name("color").defaultValue(new SettingColor(255, 141, 133)).build());
    private final Setting<Double> bdColorDiff = sgBlockDust.add(new DoubleSetting.Builder().name("color-diff").defaultValue(1.0).min(1.0).max(127.0).build());
    private final Setting<Double> bdSize = sgBlockDust.add(new DoubleSetting.Builder().name("size").defaultValue(1.0).min(0.1).max(3.0).build());
    private final Setting<Double> bdSizeDiff = sgBlockDust.add(new DoubleSetting.Builder().name("size-diff").defaultValue(0.5).min(0.0).max(1.5).build());

    // Crack (see MixinCrackParticle.java)
    private final Setting<Boolean> crackEnabled = sgCrack.add(new BoolSetting.Builder().name("enabled").defaultValue(true).build());
    public final Setting<Integer> cAmount = sgCrack.add(new IntSetting.Builder().name("amount").defaultValue(16).min(1).max(100).build());
    private final Setting<Double> cVelocityX = sgCrack.add(new DoubleSetting.Builder().name("velocity-x").defaultValue(1.0).min(0.0).max(5.0).build());
    private final Setting<Double> cVelocityY = sgCrack.add(new DoubleSetting.Builder().name("velocity-y").defaultValue(1.0).min(0.0).max(5.0).build());
    private final Setting<Double> cVelocityZ = sgCrack.add(new DoubleSetting.Builder().name("velocity-z").defaultValue(1.0).min(0.0).max(5.0).build());
    private final Setting<Double> cGravity = sgCrack.add(new DoubleSetting.Builder().name("gravity").defaultValue(1.0).min(-2.0).max(2.0).build());
    private final Setting<Double> cLifetime = sgCrack.add(new DoubleSetting.Builder().name("lifetime").defaultValue(3.0).min(0.0).max(10.0).build());
    private final Setting<Double> cLifetimeDiff = sgCrack.add(new DoubleSetting.Builder().name("lifetime-diff").defaultValue(1.0).min(0.1).max(5.0).build());
    private final Setting<SettingColor> cColor = sgCrack.add(new ColorSetting.Builder().name("color").defaultValue(new SettingColor(255, 141, 133)).build());
    private final Setting<Double> cColorDiff = sgCrack.add(new DoubleSetting.Builder().name("color-diff").defaultValue(1.0).min(1.0).max(127.0).build());
    private final Setting<Double> cSize = sgCrack.add(new DoubleSetting.Builder().name("size").defaultValue(1.0).min(0.1).max(3.0).build());
    private final Setting<Double> cSizeDiff = sgCrack.add(new DoubleSetting.Builder().name("size-diff").defaultValue(0.5).min(0.0).max(1.5).build());

    // Explosions (see MixinExplosionLargeParticle.java)
    private final Setting<Boolean> expEnabled = sgExplosions.add(new BoolSetting.Builder().name("enabled").defaultValue(true).build());
    public final Setting<Integer> eAmount = sgExplosions.add(new IntSetting.Builder().name("amount").defaultValue(6).min(1).max(50).build());
    private final Setting<Double> eVelocityX = sgExplosions.add(new DoubleSetting.Builder().name("velocity-x").defaultValue(1.0).min(0.0).max(5.0).build());
    private final Setting<Double> eVelocityY = sgExplosions.add(new DoubleSetting.Builder().name("velocity-y").defaultValue(1.0).min(0.0).max(5.0).build());
    private final Setting<Double> eVelocityZ = sgExplosions.add(new DoubleSetting.Builder().name("velocity-z").defaultValue(1.0).min(0.0).max(5.0).build());
    private final Setting<Double> eGravity = sgExplosions.add(new DoubleSetting.Builder().name("gravity").defaultValue(1.0).min(-2.0).max(2.0).build());
    private final Setting<Double> eLifetime = sgExplosions.add(new DoubleSetting.Builder().name("lifetime").defaultValue(3.0).min(0.0).max(10.0).build());
    private final Setting<Double> eLifetimeDiff = sgExplosions.add(new DoubleSetting.Builder().name("lifetime-diff").defaultValue(1.0).min(0.1).max(5.0).build());
    private final Setting<SettingColor> eColor = sgExplosions.add(new ColorSetting.Builder().name("color").defaultValue(new SettingColor(255, 141, 133)).build());
    private final Setting<Double> eColorDiff = sgExplosions.add(new DoubleSetting.Builder().name("color-diff").defaultValue(1.0).min(1.0).max(127.0).build());
    private final Setting<Double> eSize = sgExplosions.add(new DoubleSetting.Builder().name("size").defaultValue(1.0).min(0.1).max(3.0).build());
    private final Setting<Double> eSizeDiff = sgExplosions.add(new DoubleSetting.Builder().name("size-diff").defaultValue(0.5).min(0.0).max(1.5).build());

    public Particles() {
        super(WizHax.WIZHAX, "[WHax] Particles", "Customizable particles like totem pops.");
    }

    @EventHandler
    private void onParticleSpawn(EventParticle event) {
        if (!isActive()) return;
        Particle particle = event.particle();

        if (particle instanceof TotemParticle && totemEnabled.get()) {
            modify(event, tVelocityX.get(), tVelocityY.get(), tVelocityZ.get(), tGravity.get(), tLifetime.get(), tLifetimeDiff.get(), tColor.get(), tColorDiff.get(), tSize.get(), tSizeDiff.get());
        } else if (particle instanceof BlockDustParticle && bdEnabled.get()) {
            modify(event, bdVelocityX.get(), bdVelocityY.get(), bdVelocityZ.get(), bdGravity.get(), bdLifetime.get(), bdLifetimeDiff.get(), bdColor.get(), bdColorDiff.get(), bdSize.get(), bdSizeDiff.get());
        } else if (particle instanceof CrackParticle && crackEnabled.get()) {
            modify(event, cVelocityX.get(), cVelocityY.get(), cVelocityZ.get(), cGravity.get(), cLifetime.get(), cLifetimeDiff.get(), cColor.get(), cColorDiff.get(), cSize.get(), cSizeDiff.get());
        } else if (particle instanceof ExplosionLargeParticle && expEnabled.get()) {
            modify(event, eVelocityX.get(), eVelocityY.get(), eVelocityZ.get(), eGravity.get(), eLifetime.get(), eLifetimeDiff.get(), eColor.get(), eColorDiff.get(), eSize.get(), eSizeDiff.get());
        }
    }

    private void modify(EventParticle event, double velX, double velY, double velZ, double gravity, double lifetime, double lifetimeDiff, SettingColor color, double colorDiff, double size, double sizeDiff) {
        Particle particle = event.particle();
        BillboardParticle bp = (BillboardParticle) particle;
        particle.setVelocity(event.velocityX() * velX, event.velocityY() * velY, event.velocityZ() * velZ);
        ((IParticle) particle).setgravityStrength((float) gravity);
        particle.setMaxAge((int) ((lifetime + random.nextDouble(-lifetimeDiff, lifetimeDiff)) * 20));

        int rc = random.nextInt((int) -colorDiff, (int) colorDiff);
        bp.setColor(
            MathHelper.clamp(MathHelper.abs(color.r + rc) / 255f, 0f, 1f),
            MathHelper.clamp(MathHelper.abs(color.g + rc) / 255f, 0f, 1f),
            MathHelper.clamp(MathHelper.abs(color.b + rc) / 255f, 0f, 1f)
        );
        bp.scale((float) (size + random.nextDouble(-sizeDiff, sizeDiff)));
    }
}
