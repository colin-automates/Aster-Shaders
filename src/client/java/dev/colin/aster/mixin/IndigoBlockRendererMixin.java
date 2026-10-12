package dev.colin.aster.mixin;

import dev.colin.aster.FoliageMeshMetadata;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fabric redirects section compilation through Indigo, including vanilla models. */
@Mixin(targets="net.fabricmc.fabric.impl.client.indigo.renderer.render.AltModelBlockRendererImpl",remap=false)
public class IndigoBlockRendererMixin {
    @Shadow private BlockState blockState;

    @Inject(method="transform",at=@At(value="INVOKE",target="Lnet/fabricmc/fabric/api/client/renderer/v1/mesh/MutableQuadView;translate(FFF)Lnet/fabricmc/fabric/api/client/renderer/v1/mesh/MutableQuadView;"))
    private void aster$windMetadata(MutableQuadView quad,CallbackInfoReturnable<Boolean> ci) {
        // After native lighting/tint, before block offset replaces model height.
        FoliageMeshMetadata.tag(blockState,quad);
    }
}
