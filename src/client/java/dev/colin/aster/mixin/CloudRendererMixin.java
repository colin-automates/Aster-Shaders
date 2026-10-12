package dev.colin.aster.mixin;

import dev.colin.aster.AsterRenderer;
import net.minecraft.client.renderer.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The authored cloud layers replace native clouds in both transparency modes. */
@Mixin(CloudRenderer.class)
public class CloudRendererMixin {
    @Inject(method="render(Lnet/minecraft/client/CloudStatus;Lcom/mojang/renderpearl/api/commands/RenderPass;)V",at=@At("HEAD"),cancellable=true)
    private void aster$clouds(CallbackInfo ci) { if(AsterRenderer.INSTANCE.customSkyActive()) ci.cancel(); }
    @Inject(method="renderOit",at=@At("HEAD"),cancellable=true)
    private void aster$oitClouds(CallbackInfo ci) { if(AsterRenderer.INSTANCE.customSkyActive()) ci.cancel(); }
}
