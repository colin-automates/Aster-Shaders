package dev.colin.aster.mixin;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;
import dev.colin.aster.AsterRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderSystem.class)
public class DefaultUniformsMixin {
    @Inject(method="bindDefaultUniforms", at=@At("RETURN"))
    private static void aster$uniforms(RenderPass pass, CallbackInfo ci) { AsterRenderer.INSTANCE.bindScene(pass); }
}
