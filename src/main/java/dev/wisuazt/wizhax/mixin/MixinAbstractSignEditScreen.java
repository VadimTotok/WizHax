package dev.wisuazt.wizhax.mixin;

import dev.wisuazt.wizhax.module.AutoSign;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSignEditScreen.class)
public class MixinAbstractSignEditScreen extends Screen {
    @Final
    @Shadow private String[] messages;
    @Final
    @Shadow protected SignBlockEntity blockEntity;
    @Final
    @Shadow private boolean front;

    protected MixinAbstractSignEditScreen(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        AutoSign.onScreenInit((AbstractSignEditScreen) (Object) this, messages, blockEntity, front);
    }
}
