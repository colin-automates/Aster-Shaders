package dev.colin.aster.mixin;

import com.mojang.blaze3d.vertex.QuadInstance;
import dev.colin.aster.FoliageMeshMetadata;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelBlockRenderer.class)
public class ModelBlockRendererMixin {
    @Shadow @Final private QuadInstance quadInstance;

    // Runs after native AO and biome tinting, on the chunk builder's own instance.
    @Inject(method="putQuadWithTint",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/block/BlockQuadOutput;put(FFFLnet/minecraft/client/resources/model/geometry/BakedQuad;Lcom/mojang/blaze3d/vertex/QuadInstance;)V"))
    private void aster$windMetadata(BlockQuadOutput output,float x,float y,float z,BlockAndTintGetter level,
                                   BlockState state,BlockPos pos,BakedQuad quad,CallbackInfo ci) {
        FoliageMeshMetadata.tag(state,quad,quadInstance);
    }
}
