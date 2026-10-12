package dev.colin.aster.mixin;
import dev.colin.aster.AutomatedVerification;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class VerificationMouseMixin {
    @Inject(method={"grabMouse", "releaseMouse"}, at=@At("HEAD"), cancellable=true)
    private void aster$noInput(CallbackInfo ci) { if (AutomatedVerification.HIDDEN) ci.cancel(); }
}
