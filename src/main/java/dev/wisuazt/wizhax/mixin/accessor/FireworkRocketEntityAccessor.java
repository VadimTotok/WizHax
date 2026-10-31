package dev.wisuazt.wizhax.mixin.accessor;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FireworkRocketEntity.class)
public interface FireworkRocketEntityAccessor {
    @Accessor("shooter")
    LivingEntity getAttachedToEntity();

    @Accessor("life")
    int getLife();

    @Accessor("lifeTime")
    int getLifetime();
}
