package dev.colin.aster.mixin;

import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Allows the shadow-only dispatcher to own an independent staging buffer. */
@Mixin(FeatureRenderDispatcher.class)
public interface FeatureDispatcherAccessor {
    @Mutable @Accessor("stagedVertexBuffer")
    void aster$setStagedVertexBuffer(StagedVertexBuffer buffer);
}
