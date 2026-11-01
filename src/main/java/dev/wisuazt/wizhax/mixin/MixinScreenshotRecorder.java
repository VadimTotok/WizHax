package dev.wisuazt.wizhax.mixin;

import dev.wisuazt.wizhax.module.ScreenshotCleaner;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.util.function.Consumer;

@Mixin(ScreenshotRecorder.class)
public class MixinScreenshotRecorder {
    @Inject(method = "saveScreenshot(Ljava/io/File;Ljava/lang/String;Lnet/minecraft/client/gl/Framebuffer;ILjava/util/function/Consumer;)V", at = @At("HEAD"), cancellable = true)
    private static void onSaveScreenshot(File gameDirectory, String fileName, Framebuffer framebuffer, int downscaleFactor, Consumer<Text> messageReceiver, CallbackInfo ci) {

        ScreenshotCleaner module = ScreenshotCleaner.get();
        if (module == null || !module.isActive()) return;
        if (!ScreenshotCleaner.isAutoOnly()) return;
        if (ScreenshotCleaner.isCapturing()) return;

        ScreenshotCleaner.queuePendingScreenshot(gameDirectory, fileName, framebuffer, downscaleFactor, messageReceiver);
        ci.cancel();
    }
}
