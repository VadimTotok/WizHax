package dev.wisuazt.wizhax.module;

import dev.wisuazt.wizhax.WizHax;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.*;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public class KillEffects extends Module {
    public KillEffects() {
        super(WizHax.WIZHAX, "[WHax] KillEffects", "Displays effects when entities die.");
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    public final Setting<EffectType> effectType = sgGeneral.add(new EnumSetting.Builder<EffectType>()
        .name("effect-type")
        .description("The type of kill effect to display.")
        .defaultValue(EffectType.ENTITY)
        .build()
    );

    public final Setting<EntityEffectType> entityEffect = sgGeneral.add(new EnumSetting.Builder<EntityEffectType>()
        .name("entity-effect")
        .description("The entity effect to spawn.")
        .defaultValue(EntityEffectType.LIGHTNING_BOLT)
        .visible(() -> effectType.get() == EffectType.ENTITY)
        .build()
    );

    public final Setting<Integer> entityAmount = sgGeneral.add(new IntSetting.Builder()
        .name("entity-amount")
        .description("Number of entities to spawn.")
        .defaultValue(1)
        .range(1, 5)
        .sliderRange(1, 5)
        .visible(() -> effectType.get() == EffectType.ENTITY)
        .build()
    );

    public final Setting<List<ParticleType<?>>> particleTypes = sgGeneral.add(new ParticleTypeListSetting.Builder()
        .name("particle-types")
        .description("Types of particles to spawn.")
        .defaultValue(ParticleTypes.EXPLOSION)
        .visible(() -> effectType.get() == EffectType.PARTICLE)
        .build()
    );

    public final Setting<Integer> particleAmount = sgGeneral.add(new IntSetting.Builder()
        .name("particle-amount")
        .description("Number of particles to spawn.")
        .defaultValue(50)
        .range(10, 100)
        .sliderRange(10, 100)
        .visible(() -> effectType.get() == EffectType.PARTICLE)
        .build()
    );

    public final Setting<List<SoundEvent>> soundEvents = sgGeneral.add(new SoundEventListSetting.Builder()
        .name("sound-events")
        .description("Types of sounds to play. Only the first sound in the list will be played.")
        .defaultValue(SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER)
        .visible(() -> effectType.get() == EffectType.PARTICLE)
        .build()
    );

    public final Setting<Integer> soundVolume = sgGeneral.add(new IntSetting.Builder()
        .name("sound-volume")
        .description("Volume of the sound.")
        .defaultValue(100)
        .range(0, 200)
        .sliderRange(0, 200)
        .visible(() -> effectType.get() == EffectType.PARTICLE && !soundEvents.get().isEmpty())
        .build()
    );

    private final SettingGroup sgEntityFilter = settings.createGroup("Entity Filter");

    public final Setting<Boolean> players = sgEntityFilter.add(new BoolSetting.Builder()
        .name("players")
        .description("Trigger effects on player deaths.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> hostileMobs = sgEntityFilter.add(new BoolSetting.Builder()
        .name("hostile-mobs")
        .description("Trigger effects on hostile mob deaths.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> passiveMobs = sgEntityFilter.add(new BoolSetting.Builder()
        .name("passive-mobs")
        .description("Trigger effects on passive mob deaths.")
        .defaultValue(true)
        .build()
    );

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!(event.packet instanceof EntityStatusS2CPacket packet)) return;
        if (packet.getStatus() != 3) return;
        if (mc.world == null) return;
        Entity entity = packet.getEntity(mc.world);
        if (entity == null || entity == mc.player) return;
        if (!isValidEntity(entity)) return;
        if (!isKilledByPlayer(entity)) return;
        mc.execute(() -> triggerKillEffect(entity));
    }

    private boolean isKilledByPlayer(Entity entity) {
        if (entity instanceof LivingEntity livingEntity) {
            LivingEntity lastAttacker = livingEntity.getLastAttacker();
            if (lastAttacker == mc.player) return true;
            if (livingEntity.getAttacker() == mc.player) return true;
        }
        assert mc.player != null;
        return mc.player.distanceTo(entity) <= 6.0;
    }

    private void triggerKillEffect(Entity entity) {
        Vec3d pos = entity.getEntityPos();
        switch (effectType.get()) {
            case ENTITY -> spawnEntityEffect(pos);
            case PARTICLE -> spawnParticleEffect(pos);
        }
    }

    private void spawnEntityEffect(Vec3d pos) {
        if (mc.world == null) return;
        if (Objects.requireNonNull(entityEffect.get()) == EntityEffectType.LIGHTNING_BOLT) {
            for (int i = 0; i < entityAmount.get(); i++)
                spawnRealLightning(pos);
        }
    }

    private void spawnRealLightning(Vec3d pos) {
        if (mc.world == null) return;
        LightningEntity lightning = new LightningEntity(EntityType.LIGHTNING_BOLT, mc.world);
        lightning.refreshPositionAfterTeleport(pos.x, pos.y, pos.z);
        lightning.setCosmetic(true);
        mc.world.addEntity(lightning);
    }

    private void spawnParticleEffect(Vec3d pos) {
        if (mc.world == null) return;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<ParticleType<?>> selectedParticles = particleTypes.get();
        if (!selectedParticles.isEmpty()) {
            for (int i = 0; i < particleAmount.get(); i++) {
                ParticleType<?> particleType = selectedParticles.get(i % selectedParticles.size());
                double offsetX = (random.nextDouble() - 0.5) * 4.0;
                double offsetY = random.nextDouble() * 2.0;
                double offsetZ = (random.nextDouble() - 0.5) * 4.0;
                if (particleType instanceof ParticleEffect particleEffect) {
                    mc.world.addParticleClient(
                        particleEffect,
                        pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ,
                        0.0, 0.05, 0.0
                    );
                } else {
                    spawnFallbackParticles(pos);
                    break;
                }
            }
        } else {
            spawnFallbackParticles(pos);
        }
        List<SoundEvent> selectedSounds = soundEvents.get();
        if (!selectedSounds.isEmpty()) {
            float volume = soundVolume.get() / 100.0f;
            SoundEvent sound = selectedSounds.getFirst();
            mc.world.playSound(
                mc.player,
                pos.x, pos.y, pos.z,
                sound,
                SoundCategory.PLAYERS,
                volume, 1.0f
            );
        }
    }

    private void spawnFallbackParticles(Vec3d pos) {
        if (mc.world == null) return;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int j = 0; j < 20; j++) {
            double offsetX = (random.nextDouble() - 0.5) * 4.0;
            double offsetY = random.nextDouble() * 8.0;
            double offsetZ = (random.nextDouble() - 0.5) * 4.0;
            mc.world.addParticleClient(
                ParticleTypes.EXPLOSION,
                pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ,
                0.0, 0.05, 0.0
            );
        }
    }

    private boolean isValidEntity(Entity entity) {
        if (entity == null) return false;
        try {
            return switch (entity) {
                case PlayerEntity player -> players.get();
                case MagmaCubeEntity magmaCube -> hostileMobs.get();
                case SlimeEntity slime -> hostileMobs.get();
                case GhastEntity ghast -> hostileMobs.get();
                case HostileEntity hostileEntity -> hostileMobs.get();
                case Monster monster -> hostileMobs.get();
                case PassiveEntity passiveEntity -> passiveMobs.get();
                default -> false;
            };
        } catch (Exception e) {
            return false;
        }
    }

    public enum EffectType {
        ENTITY("Entity"),
        PARTICLE("Particle");

        private final String title;
        EffectType(String title) { this.title = title; }
        @Override public String toString() { return title; }
    }

    public enum EntityEffectType {
        LIGHTNING_BOLT("Lightning Bolt");

        private final String title;
        EntityEffectType(String title) { this.title = title; }
        @Override public String toString() { return title; }
    }
}
