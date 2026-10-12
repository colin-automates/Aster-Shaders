package dev.colin.aster.mixin;
import com.mojang.renderpearl.frontend.FrontendGpuDevice;
import dev.colin.aster.AutomatedVerification;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FrontendGpuDevice.class)
public class VerificationDeviceMixin {
    @Shadow @Final @Mutable public static boolean STRICT_VALIDATION;
    @Inject(method="<clinit>",at=@At("RETURN"))
    private static void aster$validate(CallbackInfo ci) {
        if (AutomatedVerification.HIDDEN) STRICT_VALIDATION = true;
    }
}
