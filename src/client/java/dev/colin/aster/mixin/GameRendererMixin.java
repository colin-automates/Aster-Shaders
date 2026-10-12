package dev.colin.aster.mixin;
import dev.colin.aster.FrameProfiler;
import dev.colin.aster.WorldScene;
import dev.colin.aster.WorldCameraTransforms;
import dev.colin.aster.AutomatedVerification;
import org.joml.Matrix4f;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method="renderLevel",at=@At("HEAD"))
    private void aster$verificationCamera(CallbackInfo ci) { AutomatedVerification.prepareWorldCamera(); }
    @ModifyArg(method="renderLevel",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"),index=0)
    private Matrix4f aster$captureWorldProjection(Matrix4f projection) {
        return WorldCameraTransforms.captureAndJitter(projection);
    }
    @Inject(method="mainRenderTarget",at=@At("HEAD"),cancellable=true)
    private void aster$worldTarget(CallbackInfoReturnable<RenderTarget> ci) { if (WorldScene.active()) ci.setReturnValue(WorldScene.target()); }
    @Inject(method="render",at=@At("HEAD"))
    private void aster$profileBegin(CallbackInfo ci) { FrameProfiler.begin(); }
    @Inject(method="render",at=@At("RETURN"))
    private void aster$profileEnd(CallbackInfo ci) { FrameProfiler.end(); }
    @Inject(method="close",at=@At("HEAD"))
    private void aster$profileClose(CallbackInfo ci) { FrameProfiler.close(); WorldCameraTransforms.clear(); }
}
