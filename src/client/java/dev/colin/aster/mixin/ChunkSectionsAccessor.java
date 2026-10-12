package dev.colin.aster.mixin;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkSectionsToRender.class)
public interface ChunkSectionsAccessor {
    @Invoker("renderLayers")
    void aster$renderLayers(ChunkSectionLayer[] layers, GpuSampler sampler, RenderPass pass,
        GpuTextureView atlas, GpuTextureView lightmap, RenderPipeline direct, RenderPipeline indirect);
}
