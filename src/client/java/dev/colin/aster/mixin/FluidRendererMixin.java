package dev.colin.aster.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.colin.aster.WaterMeshMetadata;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FluidRenderer.class)
public class FluidRendererMixin {
    // Chunk builders run concurrently; no render-thread world reads are needed.
    @Unique private static final ThreadLocal<WaterMeshMetadata.Shore> aster$shore = new ThreadLocal<>();

    @Inject(method="tesselate",at=@At("HEAD"))
    private void aster$begin(BlockAndTintGetter level,BlockPos pos,FluidRenderer.Output output,
                            BlockState state,FluidState fluid,CallbackInfo ci) {
        aster$shore.remove();
        if(Boolean.getBoolean("aster.baselineVerification") || !fluid.is(FluidTags.WATER)) return;
        boolean[] pinned=new boolean[4];
        // Inspect the same four cells for every copy of a shared corner,
        // including diagonal banks and corners across chunk boundaries.
        for(int z=0;z<2;z++) for(int x=0;x<2;x++) {
            for(int dz=-1;dz<=0;dz++) for(int dx=-1;dx<=0;dx++) {
                var neighbor=level.getBlockState(pos.offset(x+dx,0,z+dz));
                if(neighbor.isSolid()) pinned[x+z*2]=true;
            }
        }
        aster$shore.set(new WaterMeshMetadata.Shore(pos.getX()&15,pos.getZ()&15,pinned));
    }

    @Inject(method="tesselate",at=@At("RETURN"))
    private void aster$end(BlockAndTintGetter level,BlockPos pos,FluidRenderer.Output output,
                          BlockState state,FluidState fluid,CallbackInfo ci) { aster$shore.remove(); }

    @Inject(method="vertex",at=@At("HEAD"),cancellable=true)
    private void aster$metadata(VertexConsumer builder,float x,float y,float z,int color,float u,float v,
                               int light,CallbackInfo ci) {
        WaterMeshMetadata.Shore shore=aster$shore.get();
        if(shore == null) return;
        int corner=(x > shore.x()+.5f ? 1 : 0)+(z > shore.z()+.5f ? 2 : 0);
        boolean pinned=shore.pinned()[corner];
        // Native fluid color is opaque vertex tint. Reserve two alpha values
        // as water metadata; terrain.vsh restores opacity before native blending.
        int tagged=(color&0x00ffffff)|((pinned ? 128 : 254)<<24);
        builder.addVertex(x,y,z,tagged,u,v,OverlayTexture.NO_OVERLAY,light,0,1,0);
        WaterMeshMetadata.record(pinned);
        ci.cancel();
    }
}
