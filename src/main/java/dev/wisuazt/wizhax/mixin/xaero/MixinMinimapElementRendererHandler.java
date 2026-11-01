package dev.wisuazt.wizhax.mixin.xaero;

import dev.wisuazt.wizhax.module.ScreenshotCleaner;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.hud.minimap.element.render.MinimapElementRendererHandler;

@Mixin(value = MinimapElementRendererHandler.class, remap = false)
public abstract class MixinMinimapElementRendererHandler {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRender(Vec3d renderPos, float partialTicks, Framebuffer framebuffer, double backgroundCoordinateScale, RegistryKey<World> mapDimension, CallbackInfo ci) {
        if (ScreenshotCleaner.isHidden()) ci.cancel();
    }
}
