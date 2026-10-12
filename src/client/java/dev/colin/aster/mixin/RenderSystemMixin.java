package dev.colin.aster.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.device.GpuDevice;
import dev.colin.aster.Verification;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import dev.colin.aster.WorldScene;

@Mixin(RenderSystem.class)
public abstract class RenderSystemMixin {
    @Inject(method = "initRenderer", at = @At("RETURN"))
    private static void aster$verifyDevice(GpuDevice device, CallbackInfo ci) {
        Verification.recordDevice(device);
    }
    @Inject(method="getCompiledPipeline",at=@At("RETURN"))
    private static void aster$source(RenderPipeline pipeline, CallbackInfoReturnable<CompiledRenderPipeline> ci) { WorldScene.remember(pipeline,ci.getReturnValue()); }
}
