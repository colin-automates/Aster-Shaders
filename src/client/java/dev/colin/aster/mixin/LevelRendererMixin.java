package dev.colin.aster.mixin;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.colin.aster.AsterRenderer;
import dev.colin.aster.AsterWorldPass;
import dev.colin.aster.WorldScene;
import dev.colin.aster.AutomatedVerification;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    @Shadow @Final private LevelRenderState levelRenderState;
    @Shadow @Final private net.minecraft.client.renderer.RenderBuffers renderBuffers;
    @Unique private boolean aster$forcedDirect,aster$savedIndirect;

    @Inject(method="render", at=@At("HEAD"))
    private void aster$atmosphere(GraphicsResourceAllocator allocator, boolean outline, CameraRenderState camera,
        GpuBufferSlice fog, Vector4f fogColor, boolean sky, boolean consistentDepth, CallbackInfo ci) {
        AsterRenderer.INSTANCE.setAtmosphereColor(fogColor);
        // Native direct/indirect preparation owns different dynamic buffers.
        // Exercise one method for the entire hidden frame, never mix them.
        aster$forcedDirect=AutomatedVerification.HIDDEN && (AsterRenderer.INSTANCE.verificationLightingFlags()&262144) != 0;
        if(aster$forcedDirect) {
            aster$savedIndirect=levelRenderState.shouldUseMultiDrawIndirectForTerrain;
            levelRenderState.shouldUseMultiDrawIndirectForTerrain=false;
        }
        WorldScene.begin();
    }

    @ModifyArg(method="lambda$render$0",at=@At(value="INVOKE",target="Lcom/mojang/renderpearl/api/commands/CommandEncoder;clearColorAndDepthTextures(Lcom/mojang/renderpearl/api/textures/GpuTexture;Lorg/joml/Vector4fc;Lcom/mojang/renderpearl/api/textures/GpuTexture;D)V"),index=1)
    private Vector4fc aster$linearClear(Vector4fc color) { return WorldScene.active() ? WorldScene.linearClear(color) : color; }

    @Inject(method="addMainPass", at=@At("HEAD"))
    private void aster$shadows(FrameGraphBuilder graph, FeatureRenderDispatcher.PreparedFrame features,
        GpuBufferSlice fog, ChunkSectionsToRender chunks, boolean consistentDepth, CallbackInfo ci) {
        AsterRenderer.INSTANCE.begin((LevelRenderer)(Object)this, levelRenderState, features,chunks,renderBuffers);
    }

    @Redirect(method="lambda$addMainPass$0", at=@At(value="INVOKE", target="Lcom/mojang/renderpearl/api/commands/CommandEncoder;createRenderPass(Ljava/util/function/Supplier;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Ljava/util/Optional;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Ljava/util/OptionalDouble;)Lcom/mojang/renderpearl/api/commands/RenderPass;"))
    private RenderPass aster$worldPass(CommandEncoder encoder, Supplier<String> label, GpuTextureView color,
        Optional<Vector4fc> clearColor, GpuTextureView depth, OptionalDouble clearDepth) {
        var pass = encoder.createRenderPass(label,color,clearColor,depth,clearDepth);
        return AsterRenderer.INSTANCE.effectsEnabled() ? new AsterWorldPass(pass,color,depth) : pass;
    }

    @Inject(method="executeSolid", at=@At("RETURN"))
    private void aster$opaque(ChunkSectionsToRender chunks, FeatureRenderDispatcher.PreparedFrame features, RenderPass pass, CallbackInfo ci) {
        if (pass instanceof AsterWorldPass worldPass) worldPass.captureOpaqueAndContinue();
    }

    @Inject(method="render", at=@At("RETURN"))
    private void aster$composite(CallbackInfo ci) {
        AsterRenderer.INSTANCE.composite();
        WorldScene.end();
        if(aster$forcedDirect) levelRenderState.shouldUseMultiDrawIndirectForTerrain=aster$savedIndirect;
        AutomatedVerification.afterRender();
    }

    @Inject(method="close", at=@At("HEAD"))
    private void aster$close(CallbackInfo ci) { AsterRenderer.INSTANCE.close(); WorldScene.close(); }
}
