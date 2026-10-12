package dev.colin.aster;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.frontend.FrontendRenderPipeline;
import net.minecraft.client.Minecraft;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import java.util.*;

/** World-only HDR routing. Native pipeline caches own compiled variants. */
public final class WorldScene {
    private static TextureTarget world;
    private static RenderTarget nativeTarget;
    private static GpuTextureView outputColor;
    private static boolean active;
    private static final Map<String,RenderPipeline> sources = new HashMap<>();
    private static final Map<RenderPipeline,RenderPipeline> variants = new IdentityHashMap<>();
    private static final Set<String> used = new TreeSet<>();
    private WorldScene() {}
    public static boolean active() { return active; }
    public static RenderPipeline source(CompiledRenderPipeline compiled) {
        return compiled instanceof FrontendRenderPipeline p ? sources.get(p.name()) : null;
    }
    public static String sourceDescription(String name) { var p=sources.get(name); return p == null ? "unknown ("+sources.size()+" cached)" : p.getLocation().toString(); }
    public static TextureTarget target() { return world; }
    public static boolean redirects(RenderTarget target) { return active && target == nativeTarget; }
    public static GpuTextureView outputColor() { return outputColor; }
    public static void begin() {
        if (!AsterRenderer.INSTANCE.effectsEnabled()) return;
        nativeTarget = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        outputColor = nativeTarget.getColorTextureView();
        if (world == null || world.width != nativeTarget.width || world.height != nativeTarget.height) {
            if (world != null) world.destroyBuffers();
            world = new TextureTarget("Aster scene-linear world",nativeTarget.width,nativeTarget.height,GpuFormat.RGBA16_FLOAT,GpuFormat.D32_FLOAT);
        }
        active = true;
    }
    public static void end() { active = false; }
    public static void close() { active = false; if (world != null) { world.destroyBuffers(); world = null; } }
    public static Vector4fc linearClear(Vector4fc c) { return new Vector4f(linear(c.x()),linear(c.y()),linear(c.z()),c.w()); }
    private static float linear(float c) { return c <= 0.04045f ? c/12.92f : (float)Math.pow((c+0.055f)/1.055f,2.4); }
    public static void remember(RenderPipeline source, CompiledRenderPipeline compiled) {
        if (compiled instanceof FrontendRenderPipeline p && !source.getShaderDefines().flags().contains("ASTER_LINEAR_WORLD")) sources.put(p.name(),source);
    }
    public static CompiledRenderPipeline select(CompiledRenderPipeline compiled, GpuTextureView color) {
        if (!active || !(compiled instanceof FrontendRenderPipeline p)) return compiled;
        var source = sources.get(p.name());
        if (source == null || (!source.getLocation().getNamespace().equals("minecraft") && !source.getShaderDefines().flags().contains("ASTER_RAIN_SPLASH"))) {
            // Aster's terrain source retains the native pipeline location.
            return compiled;
        }
        // Frontend descriptors can hold unwrapped backend texture views.
        boolean worldOutput = color != null && color.texture().getFormat() == GpuFormat.RGBA16_FLOAT
            && p.colorTargetStates().size() == 1 && p.colorTargetStates().getFirst().format() == GpuFormat.RGBA8_UNORM;
        boolean accumulation = source.getShaderDefines().flags().contains("OIT_ACCUMULATE");
        boolean alphaOnly = source.getShaderDefines().flags().contains("OIT_ALPHA_ONLY");
        if (!worldOutput && !accumulation && !alphaOnly) return compiled;
        used.add(source.getLocation().toString());
        return RenderSystem.getCompiledPipeline(variants.computeIfAbsent(source,WorldScene::variant));
    }
    private static RenderPipeline variant(RenderPipeline source) {
        var colors = source.getColorTargetStates().toArray(ColorTargetState[]::new);
        for (int i=0;i<colors.length;i++) if (colors[i] != null && colors[i].format() == GpuFormat.RGBA8_UNORM)
            colors[i] = new ColorTargetState(colors[i].blendFunction(),GpuFormat.RGBA16_FLOAT,colors[i].writeMask());
        var snippet = new RenderPipeline.Snippet(source.getShaders(),Optional.of(source.getShaderDefines()),Optional.of(source.getBindGroupLayouts()),colors,colors.length,
            Optional.ofNullable(source.getDepthStencilState()),Optional.of(source.getPolygonMode()),Optional.of(source.isCull()),
            source.getVertexFormatBindings().toArray(com.mojang.renderpearl.api.vertex.VertexFormat[]::new),Optional.of(source.getPrimitiveTopology()),source.pushConstantSize());
        var builder = RenderPipeline.builder(snippet).withLocation(AsterPipelines.id("linear/"+source.getLocation().getPath()))
            .withShaderDefine("ASTER_LINEAR_WORLD");
        String path=source.getLocation().getPath();
        if(path.equals("pipeline/weather") || path.equals("pipeline/oit_depth_bounds_weather")
            || path.equals("pipeline/oit_transmittance_weather") || path.equals("pipeline/oit_accumulate_weather")) builder.withShaderDefine("ASTER_WEATHER");
        var fragment = source.getShaders().get(ShaderType.FRAGMENT);
        if(fragment != null && fragment.getNamespace().equals("minecraft") && !fragment.getPath().equals("core/oit_composite")) {
            builder.withBindGroupLayout(AsterPipelines.SCENE);
            if(!source.getShaderDefines().flags().contains("OIT_ALPHA_ONLY")) builder.withBindGroupLayout(AsterPipelines.FOG_FORWARD);
        }
        if (net.minecraft.resources.Identifier.withDefaultNamespace("core/entity").equals(fragment)
            && !source.getShaderDefines().flags().contains("EMISSIVE")) {
            builder.withVertexShader(AsterPipelines.id("core/entity_world")).withFragmentShader(AsterPipelines.id("core/entity_world"))
                .withBindGroupLayout(AsterPipelines.SHADOW_INPUT).withBindGroupLayout(AsterPipelines.LOCAL_LIGHTING).withBindGroupLayout(AsterPipelines.CLOUD_SHADOW_INPUT);
        } else if (net.minecraft.resources.Identifier.withDefaultNamespace("core/item").equals(fragment)) {
            builder.withVertexShader(AsterPipelines.id("core/item_world")).withFragmentShader(AsterPipelines.id("core/item_world"))
                .withBindGroupLayout(AsterPipelines.SHADOW_INPUT).withBindGroupLayout(AsterPipelines.LOCAL_LIGHTING).withBindGroupLayout(AsterPipelines.CLOUD_SHADOW_INPUT);
        } else if (net.minecraft.resources.Identifier.withDefaultNamespace("core/rendertype_entity_shadow").equals(fragment)) {
            builder.withFragmentShader(AsterPipelines.id("core/entity_contact"));
        }
        return builder.build();
    }
    public static Object evidence() {
        var result = new LinkedHashMap<String,Object>();
        result.put("worldFormat","RGBA16_FLOAT"); result.put("outputFormat","RGBA8_UNORM SDR");
        result.put("linearTransparency",true); result.put("pipelineVariantsUsed",List.copyOf(used));
        result.put("nativeShadingBoundary","Terrain and lit entities use scene-linear directional lighting; emissive effects retain native shading before color conversion.");
        return result;
    }
}
