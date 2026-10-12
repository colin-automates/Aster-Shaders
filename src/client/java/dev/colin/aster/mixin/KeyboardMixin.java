package dev.colin.aster.mixin;
import dev.colin.aster.AsterRenderer;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardMixin {
    @Inject(method="keyPress", at=@At("HEAD"))
    private void aster$toggle(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (action == InputConstants.PRESS && event.key() == InputConstants.KEY_F8 && Minecraft.getInstance().gui.screen() == null)
            AsterRenderer.INSTANCE.toggle();
    }
}
