package dev.colin.aster.mixin;
import com.mojang.renderpearl.frontend.FrontendRenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import dev.colin.aster.WorldScene;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import java.util.List;
import java.util.Optional;
@Mixin(FrontendRenderPass.class)
public class WorldPipelineMixin {
    @Shadow @Final private List<RenderPassDescriptor.Attachment<Optional<Vector4fc>>> colorAttachments;
    @ModifyVariable(method="setPipeline",at=@At("HEAD"),argsOnly=true)
    private CompiledRenderPipeline aster$linear(CompiledRenderPipeline pipeline) {
        var color = colorAttachments.isEmpty() || colorAttachments.getFirst() == null ? null : colorAttachments.getFirst().textureView();
        var selected = WorldScene.select(pipeline,color);
        if (color != null && selected instanceof com.mojang.renderpearl.frontend.FrontendRenderPipeline p && p.colorTargetStates().getFirst() != null && p.colorTargetStates().getFirst().format() != color.texture().getFormat())
            throw new IllegalStateException("Aster HDR routing mismatch: "+p.name()+", worldActive="+WorldScene.active()+", output="+color.texture().getFormat()+", pipeline="+p.colorTargetStates().getFirst().format()+", source="+WorldScene.sourceDescription(p.name()));
        return selected;
    }
}
