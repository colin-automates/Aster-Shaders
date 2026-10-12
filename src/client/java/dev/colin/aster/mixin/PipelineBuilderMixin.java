package dev.colin.aster.mixin;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import dev.colin.aster.AsterPipelines;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Map;

@Mixin(RenderPipeline.Builder.class)
public abstract class PipelineBuilderMixin {
    @Shadow @Final private Map<ShaderType, Identifier> shaders;

    @Inject(method="build", at=@At("HEAD"))
    private void aster$terrain(CallbackInfoReturnable<RenderPipeline> ci) {
        if (!Boolean.getBoolean("aster.baselineVerification") && Identifier.withDefaultNamespace("core/terrain").equals(shaders.get(ShaderType.VERTEX))) {
            ((RenderPipeline.Builder)(Object)this).withBindGroupLayout(AsterPipelines.SCENE)
                .withBindGroupLayout(AsterPipelines.FOG_FORWARD)
                .withBindGroupLayout(AsterPipelines.LOCAL_LIGHTING)
                .withBindGroupLayout(AsterPipelines.SKY_INPUT)
                .withBindGroupLayout(AsterPipelines.CLOUD_SHADOW_INPUT)
                .withBindGroupLayout(AsterPipelines.WATER_INPUTS)
                .withBindGroupLayout(AsterPipelines.MATERIAL_INPUT).withBindGroupLayout(AsterPipelines.SHADOW_INPUT)
                .withVertexShader(AsterPipelines.id("core/terrain"))
                .withFragmentShader(AsterPipelines.id("core/terrain"));
        }
    }
}
