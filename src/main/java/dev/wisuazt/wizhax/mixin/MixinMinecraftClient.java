package dev.wisuazt.wizhax.mixin;

import dev.wisuazt.wizhax.module.ScreenshotCleaner;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public class MixinMinecraftClient {
    @Inject(method = "getWindowTitle", at = @At("HEAD"), cancellable = true)
    private void setNewWindowTitle(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue("WizHax");
    }

    @Inject(method = "render(Z)V", at = @At("RETURN"))
    private void onRenderReturn(boolean tick, CallbackInfo ci) {
        ScreenshotCleaner.onFrameEnd();
    }
}
