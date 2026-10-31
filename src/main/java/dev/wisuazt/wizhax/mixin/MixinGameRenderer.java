package dev.wisuazt.wizhax.mixin;

import dev.wisuazt.wizhax.module.NoHurtCam;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {
    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void tiltViewWhenHurt(MatrixStack matrices, float tickProgress, CallbackInfo ci) {
        Modules modules = Modules.get();
        if (modules != null) {
            NoHurtCam noHurtCam = modules.get(NoHurtCam.class);
            if (noHurtCam != null && noHurtCam.shouldDisableHurtCam()) ci.cancel();
        }
    }
}
