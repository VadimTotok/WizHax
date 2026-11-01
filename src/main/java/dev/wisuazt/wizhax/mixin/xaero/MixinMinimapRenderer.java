package dev.wisuazt.wizhax.mixin.xaero;

import dev.wisuazt.wizhax.module.ScreenshotCleaner;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.hud.minimap.module.MinimapRenderer;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.render.module.ModuleRenderContext;

@Mixin(value = MinimapRenderer.class, remap = false)
public class MixinMinimapRenderer {
    @Inject(method = "render(Lxaero/hud/minimap/module/MinimapSession;Lxaero/hud/render/module/ModuleRenderContext;Lnet/minecraft/client/gui/DrawContext;F)V", at = @At("HEAD"), cancellable = true)
    private void onRender(MinimapSession session, ModuleRenderContext c, DrawContext guiGraphics, float partialTicks, CallbackInfo ci) {
        if (ScreenshotCleaner.isHidden()) ci.cancel();
    }
}
