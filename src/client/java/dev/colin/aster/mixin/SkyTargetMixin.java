package dev.colin.aster.mixin;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SkyRenderer;
import dev.colin.aster.AsterRenderer;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
/** Resolve each frame: SkyRenderer can retain a target from an earlier mode/size. */
@Mixin(SkyRenderer.class)
public class SkyTargetMixin {
    @Inject(method="render",at=@At("HEAD"),cancellable=true)
    private void aster$proceduralSky(CallbackInfo ci) {
        if(AsterRenderer.INSTANCE.renderSky()) ci.cancel();
    }
    @Redirect(method="render",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/pipeline/RenderTarget;getColorTextureView()Lcom/mojang/renderpearl/api/textures/GpuTextureView;"))
    private GpuTextureView aster$color(RenderTarget ignored) { return Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTextureView(); }
    @Redirect(method="render",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/pipeline/RenderTarget;getDepthTextureView()Lcom/mojang/renderpearl/api/textures/GpuTextureView;"))
    private GpuTextureView aster$depth(RenderTarget ignored) { return Minecraft.getInstance().gameRenderer.mainRenderTarget().getDepthTextureView(); }
}
