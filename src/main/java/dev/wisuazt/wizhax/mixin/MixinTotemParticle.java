package dev.wisuazt.wizhax.mixin;

import dev.wisuazt.wizhax.event.EventParticle;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.TotemParticle;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TotemParticle.class)
public abstract class MixinTotemParticle extends BillboardParticle {
    protected MixinTotemParticle(ClientWorld world, double x, double y, double z, Sprite sprite) {
        super(world, x, y, z, sprite);
    }

    protected MixinTotemParticle(ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, Sprite sprite) {
        super(world, x, y, z, velocityX, velocityY, velocityZ, sprite);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        MeteorClient.EVENT_BUS.post(new EventParticle(this, this.velocityX, this.velocityY, this.velocityZ));
    }
}
