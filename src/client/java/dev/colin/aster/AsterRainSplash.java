package dev.colin.aster;

import com.mojang.renderpearl.api.pipeline.*;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import java.util.Optional;

/** Water splashes use a separate native particle layer; other sprite colours survive. */
public final class AsterRainSplash {
    private static SingleQuadParticle.Layer layer;
    private AsterRainSplash() {}
    public static SingleQuadParticle.Layer layer() {
        if(layer==null) {
            var nativeLayer=SingleQuadParticle.Layer.TRANSLUCENT;
            var oit=nativeLayer.oitPipelineSet();
            layer=new SingleQuadParticle.Layer(true,nativeLayer.textureAtlasLocation(),copy(nativeLayer.pipeline(),"classic"),
                new OitPipelineSet(copy(oit.depthBoundsPipeline(),"depth"),copy(oit.transmittancePipeline(),"transmittance"),copy(oit.accumulatePipeline(),"accumulate")));
        }
        return layer;
    }
    private static RenderPipeline copy(RenderPipeline source,String phase) {
        var colors=source.getColorTargetStates().toArray(ColorTargetState[]::new);
        var snippet=new RenderPipeline.Snippet(source.getShaders(),Optional.of(source.getShaderDefines()),Optional.of(source.getBindGroupLayouts()),colors,colors.length,
            Optional.ofNullable(source.getDepthStencilState()),Optional.of(source.getPolygonMode()),Optional.of(source.isCull()),
            source.getVertexFormatBindings().toArray(com.mojang.renderpearl.api.vertex.VertexFormat[]::new),Optional.of(source.getPrimitiveTopology()),source.pushConstantSize());
        return RenderPipeline.builder(snippet).withLocation(AsterPipelines.id("pipeline/rain_splash/"+phase)).withShaderDefine("ASTER_RAIN_SPLASH").build();
    }
}
