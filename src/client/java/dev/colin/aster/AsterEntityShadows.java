package dev.colin.aster;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.*;
import net.minecraft.resources.Identifier;
import java.util.*;

/** Native posed entity meshes, with alpha-tested depth instead of lit color. */
public final class AsterEntityShadows {
    private static final Map<RenderPipeline,RenderPipeline> variants = new IdentityHashMap<>();
    private static final Set<String> used = new TreeSet<>();
    private static int draws;
    private AsterEntityShadows() {}
    public static void beginFrame() { draws=0; }
    public static void drew() { draws++; }
    public static int drawCount() { return draws; }
    public static CompiledRenderPipeline select(CompiledRenderPipeline compiled) {
        var source=WorldScene.source(compiled);
        if(source == null || (!Identifier.withDefaultNamespace("core/entity").equals(source.getShaders().get(ShaderType.FRAGMENT))
            && !Identifier.withDefaultNamespace("core/item").equals(source.getShaders().get(ShaderType.FRAGMENT)))
            || source.getShaderDefines().flags().contains("EMISSIVE")
            || source.getDepthStencilState() == null || !source.getDepthStencilState().writeDepth()) return null;
        used.add(source.getLocation().toString());
        return RenderSystem.getCompiledPipeline(variants.computeIfAbsent(source,AsterEntityShadows::variant));
    }
    private static RenderPipeline variant(RenderPipeline source) {
        // Retain native projection, transform and texture layouts; no shadow
        // samplers exist in this pipeline, so attachments cannot feed back.
        var colors=new ColorTargetState[]{new ColorTargetState(Optional.empty(),GpuFormat.RGBA8_UNORM,0)};
        var snippet=new RenderPipeline.Snippet(source.getShaders(),Optional.of(source.getShaderDefines()),Optional.of(source.getBindGroupLayouts()),colors,1,
            Optional.of(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL,true)),Optional.of(source.getPolygonMode()),Optional.of(false),
            source.getVertexFormatBindings().toArray(com.mojang.renderpearl.api.vertex.VertexFormat[]::new),Optional.of(source.getPrimitiveTopology()),source.pushConstantSize());
        var builder=RenderPipeline.builder(snippet).withLocation(AsterPipelines.id("entity_shadow/"+source.getLocation().getPath()))
            .withFragmentShader(AsterPipelines.id("core/entity_caster"));
        if(Identifier.withDefaultNamespace("core/item").equals(source.getShaders().get(ShaderType.FRAGMENT))) builder.withShaderDefine("ASTER_ITEM_CASTER");
        return builder.build();
    }
    public static Object evidence() {
        return Map.of("drawsAcrossThreeCascades",draws,"casterPipelinesUsed",List.copyOf(used),
            "projectionProtected",true,"alphaTested",true,"nativePosedMeshes",true,"offscreenCasterRadius",96,
            "contactOpacityScale",.20,"receivesSunAndMoonShadows",true);
    }
}
