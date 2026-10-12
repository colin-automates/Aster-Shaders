package dev.colin.aster.mixin;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.colin.aster.AsterPipelines;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.ArrayList;
import java.util.List;

@Mixin(RenderPipelines.class)
public class RenderPipelinesMixin {
    @Inject(method="requiredPipelines", at=@At("RETURN"), cancellable=true)
    private static void aster$register(CallbackInfoReturnable<List<RenderPipeline>> ci) {
        var pipelines = new ArrayList<>(ci.getReturnValue());
        pipelines.addAll(AsterPipelines.all());
        ci.setReturnValue(pipelines);
    }
}
