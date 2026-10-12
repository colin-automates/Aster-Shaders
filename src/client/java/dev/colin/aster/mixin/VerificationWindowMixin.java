package dev.colin.aster.mixin;
import com.mojang.blaze3d.platform.Window;
import dev.colin.aster.AutomatedVerification;
import org.lwjgl.sdl.SDLVideo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public class VerificationWindowMixin {
    @ModifyArg(method="createWindow", at=@At(value="INVOKE", target="Lcom/mojang/renderpearl/api/device/GpuBackend;createWindow(Ljava/lang/String;IIJ)J"), index=3)
    private long aster$hidden(long flags) {
        return AutomatedVerification.HIDDEN ? flags | SDLVideo.SDL_WINDOW_HIDDEN | SDLVideo.SDL_WINDOW_NOT_FOCUSABLE : flags;
    }
    @Inject(method={"selectCursor", "updateWindowMouseGrab"}, at=@At("HEAD"), cancellable=true)
    private void aster$noCursor(CallbackInfo ci) { if (AutomatedVerification.HIDDEN) ci.cancel(); }
}
